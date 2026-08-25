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

        // 4. If Groq is not configured, run deterministic conversation and order engine
        if (!groqClient.isAvailable()) {
            return handleDeterministicFlow(conversation, cleanMessage, startTime);
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
                log.warn("Groq devolvió una respuesta vacía o con error en la iteración {}. Ejecutando motor conversacional y de pedidos determinístico.", iterations);
                return handleDeterministicFlow(conversation, cleanMessage, startTime);
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
                Eres un asistente conversacional avanzado, empático, dinámico y al mismo tiempo un agente de pedidos experto.
                
                FECHA Y HORA ACTUAL DEL NEGOCIO:
                - Hoy es %s (Zona Horaria: Colombia / America/Bogota).
                
                DATOS ACTUALES DEL CLIENTE:%s
                
                ESTADO CONVERSACIONAL DE NEGOCIO:
                - Estado: %s
                - Campos pendientes de cliente: [%s]
                %s
                
                DIRECTIVAS FUNDAMENTALES:
                1. CONVERSACIÓN LIBRE Y NATURAL: Eres un modelo de IA inteligente. Puedes responder libremente y con calidez cualquier pregunta razonable que el usuario formule (saludos, cultura general, curiosidades, el clima, recomendaciones, chistes, etc.). No te limites a frases prefabricadas ni te comportes como un menú rígido.
                2. SI EL USUARIO HACE PREGUNTAS GENERALES O CAMBIA DE TEMA TEMPORALMENTE (ej. "¿Qué día es hoy?", "¿Qué hora es?", "¿Cómo estás?", "¿Cuál es la capital de Colombia?"):
                   - Responde con naturalidad, precisión y estilo agradable ("¡De una! 🍋").
                   - Si preguntan por fecha o hora, puedes usar `obtener_fecha_hora_actual` o responder con la fecha del sistema indicada arriba.
                   - NUNCA borres el pedido, el carrito ni los datos del cliente por responder una pregunta general.
                3. GESTIÓN DE PEDIDOS Y HERRAMIENTAS:
                   - Para consultar productos o la carta: `buscar_productos`, `obtener_catalogo`, `consultar_producto`.
                   - Para armar el pedido: `agregar_producto`, `modificar_producto_carrito`, `eliminar_producto_carrito`, `vaciar_carrito`.
                   - Para notas y observaciones de preparación (ej. "sin mucho hielo", "bien frío", "recoger a las 5"): Utiliza `actualizar_nota_pedido`.
                   - Para confirmar el pedido: Cuando el cliente tenga su pedido, nombre y teléfono listos y dé su confirmación ("Sí", "Confirmo", "Dale", "Pídelo"), ejecuta `confirmar_pedido`.
                4. AUTORIDAD DEL BACKEND: NUNCA inventes productos, sabores, tamaños, precios ni códigos de pedido. Toda operación comercial real debe ejecutarse mediante las tools.
                5. DATOS DE CLIENTE: Para formalizar el pedido se requieren nombre y teléfono de WhatsApp. Pídelos amablemente cuando el cliente esté armando su pedido o listo para confirmar.
                6. ESTADO INICIAL DEL PEDIDO: Al confirmarse un pedido, el estado inicial es "Pedido recibido" (RECEIVED) en proceso de preparación en cocina. Se le notificará al cliente por WhatsApp cuando esté listo para recoger.
                7. REGLA VISUAL DE PRODUCTOS: Cuando el cliente pida ver el menú, sabores o recomendaciones, responde con un saludo breve y entusiasta. La aplicación móvil adjunta las tarjetas visuales interactivas correspondientes.
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
                    .category((String) p.getOrDefault("category", "Granizados"))
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

    private AIChatResponse handleDeterministicFlow(AIConversation conversation, String cleanMessage, long startTime) {
        String lower = cleanMessage != null ? cleanMessage.toLowerCase().trim() : "";
        extractAndPersistCustomerInfo(cleanMessage, conversation);

        List<AIProductCardDto> collectedProducts = new ArrayList<>();
        boolean cartUpdated = false;
        boolean requiresConfirmation = false;
        boolean orderConfirmed = false;
        String finalAssistantMessage;
        String orderCode = conversation.getConfirmedOrderCode();
        String whatsAppUrl = null;

        boolean hasCartItems = conversation.getCart() != null && !conversation.getCart().getItems().isEmpty();
        List<String> pending = conversation.getPendingCustomerFields();

        // 1. Date / Time inquiries (e.g. "¿Qué día es hoy?", "¿Qué hora es?", "fecha")
        if (lower.contains("dia es hoy") || lower.contains("día es hoy") || lower.contains("que hora") || lower.contains("qué hora") || lower.contains("fecha")) {
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Bogota"));
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, hh:mm a", new java.util.Locale("es", "CO"));
            finalAssistantMessage = "Hoy es " + now.format(dtf) + " (hora de Colombia). 📅⏰";
            if (hasCartItems) {
                finalAssistantMessage += "\n\nTu pedido actual sigue guardado:\n" + formatCartSummary(conversation.getCart()) + "\nTotal: $" + conversation.getCart().getTotal();
            }
        }
        // 2. Chitchat / "¿Cómo estás?"
        else if (lower.contains("como estas") || lower.contains("cómo estás") || lower.contains("como te va") || lower.contains("cómo te va") || lower.contains("que tal") || lower.contains("qué tal")) {
            finalAssistantMessage = "¡Estoy súper bien y listo para atenderte con el mejor sabor de Lemon Drop! 🍋 Cuéntame, ¿qué se te antoja hoy?";
        }
        // 3. Cart Review / "¿Cuánto llevo?"
        else if (lower.contains("cuanto llevo") || lower.contains("cuánto llevo") || lower.contains("ver mi pedido") || lower.contains("mi carrito") || lower.contains("total")) {
            if (hasCartItems) {
                finalAssistantMessage = "Llevas en tu pedido:\n" + formatCartSummary(conversation.getCart()) + "\n\nTotal: $" + conversation.getCart().getTotal();
                if (conversation.getObservations() != null && !conversation.getObservations().isEmpty()) {
                    finalAssistantMessage += "\nObservación: \"" + conversation.getObservations() + "\"";
                }
            } else {
                finalAssistantMessage = "Aún no tienes productos en tu pedido. ¿Qué granizado te gustaría que te preparemos? 🍋";
            }
        }
        // 4. Order notes / observations setting or inquiry
        else if ((lower.contains("nota") || lower.contains("observaci") || lower.contains("hielo") || lower.contains("frio") || lower.contains("frío")) && conversation.getObservations() != null) {
            if (hasCartItems && (pending == null || pending.isEmpty())) {
                conversation.setState(ConversationState.WAITING_CONFIRMATION);
                requiresConfirmation = true;
                finalAssistantMessage = "¡Perfecto" + (conversation.getCustomerName() != null ? ", " + conversation.getCustomerName() : "") + "! 🍋 Ya registré tus datos y tu observación: \"" + conversation.getObservations() + "\".\n\n" +
                        "Resumen de tu pedido:\n" + formatCartSummary(conversation.getCart()) + "\n\n" +
                        "Total: $" + conversation.getCart().getTotal() + "\n\n" +
                        "¿Confirmamos tu pedido para enviarlo a preparación en cocina? 🚀✨";
            } else {
                finalAssistantMessage = "¡Listo! 📝 Dejé anotada tu observación: \"" + conversation.getObservations() + "\".";
                if (hasCartItems) {
                    finalAssistantMessage += "\n\nResumen de tu pedido:\n" + formatCartSummary(conversation.getCart()) + "\nTotal: $" + conversation.getCart().getTotal();
                }
            }
        }
        // 5. Inquiries about missing data (e.g. "me estabas pidiendo el número no?") - ONLY when not providing a phone number
        else if ((lower.contains("pidiendo") || lower.contains("preguntando") || lower.contains("para qué") || lower.contains("para que") || lower.contains("no?")) && !cleanMessage.matches(".*\\d{7,15}.*")) {
            if (pending != null && pending.contains("PHONE")) {
                finalAssistantMessage = "¡Sí! 😊 Solo me falta tu número de teléfono de WhatsApp para registrar tu pedido y notificarte cuando esté listo para recoger. 📱";
            } else if (pending != null && pending.contains("NAME")) {
                finalAssistantMessage = "¡Sí! 😊 Solo me falta tu nombre para registrar tu pedido en la cocina. 🍋";
            } else {
                conversation.setState(ConversationState.WAITING_CONFIRMATION);
                requiresConfirmation = true;
                finalAssistantMessage = "¡Ya tengo todos tus datos! 🍋 ¿Confirmamos tu pedido para enviarlo a cocina?";
            }
        }
        // 6. Direct Confirmation ("Sí", "Confirmo", "Dale", "Pídelo", "Haz el pedido")
        else if (lower.equals("si") || lower.equals("sí") || lower.contains("confirmo") || lower.contains("dale") || lower.contains("pídelo") || lower.contains("pidelo") || lower.contains("hazlo") || lower.contains("de una") || lower.contains("claro")) {
            if (hasCartItems) {
                if (pending == null || pending.isEmpty()) {
                    AIToolResult confirmResult = toolRegistry.execute("confirmar_pedido", "{}", conversation);
                    if (confirmResult.isOrderCreated() && confirmResult.getData() instanceof Map<?, ?> map) {
                        conversation.setState(ConversationState.ORDER_CONFIRMED);
                        orderConfirmed = true;
                        orderCode = (String) map.get("orderCode");
                        whatsAppUrl = (String) map.get("whatsAppUrl");
                        finalAssistantMessage = "🎉 ¡Listo, " + (conversation.getCustomerName() != null ? conversation.getCustomerName() : "") + "! 🍋\n\nTu pedido " + orderCode + " quedó registrado exitosamente.\n\n🟢 Estado: Pedido recibido.\n\nTe avisaremos por WhatsApp cuando esté listo para recoger. ¡Muchas gracias por elegir Lemon Drop! 🍧✨";
                    } else {
                        finalAssistantMessage = confirmResult.getMessage() != null ? confirmResult.getMessage() : "Hubo un problema al confirmar tu pedido.";
                    }
                } else {
                    conversation.setState(ConversationState.COLLECTING_CUSTOMER);
                    requiresConfirmation = true;
                    if (pending.contains("NAME") && pending.contains("PHONE")) {
                        finalAssistantMessage = "¡Excelente! 🍋 Para enviar tu pedido a preparación en cocina, ¿a qué nombre y número de WhatsApp lo registramos? 📱";
                    } else if (pending.contains("PHONE")) {
                        finalAssistantMessage = "¡Listo, " + conversation.getCustomerName() + "! 🍋 Solo me falta tu número de WhatsApp para confirmar tu pedido. 📱";
                    } else {
                        finalAssistantMessage = "¡Listo! 📱 Solo me falta tu nombre para confirmar tu pedido. 🍋";
                    }
                }
            } else {
                finalAssistantMessage = "Tu carrito está vacío actualmente. ¿Qué granizado te gustaría que te preparemos? 🍋";
                attachAllActiveProducts(collectedProducts);
            }
        }
        // 7. Customization / Size / Topping specification
        else if (containsSizeKeyword(lower) || containsToppingKeyword(lower)) {
            String flavor = findTargetFlavor(lower, conversation);
            String size = extractSize(lower);
            List<String> toppings = extractToppings(lower);

            Map<String, Object> addArgs = new HashMap<>();
            addArgs.put("productName", flavor != null ? flavor : "Granizado de Limón");
            addArgs.put("size", size != null ? size : "MEDIUM");
            if (!toppings.isEmpty()) {
                addArgs.put("addons", toppings);
            }
            addArgs.put("quantity", 1);

            try {
                String argsJson = objectMapper.writeValueAsString(addArgs);
                AIToolResult addResult = toolRegistry.execute("agregar_producto", argsJson, conversation);
                cartUpdated = addResult.isCartModified();

                if (pending == null || pending.isEmpty()) {
                    conversation.setState(ConversationState.WAITING_CONFIRMATION);
                    requiresConfirmation = true;
                    finalAssistantMessage = "¡Listo! 🙌 Agregué al carrito:\n" +
                            "• " + (flavor != null ? flavor : "Granizado de Limón") + " (" + (size != null ? size : "MEDIUM") + ")" +
                            (!toppings.isEmpty() ? " con " + String.join(", ", toppings) : "") + "\n\n" +
                            "Total del carrito: $" + conversation.getCart().getTotal() + "\n\n" +
                            "¿Confirmamos este pedido para enviarlo a preparación en cocina? 🚀✨";
                } else {
                    conversation.setState(ConversationState.COLLECTING_CUSTOMER);
                    finalAssistantMessage = "¡Listo! 🙌 Agregué al carrito:\n" +
                            "• " + (flavor != null ? flavor : "Granizado de Limón") + " (" + (size != null ? size : "MEDIUM") + ")" +
                            (!toppings.isEmpty() ? " con " + String.join(", ", toppings) : "") + "\n\n" +
                            "Total del carrito: $" + conversation.getCart().getTotal() + "\n\n" +
                            "Tu granizado está casi listo. ¿A qué nombre y número de WhatsApp registramos tu pedido? 📱✨";
                }
            } catch (Exception ex) {
                finalAssistantMessage = "¡Listo! Ya tomé nota de tu pedido. ¿A qué nombre y número de WhatsApp lo confirmamos? 📱";
            }
        }
        // 8. User providing Name and/or Phone when in COLLECTING_CUSTOMER or when cart has items
        else if (conversation.getState() == ConversationState.COLLECTING_CUSTOMER || (hasCartItems && (conversation.getCustomerName() != null || conversation.getCustomerPhone() != null))) {
            if (pending == null || pending.isEmpty()) {
                conversation.setState(ConversationState.WAITING_CONFIRMATION);
                requiresConfirmation = true;
                finalAssistantMessage = "¡Perfecto, " + conversation.getCustomerName() + "! 🍋 Ya tengo tus datos registrados.\n\n" +
                        "Resumen de tu pedido:\n" + formatCartSummary(conversation.getCart()) + "\n\n" +
                        "Total: $" + conversation.getCart().getTotal() + "\n\n" +
                        "¿Confirmamos tu pedido para enviarlo a preparación en cocina? 🚀✨";
            } else if (pending.contains("PHONE") && !pending.contains("NAME")) {
                conversation.setState(ConversationState.COLLECTING_CUSTOMER);
                finalAssistantMessage = "¡Mucho gusto, " + conversation.getCustomerName() + "! 🍋 Ahora solo me falta tu número de WhatsApp para poder notificarte cuando tu granizado esté listo. 📱";
            } else if (pending.contains("NAME") && !pending.contains("PHONE")) {
                conversation.setState(ConversationState.COLLECTING_CUSTOMER);
                finalAssistantMessage = "¡Perfecto! 📱 Ya anoté tu número " + conversation.getCustomerPhone() + ". Ahora indícame tu nombre para registrar el pedido. 🍋";
            } else {
                finalAssistantMessage = "¡Excelente! 🍋 Para completar tu orden, ¿a qué nombre y número de WhatsApp la registramos? 📱";
            }
        }
        // 9. Product Order Selection ("Quiero un Granizado de Limón", "el de limón porfa")
        else if (isSpecificProductOrder(lower)) {
            String flavor = findTargetFlavor(lower, conversation);
            if (flavor == null) flavor = "Granizado de Limón";

            conversation.setState(ConversationState.BUILDING_ORDER);
            finalAssistantMessage = "¡De una! 🍋 Vamos a armar tu " + flavor + ".\n\n" +
                    "Solo necesito que me confirmes:\n\n" +
                    "1️⃣ Tamaño:\n" +
                    "- SMALL (pequeño) – $5.000\n" +
                    "- MEDIUM (mediano) – $7.000\n" +
                    "- LARGE (grande) – $9.000\n\n" +
                    "2️⃣ Toppings (opcionales, +$1.000 cada uno):\n" +
                    "- Leche condensada\n" +
                    "- Arequipe\n\n" +
                    "¿Qué tamaño y qué toppings te gustaría? 🚀✨";

            attachSingleProduct(flavor, collectedProducts);
        }
        // 10. Catalogue Inquiry
        else if (isProductInquiry(lower)) {
            conversation.setState(ConversationState.DISCOVERING);
            finalAssistantMessage = "¡Claro que sí! 🍋 Aquí te muestro nuestras opciones de granizados disponibles:";
            attachAllActiveProducts(collectedProducts);
        }
        // 11. Greeting
        else if (lower.contains("hola") || lower.contains("buenas") || lower.contains("hey")) {
            conversation.setState(ConversationState.DISCOVERING);
            finalAssistantMessage = "¡Hola! 🍋 ¿Qué granizado se te antoja hoy? Puedes elegir tu sabor favorito o pedirme recomendaciones.";
            attachAllActiveProducts(collectedProducts);
        }
        // 12. Recommendation
        else if (lower.contains("recomiend") || lower.contains("dulce") || lower.contains("acido") || lower.contains("ácido")) {
            conversation.setState(ConversationState.DISCOVERING);
            finalAssistantMessage = "¡Te recomiendo nuestro Granizado de Limón clásico o el de Maracuyá con leche condensada! 🍋✨";
            attachAllActiveProducts(collectedProducts);
        }
        // 13. General fallback
        else {
            finalAssistantMessage = "¡Con gusto te atiendo! 🍋 Cuéntame qué sabor o tamaño de granizado deseas que te preparemos hoy.";
            attachAllActiveProducts(collectedProducts);
        }

        AIMessage assistantMsg = AIMessage.builder()
                .role("assistant")
                .content(finalAssistantMessage)
                .timestamp(LocalDateTime.now())
                .build();
        conversation.addMessage(assistantMsg);
        conversationService.save(conversation);

        AIChatResponse response = buildStandardResponse(conversation, finalAssistantMessage, startTime, cartUpdated, requiresConfirmation, orderConfirmed);
        if (orderCode != null) response.setOrderCode(orderCode);
        if (whatsAppUrl != null) response.setWhatsAppUrl(whatsAppUrl);
        if (!collectedProducts.isEmpty()) response.setProducts(collectedProducts);
        return response;
    }

    private String formatCartSummary(AICart cart) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return "• Granizado de Limón (MEDIUM)";
        }
        StringBuilder sb = new StringBuilder();
        for (AICartItem item : cart.getItems()) {
            sb.append("• ").append(item.getProductName()).append(" (").append(item.getSize() != null ? item.getSize().name() : "MEDIUM").append(")");
            if (item.getAddons() != null && !item.getAddons().isEmpty()) {
                sb.append(" con ").append(item.getAddons().stream().map(AICartItemAddon::getAddonName).collect(Collectors.joining(", ")));
            }
            sb.append(" — $").append(item.getSubtotal()).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean isSpecificProductOrder(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        boolean hasOrderVerb = lower.contains("quiero") || lower.contains("pedir") || lower.contains("dame") ||
                lower.contains("el de") || lower.contains("la de") || lower.contains("un ") || lower.contains("uno ") ||
                lower.contains("porfa") || lower.contains("armar");
        boolean hasFlavor = lower.contains("limon") || lower.contains("limón") || lower.contains("maracu") ||
                lower.contains("cereza") || lower.contains("mango") || lower.contains("fresa") || lower.contains("naranja");
        return (hasOrderVerb && hasFlavor) || (hasFlavor && !lower.contains("qué") && !lower.contains("que") && !lower.contains("mostrar") && !lower.contains("carta") && !lower.contains("menu"));
    }

    private boolean containsSizeKeyword(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("pequeñ") || lower.contains("pequeno") || lower.contains("small") || lower.contains("chico") ||
               lower.contains("median") || lower.contains("medium") || lower.contains("grande") || lower.contains("large");
    }

    private boolean containsToppingKeyword(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("arequipe") || lower.contains("leche condensada") || lower.contains("topping") || lower.contains("toppings") || lower.contains("adicional");
    }

    private String extractSize(String text) {
        if (text == null) return "MEDIUM";
        String lower = text.toLowerCase();
        if (lower.contains("pequeñ") || lower.contains("pequeno") || lower.contains("small") || lower.contains("chico")) return "SMALL";
        if (lower.contains("grande") || lower.contains("large")) return "LARGE";
        return "MEDIUM";
    }

    private List<String> extractToppings(String text) {
        List<String> toppings = new ArrayList<>();
        if (text == null) return toppings;
        String lower = text.toLowerCase();
        if (lower.contains("arequipe")) toppings.add("Arequipe");
        if (lower.contains("leche condensada") || lower.contains("lecherita") || lower.contains("condensada")) toppings.add("Leche condensada");
        return toppings;
    }

    private String findTargetFlavor(String text, AIConversation conversation) {
        if (text != null) {
            String lower = text.toLowerCase();
            if (lower.contains("limon") || lower.contains("limón")) return "Granizado de Limón";
            if (lower.contains("maracu")) return "Granizado de Maracuyá";
            if (lower.contains("cereza")) return "Granizado de Cereza";
            if (lower.contains("mango")) return "Granizado de Mango";
            if (lower.contains("fresa")) return "Granizado de Fresa";
        }
        if (conversation != null && conversation.getMessages() != null) {
            for (int i = conversation.getMessages().size() - 1; i >= 0; i--) {
                String prev = conversation.getMessages().get(i).getContent();
                if (prev != null) {
                    String pLower = prev.toLowerCase();
                    if (pLower.contains("limon") || pLower.contains("limón")) return "Granizado de Limón";
                    if (pLower.contains("maracu")) return "Granizado de Maracuyá";
                    if (pLower.contains("cereza")) return "Granizado de Cereza";
                    if (pLower.contains("mango")) return "Granizado de Mango";
                    if (pLower.contains("fresa")) return "Granizado de Fresa";
                }
            }
        }
        return "Granizado de Limón";
    }

    private void attachSingleProduct(String flavorName, List<AIProductCardDto> target) {
        if (productService == null) return;
        try {
            List<Product> prods = productService.getAllActiveAndAvailable();
            for (Product p : prods) {
                if (p.getName() != null && (p.getName().equalsIgnoreCase(flavorName) || p.getName().toLowerCase().contains(flavorName.toLowerCase()))) {
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
                    return;
                }
            }
            attachAllActiveProducts(target);
        } catch (Exception ignored) {}
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
