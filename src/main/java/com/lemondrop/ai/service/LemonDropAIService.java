package com.lemondrop.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AICartDto;
import com.lemondrop.ai.dto.AICartItemDto;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIProductCardDto;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.dto.groq.*;
import com.lemondrop.ai.model.*;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.model.Product;
import com.lemondrop.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LemonDropAIService {

    private static final Logger log = LoggerFactory.getLogger(LemonDropAIService.class);

    private final GroqClient groqClient;
    private final GroqProperties groqProperties;
    private final LemonAiProperties lemonAiProperties;
    private final AIConversationService conversationService;
    private final AIToolRegistry toolRegistry;
    private final SecurityAuditService auditService;
    private final ObjectMapper objectMapper;
    private final ProductService productService;

    @Autowired
    public LemonDropAIService(GroqClient groqClient,
                              GroqProperties groqProperties,
                              LemonAiProperties lemonAiProperties,
                              AIConversationService conversationService,
                              AIToolRegistry toolRegistry,
                              SecurityAuditService auditService,
                              ObjectMapper objectMapper,
                              ProductService productService) {
        this.groqClient = groqClient;
        this.groqProperties = groqProperties;
        this.lemonAiProperties = lemonAiProperties;
        this.conversationService = conversationService;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.productService = productService;
    }

    public LemonDropAIService(GroqClient groqClient,
                              GroqProperties groqProperties,
                              LemonAiProperties lemonAiProperties,
                              AIConversationService conversationService,
                              AIToolRegistry toolRegistry,
                              SecurityAuditService auditService,
                              ObjectMapper objectMapper) {
        this(groqClient, groqProperties, lemonAiProperties, conversationService, toolRegistry, auditService, objectMapper, null);
    }

    public AIChatResponse processMessage(AIChatRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Sanitize input
        String cleanMessage = auditService.sanitizeInput(request.getMessage());
        String action = request.getAction();

        // 2. Retrieve or create Conversation
        AIConversation conversation = conversationService.getOrCreateConversation(
                request.getConversationId(),
                request.getClientToken(),
                request.getCustomerName(),
                request.getCustomerPhone()
        );

        // Auto-extract customer info from user message if provided
        extractAndPersistCustomerInfo(cleanMessage, conversation);

        // Check if direct confirmation action requested by frontend button
        if ("CONFIRM_ORDER".equalsIgnoreCase(action)) {
            AIToolResult confirmResult = toolRegistry.execute("confirmar_pedido", "{}", conversation);
            conversationService.save(conversation);
            return buildResponseFromToolResult(conversation, confirmResult, "¡De una! 🍋 He confirmado tu pedido exitosamente.", startTime);
        } else if ("CLEAR_CART".equalsIgnoreCase(action)) {
            toolRegistry.execute("vaciar_carrito", "{}", conversation);
            conversationService.save(conversation);
            return buildResponseFromToolResult(conversation, null, "¡Listo! Vacié tu carrito. ¿Qué se te antoja ahora? 🍋", startTime);
        }

        // Check if message is empty
        if (cleanMessage.isEmpty()) {
            return buildStandardResponse(conversation, "¿En qué puedo ayudarte hoy con tu granizado? 🍋", startTime, false, false, false);
        }

        // 3. Append User Message
        AIMessage userMsg = AIMessage.builder()
                .role("user")
                .content(cleanMessage)
                .timestamp(LocalDateTime.now())
                .build();
        conversation.addMessage(userMsg);

        // 4. If Groq is not configured, notify clearly
        if (!groqClient.isAvailable()) {
            log.warn("Groq API no está configurada o no tiene API key válida.");
            return buildStandardResponse(
                    conversation,
                    "Lemon AI no está disponible en este momento. Por favor verifica la configuración de GROQ_API_KEY en el servidor o realiza tu pedido desde el catálogo digital. 🍋",
                    startTime,
                    false,
                    false,
                    false
            );
        }

        // 5. Agent Loop with Function Calling
        boolean cartUpdated = false;
        boolean requiresConfirmation = false;
        boolean orderConfirmed = false;
        String finalAssistantMessage = "";
        String lastOrderCode = conversation.getConfirmedOrderCode();
        String whatsAppUrl = null;

        int iterations = 0;
        int maxIterations = lemonAiProperties.getMaxToolIterations();
        List<AIProductCardDto> collectedProducts = new ArrayList<>();

        while (iterations < maxIterations) {
            iterations++;

            GroqChatRequest groqRequest = buildGroqRequest(conversation);
            Optional<GroqChatResponse> optResponse = groqClient.sendChatCompletion(groqRequest);

            if (optResponse.isEmpty() || optResponse.get().getChoices() == null || optResponse.get().getChoices().isEmpty()) {
                log.error("Groq devolvió una respuesta vacía o con error en la iteración {}.", iterations);
                return buildStandardResponse(
                        conversation,
                        "Lemon AI está experimentando alta demanda o intermitencia en este momento. Por favor intenta de nuevo en unos segundos. 🍋",
                        startTime,
                        false,
                        false,
                        false
                );
            }

            GroqChatResponse.GroqChoice choice = optResponse.get().getChoices().get(0);
            GroqMessage choiceMsg = choice.getMessage();

            if (choiceMsg == null) {
                break;
            }

            List<GroqToolCall> toolCalls = choiceMsg.getToolCalls();

            // If Groq wants to call one or more tools
            if (toolCalls != null && !toolCalls.isEmpty()) {
                log.info("Agent Loop iteración {}: Groq solicitó {} tool call(s)", iterations, toolCalls.size());

                // Record Assistant's tool request in conversation
                AIMessage assistantCallMsg = AIMessage.builder()
                        .role("assistant")
                        .content(choiceMsg.getContent())
                        .toolCalls(toolCalls.stream().map(tc -> AIMessage.AIToolCall.builder()
                                .id(tc.getId())
                                .type(tc.getType())
                                .function(AIMessage.AIFunctionCall.builder()
                                        .name(tc.getFunction() != null ? tc.getFunction().getName() : "")
                                        .arguments(tc.getFunction() != null ? tc.getFunction().getArguments() : "{}")
                                        .build())
                                .build()).collect(Collectors.toList()))
                        .timestamp(LocalDateTime.now())
                        .build();
                conversation.addMessage(assistantCallMsg);

                // Execute each tool
                for (GroqToolCall toolCall : toolCalls) {
                    String toolName = toolCall.getFunction().getName();
                    String args = toolCall.getFunction().getArguments();
                    long tStart = System.currentTimeMillis();

                    AIToolResult result = toolRegistry.execute(toolName, args, conversation);
                    long tDuration = System.currentTimeMillis() - tStart;

                    auditService.logToolExecution(
                            conversation.getConversationId(),
                            conversation.getClientToken(),
                            toolName,
                            args,
                            result.getMessage(),
                            result.isSuccess() ? "SUCCESS" : "FAILED",
                            tDuration
                    );

                    if (result.isCartModified()) cartUpdated = true;
                    if (result.isRequiresConfirmation()) requiresConfirmation = true;
                    if (result.isOrderCreated()) {
                        orderConfirmed = true;
                        if (result.getData() instanceof Map<?, ?> map) {
                            lastOrderCode = (String) map.get("orderCode");
                            whatsAppUrl = (String) map.get("whatsAppUrl");
                        }
                    }

                    // Extract products for visual catalog cards in Flutter
                    if (result.isSuccess() && result.getData() != null) {
                        extractProductsFromResult(result.getData(), collectedProducts);
                    }

                    // Append Tool Result to conversation
                    String resultJson;
                    try {
                        resultJson = objectMapper.writeValueAsString(result.getData() != null ? result.getData() : result.getMessage());
                    } catch (Exception e) {
                        resultJson = "{\"status\": \"" + (result.isSuccess() ? "success" : "error") + "\", \"message\": \"" + result.getMessage() + "\"}";
                    }

                    AIMessage toolResultMsg = AIMessage.builder()
                            .role("tool")
                            .toolCallId(toolCall.getId())
                            .toolName(toolName)
                            .content(resultJson)
                            .timestamp(LocalDateTime.now())
                            .build();
                    conversation.addMessage(toolResultMsg);
                }

                // Continue loop to feed tool results back to Groq
                continue;
            }

            // Final Assistant text reached
            finalAssistantMessage = choiceMsg.getContent();
            AIMessage finalMsg = AIMessage.builder()
                    .role("assistant")
                    .content(finalAssistantMessage)
                    .timestamp(LocalDateTime.now())
                    .build();
            conversation.addMessage(finalMsg);
            break;
        }

        if (iterations >= maxIterations && (finalAssistantMessage == null || finalAssistantMessage.isEmpty())) {
            log.warn("Límite de iteraciones alcanzado ({}) para conversación {}", maxIterations, conversation.getConversationId());
            finalAssistantMessage = "¡Listo! 🍋 Ya tengo tus datos registrados. ¿Deseas que confirmemos tu pedido ahora?";
            AIMessage maxReachedMsg = AIMessage.builder()
                    .role("assistant")
                    .content(finalAssistantMessage)
                    .timestamp(LocalDateTime.now())
                    .build();
            conversation.addMessage(maxReachedMsg);
        }

        // Save conversation state
        conversationService.save(conversation);

        // Ensure visual product cards are attached if the conversation is an inquiry about products
        if (collectedProducts.isEmpty() && (isProductInquiry(cleanMessage) || isProductInquiry(finalAssistantMessage)) && productService != null) {
            try {
                List<Product> activeProds = productService.getAllActiveAndAvailable();
                for (Product p : activeProds) {
                    BigDecimal priceFrom = p.getSmallPrice();
                    if (priceFrom == null || priceFrom.compareTo(BigDecimal.ZERO) <= 0) {
                        priceFrom = p.getMediumPrice();
                    }
                    Map<String, BigDecimal> prices = new HashMap<>();
                    if (p.getSizePrices() != null) {
                        p.getSizePrices().forEach((sz, pr) -> prices.put(sz.name(), pr));
                    }
                    collectedProducts.add(AIProductCardDto.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .description(p.getDescription() != null ? p.getDescription() : "")
                            .image(p.getImage() != null ? p.getImage() : "")
                            .category(p.getCategory() != null ? p.getCategory() : "Granizados")
                            .badge(p.getBadge() != null ? p.getBadge() : "")
                            .priceFrom(priceFrom != null ? priceFrom : BigDecimal.ZERO)
                            .prices(prices)
                            .available(p.isAvailable())
                            .build());
                }
            } catch (Exception ex) {
                log.warn("No se pudieron cargar productos para respuesta visual: {}", ex.getMessage());
            }
        }

        // Build structured response
        AIChatResponse response = buildStandardResponse(conversation, finalAssistantMessage, startTime, cartUpdated, requiresConfirmation, orderConfirmed);
        if (lastOrderCode != null) response.setOrderCode(lastOrderCode);
        if (whatsAppUrl != null) response.setWhatsAppUrl(whatsAppUrl);
        if (!collectedProducts.isEmpty()) response.setProducts(collectedProducts);

        return response;
    }

    private GroqChatRequest buildGroqRequest(AIConversation conversation) {
        List<GroqMessage> messages = new ArrayList<>();

        // 1. System Prompt
        messages.add(GroqMessage.builder()
                .role("system")
                .content(buildSystemPrompt(conversation))
                .build());

        // 2. Conversation History (Mapped to Groq format, windowed to last 8 messages to conserve TPM)
        if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
            List<AIMessage> history = conversation.getMessages();
            int maxHistory = 8;
            if (history.size() > maxHistory) {
                history = history.subList(history.size() - maxHistory, history.size());
            }

            for (AIMessage msg : history) {
                GroqMessage.GroqMessageBuilder builder = GroqMessage.builder()
                        .role(msg.getRole())
                        .content(msg.getContent());

                if ("tool".equals(msg.getRole())) {
                    builder.toolCallId(msg.getToolCallId());
                    builder.name(msg.getToolName());
                }

                if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    builder.toolCalls(msg.getToolCalls().stream().map(tc -> GroqToolCall.builder()
                            .id(tc.getId())
                            .type(tc.getType())
                            .function(GroqToolCall.GroqFunctionCall.builder()
                                    .name(tc.getFunction() != null ? tc.getFunction().getName() : "")
                                    .arguments(tc.getFunction() != null ? tc.getFunction().getArguments() : "{}")
                                    .build())
                            .build()).collect(Collectors.toList()));
                }

                messages.add(builder.build());
            }
        }

        return GroqChatRequest.builder()
                .model(groqProperties.getApi().getModel())
                .messages(messages)
                .tools(toolRegistry.getGroqTools())
                .toolChoice("auto")
                .temperature(0.5)
                .maxTokens(800)
                .build();
    }

    private void extractAndPersistCustomerInfo(String text, AIConversation conv) {
        if (text == null || text.trim().isEmpty() || conv == null) return;
        String raw = text.trim();

        // 1. Extract phone number (7 to 15 digits, Colombian format 3xx xxx xxxx, +57, etc.)
        java.util.regex.Pattern phonePattern = java.util.regex.Pattern.compile("(?:\\+?57\\s*)?(3\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{4}|\\b\\d{7,15}\\b)");
        java.util.regex.Matcher phoneMatcher = phonePattern.matcher(raw);
        if (phoneMatcher.find()) {
            String rawFound = phoneMatcher.group(1);
            String digits = rawFound.replaceAll("[^0-9]", "");
            if (digits.length() >= 7 && digits.length() <= 15) {
                conv.setCustomerPhone(digits);
            }
        }

        // 2. Extract name
        // Pattern A: Text preceding phone number (e.g. "Juan y el número es 3005722844", "Carlos, 3101234567", "Soy Juan 300...")
        if (conv.getCustomerName() == null || conv.getCustomerName().isEmpty()) {
            java.util.regex.Matcher pMatcher = phonePattern.matcher(raw);
            if (pMatcher.find()) {
                int phoneStart = pMatcher.start();
                if (phoneStart > 0) {
                    String prefix = raw.substring(0, phoneStart).trim();
                    String[] words = prefix.split("[,\\s]+");
                    StringBuilder nameBuilder = new StringBuilder();
                    for (String w : words) {
                        String cleanWord = w.replaceAll("[^\\p{L}]", "");
                        if (cleanWord.isEmpty()) continue;
                        String lw = cleanWord.toLowerCase();
                        if (lw.equals("y") || lw.equals("e") || lw.equals("el") || lw.equals("la") ||
                            lw.equals("mi") || lw.equals("es") || lw.equals("numero") || lw.equals("número") ||
                            lw.equals("num") || lw.equals("celular") || lw.equals("cel") || lw.equals("tel") ||
                            lw.equals("telefono") || lw.equals("teléfono") || lw.equals("whatsapp") ||
                            lw.equals("wa") || lw.equals("soy") || lw.equals("me") || lw.equals("llamo") ||
                            lw.equals("nombre")) {
                            continue;
                        }
                        if (nameBuilder.length() > 0) nameBuilder.append(" ");
                        nameBuilder.append(cleanWord);
                    }
                    String candidate = nameBuilder.toString().trim();
                    if (candidate.matches("^[\\p{L} ]{2,30}$") && isValidName(candidate)) {
                        conv.setCustomerName(candidate);
                    }
                }
            }
        }

        // Pattern B: "Mi nombre es Juan", "Me llamo Juan", "Soy Juan"
        if (conv.getCustomerName() == null || conv.getCustomerName().isEmpty()) {
            java.util.regex.Pattern explicitNamePattern = java.util.regex.Pattern.compile("(?i)(?:mi nombre es|me llamo|soy)\\s+([\\p{L} ]{2,30})");
            java.util.regex.Matcher enMatcher = explicitNamePattern.matcher(raw);
            if (enMatcher.find()) {
                String foundName = enMatcher.group(1).trim();
                if (isValidName(foundName)) {
                    conv.setCustomerName(foundName);
                }
            }
        }

        // Pattern C: Single word or two words if state is COLLECTING_CUSTOMER and expecting NAME
        if ((conv.getCustomerName() == null || conv.getCustomerName().isEmpty()) &&
                (conv.getState() == ConversationState.COLLECTING_CUSTOMER || conv.getPendingCustomerFields().contains("NAME"))) {
            if (raw.matches("^[\\p{L} ]{2,30}$") && isValidName(raw)) {
                conv.setCustomerName(raw.trim());
            }
        }

        // 3. Extract Order Notes / Observations
        java.util.regex.Pattern notePattern = java.util.regex.Pattern.compile("(?i)(?:(?:pon(?:le)?|deja|agrega)?\\s*(?:en|de)?\\s*(?:la\\s+)?(?:nota|observación|observacion|indicación|indicacion)(?:\\s+es)?(?:\\s*:\\s*|\\s+que\\s+|\\s+)|sin\\s+(?:mucho\\s+|tanto\\s+)?hielo|bien\\s+fr[ií]o|poca\\s+az[uú]car|sin\\s+az[uú]car)(.*)");
        java.util.regex.Matcher noteMatcher = notePattern.matcher(raw);
        if (noteMatcher.find()) {
            String extractedNote = noteMatcher.group(0).trim();
            String cleanNote = extractedNote.replaceAll("(?i)^(?:(?:pon(?:le)?|deja|agrega)?\\s*(?:en|de)?\\s*(?:la\\s+)?(?:nota|observación|observacion)(?:\\s+es)?(?:\\s*:\\s*|\\s+que\\s+|\\s+))", "").trim();
            if (cleanNote.length() >= 3) {
                cleanNote = cleanNote.substring(0, 1).toUpperCase() + cleanNote.substring(1);
                conv.setObservations(cleanNote);
                if (conv.getCart() != null) {
                    conv.getCart().setObservations(cleanNote);
                }
            }
        }

        // 4. Update pendingCustomerFields
        List<String> pending = new ArrayList<>();
        if (conv.getCustomerName() == null || conv.getCustomerName().trim().isEmpty()) {
            pending.add("NAME");
        }
        if (conv.getCustomerPhone() == null || conv.getCustomerPhone().trim().isEmpty()) {
            pending.add("PHONE");
        }
        conv.setPendingCustomerFields(pending);
    }

    private boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String lower = name.toLowerCase().trim();
        if (lower.contains("granizado") || lower.contains("limon") || lower.contains("limón") ||
            lower.contains("maracu") || lower.contains("cereza") || lower.contains("mango") ||
            lower.contains("fresa") || lower.contains("pequeñ") || lower.contains("pequeno") ||
            lower.contains("small") || lower.contains("median") || lower.contains("medium") ||
            lower.contains("grande") || lower.contains("large") || lower.contains("arequipe") ||
            lower.contains("topping") || lower.contains("leche") || lower.contains("quiero") ||
            lower.contains("dame") || lower.contains("pedir") || lower.contains("menu") ||
            lower.contains("menú") || lower.contains("carta") || lower.contains("sabor") ||
            lower.contains("sabores") || lower.contains("hola") || lower.contains("buenas") ||
            lower.contains("buenos") || lower.contains("si") || lower.contains("sí") ||
            lower.contains("no") || lower.contains("ok") || lower.contains("dale") ||
            lower.contains("nota") || lower.contains("hielo") || lower.contains("hoy") ||
            lower.contains("hora") || lower.contains("dia") || lower.contains("día")) {
            return false;
        }
        return name.trim().length() >= 2 && name.trim().length() <= 30;
    }

    private String buildSystemPrompt(AIConversation conv) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Bogota"));
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, hh:mm a", new java.util.Locale("es", "CO"));
        String currentDateTimeFormatted = now.format(dtf);

        StringBuilder clientContext = new StringBuilder();
        if (conv.getCustomerName() != null && !conv.getCustomerName().isEmpty()) {
            clientContext.append("\n- Nombre del cliente: ").append(conv.getCustomerName());
        }
        if (conv.getCustomerPhone() != null && !conv.getCustomerPhone().isEmpty()) {
            clientContext.append("\n- Teléfono del cliente: ").append(conv.getCustomerPhone());
        }
        if (conv.getObservations() != null && !conv.getObservations().isEmpty()) {
            clientContext.append("\n- Observaciones/Notas del pedido: \"").append(conv.getObservations()).append("\"");
        }

        StringBuilder cartContext = new StringBuilder();
        if (conv.getCart() != null && conv.getCart().getItems() != null && !conv.getCart().getItems().isEmpty()) {
            cartContext.append("\nESTADO ACTUAL DEL CARRITO:");
            for (AICartItem item : conv.getCart().getItems()) {
                cartContext.append("\n• ")
                        .append(item.getQuantity()).append("x ")
                        .append(item.getProductName()).append(" (").append(item.getSize()).append(")");
                if (item.getAddons() != null && !item.getAddons().isEmpty()) {
                    cartContext.append(" con Toppings: ").append(item.getAddons().stream().map(AICartItemAddon::getAddonName).collect(Collectors.joining(", ")));
                }
                if (item.getObservations() != null && !item.getObservations().isEmpty()) {
                    cartContext.append(" [Nota ítem: ").append(item.getObservations()).append("]");
                }
                cartContext.append(" - $").append(item.getSubtotal());
            }
            cartContext.append("\nTotal acumulado: $").append(conv.getCart().getTotal());
            if (conv.getCart().getObservations() != null && !conv.getCart().getObservations().isEmpty()) {
                cartContext.append("\nNota general del pedido: \"").append(conv.getCart().getObservations()).append("\"");
            }
        }

        String pendingStr = (conv.getPendingCustomerFields() != null && !conv.getPendingCustomerFields().isEmpty())
                ? String.join(", ", conv.getPendingCustomerFields())
                : "Ninguno (datos completos)";

        return """
                Eres "Lemon Drop AI", el asistente inteligente oficial de Lemon Drop.
                Eres un asistente conversacional general y un agente de pedidos experto.
                
                FECHA Y HORA ACTUAL DEL NEGOCIO:
                - Hoy es %s (Zona Horaria: Colombia / America/Bogota).
                
                DATOS ACTUALES DEL CLIENTE:%s
                
                ESTADO CONVERSACIONAL DE NEGOCIO:
                - Estado: %s
                - Campos pendientes de cliente: [%s]
                %s
                
                DIRECTIVAS PRINCIPALES:
                1. CONVERSACIÓN LIBRE: Puedes responder preguntas generales, conversar, explicar, recomendar y ayudar con pedidos.
                2. NO REDIRIJAS AUTOMÁTICAMENTE toda conversación hacia un pedido. Solo habla del pedido cuando el usuario esté hablando del pedido o cuando sea útil y pertinente para la conversación.
                3. Si el usuario pregunta "¿eres una IA?", responde directamente y con naturalidad explicando quién eres y qué puedes hacer.
                4. Si el usuario pregunta "¿cómo estás?", responde directamente con calidez y estilo agradable ("¡De una! 🍋").
                5. Si el usuario pregunta "¿cuáles productos tienen?" o "¿cuáles hay?", consulta el catálogo con `buscar_productos` o `obtener_catalogo`.
                6. Si el usuario pregunta la fecha o la hora (ej. "¿qué día es hoy?"), responde usando la fecha/hora actual del sistema o la herramienta `obtener_fecha_hora_actual`.
                7. Si el usuario quiere pedir algo, utiliza las tools correspondientes (`agregar_producto`, `modificar_producto_carrito`, `actualizar_nota_pedido`, etc.).
                8. MANTÉN EL CONTEXTO: Conserva siempre los datos del cliente, notas y los productos agregados al carrito, pero NO fuerces al usuario a hablar del pedido si está haciendo preguntas generales.
                9. AUTORIDAD DEL BACKEND: NUNCA inventes productos, sabores, tamaños, precios ni códigos de pedido. Toda operación comercial real debe ejecutarse mediante las tools.
                10. CONFIRMACIÓN: Cuando el cliente tenga su pedido armado y dé su confirmación ("Sí", "Confirmo", "Dale", "Pídelo"), ejecuta `confirmar_pedido`. El estado inicial es "Pedido recibido" (RECEIVED) en preparación en cocina.
                11. REGLA VISUAL DE PRODUCTOS: Cuando consultes productos del catálogo, da una introducción breve y entusiasta. Las tarjetas interactivas se muestran automáticamente en la interfaz.
                """.formatted(currentDateTimeFormatted, clientContext.toString(),
                conv.getState() != null ? conv.getState().name() : "IDLE",
                pendingStr,
                cartContext.toString());
    }

    private AIChatResponse buildStandardResponse(AIConversation conv, String message, long startTime,
                                                 boolean cartUpdated, boolean requiresConfirmation, boolean orderConfirmed) {
        AICartDto cartDto = formatCartDto(conv.getCart());

        List<String> suggestions = List.of(
                "🍓 Algo dulce",
                "🥭 Recomiéndame algo",
                "🔥 Lo más vendido",
                "🛒 Ver mi pedido"
        );

        return AIChatResponse.builder()
                .conversationId(conv.getConversationId())
                .clientToken(conv.getClientToken())
                .message(message)
                .state(conv.getState() != null ? conv.getState().name() : ConversationState.IDLE.name())
                .intent(orderConfirmed ? "ORDER_CONFIRMED" : (requiresConfirmation ? "WAITING_CONFIRMATION" : (conv.getState() != null ? conv.getState().name() : "DISCOVERING")))
                .customerName(conv.getCustomerName())
                .customerPhone(conv.getCustomerPhone())
                .observations(conv.getObservations())
                .pendingCustomerFields(conv.getPendingCustomerFields() != null ? new ArrayList<>(conv.getPendingCustomerFields()) : new ArrayList<>())
                .cartUpdated(cartUpdated)
                .orderReadyForConfirmation(requiresConfirmation || conv.getState() == ConversationState.WAITING_CONFIRMATION)
                .requiresConfirmation(requiresConfirmation || conv.getState() == ConversationState.WAITING_CONFIRMATION)
                .orderConfirmed(orderConfirmed)
                .cart(cartDto)
                .orderCode(conv.getConfirmedOrderCode())
                .suggestions(suggestions)
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .success(true)
                .build();
    }

    private AIChatResponse buildResponseFromToolResult(AIConversation conv, AIToolResult result, String defaultMsg, long startTime) {
        String msg = result != null && result.getMessage() != null ? result.getMessage() : defaultMsg;
        String orderCode = null;
        String whatsAppUrl = null;
        boolean orderCreated = result != null && result.isOrderCreated();

        if (result != null && result.getData() instanceof Map<?, ?> map) {
            orderCode = (String) map.get("orderCode");
            whatsAppUrl = (String) map.get("whatsAppUrl");
        }

        AIChatResponse response = buildStandardResponse(conv, msg, startTime, true, false, orderCreated);
        if (orderCode != null) response.setOrderCode(orderCode);
        if (whatsAppUrl != null) response.setWhatsAppUrl(whatsAppUrl);
        return response;
    }

    private AICartDto formatCartDto(AICart cart) {
        if (cart == null) {
            return AICartDto.builder().items(new ArrayList<>()).totalItems(0).build();
        }

        List<AICartItemDto> items = cart.getItems().stream().map(i -> AICartItemDto.builder()
                .id(i.getId())
                .productId(i.getProductId())
                .productName(i.getProductName())
                .flavorId(i.getFlavorId())
                .flavorName(i.getFlavorName())
                .size(i.getSize() != null ? i.getSize().name() : "MEDIUM")
                .quantity(i.getQuantity())
                .addonNames(i.getAddons() != null ? i.getAddons().stream().map(AICartItemAddon::getAddonName).collect(Collectors.toList()) : new ArrayList<>())
                .unitPrice(i.getUnitPrice())
                .addonTotal(i.getAddonTotal())
                .subtotal(i.getSubtotal())
                .observations(i.getObservations())
                .build()).collect(Collectors.toList());

        return AICartDto.builder()
                .cartId(cart.getCartId())
                .items(items)
                .subtotal(cart.getSubtotal())
                .total(cart.getTotal())
                .status(cart.getStatus() != null ? cart.getStatus().name() : "DRAFT")
                .observations(cart.getObservations())
                .totalItems(items.size())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void extractProductsFromResult(Object data, List<AIProductCardDto> target) {
        if (data == null) return;

        List<Map<String, Object>> productMaps = new ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && (map.containsKey("name") && (map.containsKey("prices") || map.containsKey("priceFrom")))) {
                    productMaps.add((Map<String, Object>) map);
                }
            }
        } else if (data instanceof Map<?, ?> map) {
            if (map.containsKey("products") && map.get("products") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> pMap) {
                        productMaps.add((Map<String, Object>) pMap);
                    }
                }
            }
        }

        for (Map<String, Object> p : productMaps) {
            String name = (String) p.getOrDefault("name", "");
            if (name == null || name.trim().isEmpty()) continue;

            // Avoid duplicates
            if (target.stream().anyMatch(existing -> existing.getName().equalsIgnoreCase(name))) {
                continue;
            }

            BigDecimal priceFrom = BigDecimal.ZERO;
            if (p.get("priceFrom") instanceof BigDecimal bd) {
                priceFrom = bd;
            } else if (p.get("priceFrom") instanceof Number num) {
                priceFrom = BigDecimal.valueOf(num.doubleValue());
            }

            Map<String, BigDecimal> prices = new HashMap<>();
            if (p.get("prices") instanceof Map<?, ?> pMap) {
                pMap.forEach((k, v) -> {
                    if (v instanceof BigDecimal bd) prices.put(k.toString(), bd);
                    else if (v instanceof Number n) prices.put(k.toString(), BigDecimal.valueOf(n.doubleValue()));
                });
            }

            target.add(AIProductCardDto.builder()
                    .id((String) p.getOrDefault("id", ""))
                    .name(name)
                    .description((String) p.getOrDefault("description", ""))
                    .image((String) p.getOrDefault("image", ""))
                    .badge((String) p.getOrDefault("badge", ""))
                    .priceFrom(priceFrom)
                    .prices(prices)
                    .available((Boolean) p.getOrDefault("available", true))
                    .build());
        }
    }

    private boolean isProductInquiry(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("producto") || lower.contains("granizado") || lower.contains("sabor") ||
                lower.contains("sabores") || lower.contains("carta") || lower.contains("menu") ||
                lower.contains("menú") || lower.contains("recomiend") || lower.contains("tienes") ||
                lower.contains("opcion") || lower.contains("opción") || lower.contains("vendido") ||
                lower.contains("catalogo") || lower.contains("catálogo") || lower.contains("vendes") ||
                lower.contains("ofreces") || lower.contains("dulce") || lower.contains("acido") ||
                lower.contains("ácido") || lower.contains("mostrar") || lower.contains("muestrame") ||
                lower.contains("muéstrame") || lower.contains("puedo pedir") || lower.contains("que hay");
    }

    private void attachAllActiveProducts(List<AIProductCardDto> target) {
        if (productService == null) return;
        try {
            List<Product> prods = productService.getAllActiveAndAvailable();
            for (Product p : prods) {
                BigDecimal priceFrom = p.getSmallPrice();
                if (priceFrom == null || priceFrom.compareTo(BigDecimal.ZERO) <= 0) priceFrom = p.getMediumPrice();
                Map<String, BigDecimal> prices = new HashMap<>();
                if (p.getSizePrices() != null) p.getSizePrices().forEach((sz, pr) -> prices.put(sz.name(), pr));

                target.add(AIProductCardDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription() != null ? p.getDescription() : "")
                        .image(p.getImage() != null ? p.getImage() : "")
                        .category(p.getCategory() != null ? p.getCategory() : "Granizados")
                        .badge(p.getBadge() != null ? p.getBadge() : "")
                        .priceFrom(priceFrom != null ? priceFrom : BigDecimal.ZERO)
                        .prices(prices)
                        .available(p.isAvailable())
                        .build());
            }
        } catch (Exception ignored) {}
    }
}
