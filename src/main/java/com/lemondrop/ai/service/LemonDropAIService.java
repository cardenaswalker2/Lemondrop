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
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public enum UserIntent {
        GREETING(false),
        CASUAL_CHAT(false),
        BUSINESS_INFO(false),
        AI_IDENTITY(false),
        CUSTOMER_DATA(false),
        CONTEXTUAL_SELECTION(false),
        SEARCH_PRODUCTS(true),
        RECOMMENDATION(true),
        ORDER_INTENT(true),
        ORDER_CONFIRMATION(true),
        GENERAL(true);

        private final boolean requiresTools;

        UserIntent(boolean requiresTools) {
            this.requiresTools = requiresTools;
        }

        public boolean requiresTools() {
            return requiresTools;
        }
    }

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

        // Auto-extract customer info and order preferences from user message
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
            return buildStandardResponse(conversation, "¿En qué puedo ayudarte hoy con tu granizado? 🍋", startTime, false, false, false, UserIntent.GREETING.name());
        }

        // 3. Detect User Intent
        UserIntent intent = detectIntent(cleanMessage, conversation);
        log.info("Intención detectada: {} para el mensaje: '{}'", intent, cleanMessage);

        // 4. Append User Message
        AIMessage userMsg = AIMessage.builder()
                .role("user")
                .content(cleanMessage)
                .timestamp(LocalDateTime.now())
                .build();
        conversation.addMessage(userMsg);

        // 5. If Groq is not configured, notify clearly
        if (!groqClient.isAvailable()) {
            log.warn("Groq API no está configurada o no tiene API key válida.");
            return buildStandardResponse(
                    conversation,
                    "Lemon AI no está disponible en este momento. Por favor realiza tu pedido desde el menú digital. 🍋",
                    startTime,
                    false,
                    false,
                    false,
                    intent.name()
            );
        }

        // 6. Agent Loop with Function Calling
        boolean cartUpdated = false;
        boolean requiresConfirmation = false;
        boolean orderConfirmed = false;
        String finalAssistantMessage = "";
        String lastOrderCode = conversation.getConfirmedOrderCode();
        String whatsAppUrl = null;

        int iterations = 0;
        int maxIterations = intent.requiresTools() ? lemonAiProperties.getMaxToolIterations() : 1;
        List<AIProductCardDto> collectedProducts = new ArrayList<>();

        while (iterations < maxIterations) {
            iterations++;

            GroqChatRequest groqRequest = buildGroqRequest(conversation, intent);
            Optional<GroqChatResponse> optResponse = groqClient.sendChatCompletion(groqRequest);

            if (optResponse.isEmpty() || optResponse.get().getChoices() == null || optResponse.get().getChoices().isEmpty()) {
                log.error("Groq devolvió una respuesta vacía o con error en la iteración {}.", iterations);
                return buildStandardResponse(
                        conversation,
                        "Lemon AI está experimentando alta demanda. Por favor intenta de nuevo en unos segundos. 🍋",
                        startTime,
                        false,
                        false,
                        false,
                        intent.name()
                );
            }

            GroqChatResponse.GroqChoice choice = optResponse.get().getChoices().get(0);
            GroqMessage choiceMsg = choice.getMessage();

            if (choiceMsg == null) {
                break;
            }

            List<GroqToolCall> toolCalls = choiceMsg.getToolCalls();

            // If Groq wants to call one or more tools (only when tools are enabled for this intent)
            if (intent.requiresTools() && toolCalls != null && !toolCalls.isEmpty()) {
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

                    // Extract products ONLY from tools that explicitly search or recommend products
                    if (result.isSuccess() && result.getData() != null) {
                        extractProductsFromResult(result.getData(), collectedProducts);
                    }

                    // Append Tool Result to conversation (compact JSON)
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

        // Store last shown product names in metadata for subsequent contextual references
        if (!collectedProducts.isEmpty()) {
            List<String> productNames = collectedProducts.stream()
                    .map(AIProductCardDto::getName)
                    .collect(Collectors.toList());
            if (conversation.getMetadata() == null) {
                conversation.setMetadata(new HashMap<>());
            }
            conversation.getMetadata().put("lastShownProducts", productNames);
        }

        // Track last selected/recommended product if assistant mentioned one from options
        if (finalAssistantMessage != null && conversation.getMetadata() != null && conversation.getMetadata().containsKey("lastShownProducts")) {
            Object obj = conversation.getMetadata().get("lastShownProducts");
            if (obj instanceof List<?> list) {
                String msgLower = finalAssistantMessage.toLowerCase();
                for (Object pName : list) {
                    if (pName instanceof String name) {
                        String cleanFlavor = name.toLowerCase().replace("granizado de ", "").trim();
                        if (msgLower.contains(cleanFlavor)) {
                            conversation.getMetadata().put("lastRecommendedProduct", name);
                            break;
                        }
                    }
                }
            }
        }

        // Save conversation state
        conversationService.save(conversation);

        // Build structured response (Strict product attachment: only from explicit tool execution)
        AIChatResponse response = buildStandardResponse(conversation, finalAssistantMessage, startTime, cartUpdated, requiresConfirmation, orderConfirmed, intent.name());
        if (lastOrderCode != null) response.setOrderCode(lastOrderCode);
        if (whatsAppUrl != null) response.setWhatsAppUrl(whatsAppUrl);
        if (!collectedProducts.isEmpty()) {
            response.setProducts(collectedProducts);
        } else {
            response.setProducts(new ArrayList<>());
        }

        return response;
    }

    public UserIntent detectIntent(String rawMessage, AIConversation conv) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) return UserIntent.GENERAL;
        String norm = normalize(rawMessage);

        // 1. AI Identity
        if (norm.contains("que modelo usas") || norm.contains("que modelo de ia") || norm.contains("que ia eres") ||
            norm.contains("eres una ia") || norm.contains("como funcionas") || norm.contains("quien te creo") ||
            norm.contains("que arquitectura") || norm.contains("eres un bot") || norm.contains("eres un robot")) {
            return UserIntent.AI_IDENTITY;
        }

        // 2. Business Info
        if (norm.contains("horario") || norm.contains("a que hora") || norm.contains("donde estan") ||
            norm.contains("ubicacion") || norm.contains("direccion") || norm.contains("donde quedan") ||
            norm.contains("whatsapp") || norm.contains("medios de pago") || norm.contains("metodos de pago") ||
            norm.contains("como pagar") || norm.contains("donde los encuentro")) {
            return UserIntent.BUSINESS_INFO;
        }

        // 3. Casual greeting
        if (norm.matches("^(hola|buenas|buenos dias|buenas tardes|buenas noches|hey|holis|saludos|que mas)[!.\\s]*$")) {
            return UserIntent.GREETING;
        }

        // 4. Casual chat
        if (norm.matches("^(como estas|que tal|como te va|todo bien|que haces|como va todo)[?!.\\s]*$")) {
            return UserIntent.CASUAL_CHAT;
        }

        // 5. Contextual Selection (e.g. "entre esos 3 escoge uno", "cual es el segundo", "el primero", "el de mango", "escoge al azar")
        if (hasShownProductsInContext(conv) && isContextualReference(norm)) {
            return UserIntent.CONTEXTUAL_SELECTION;
        }

        // 6. Explicit Customer Data (Name or Phone provided in response to prompt)
        if (isCustomerDataInput(rawMessage, conv)) {
            return UserIntent.CUSTOMER_DATA;
        }

        // 7. Order Confirmation
        if (isConfirmationIntent(norm, conv)) {
            return UserIntent.ORDER_CONFIRMATION;
        }

        // 8. Recommendation
        if (norm.contains("recomiend") || norm.contains("mas vendido") || norm.contains("mas vendidos") ||
            norm.contains("lo mas rico") || norm.contains("algo rico") || norm.contains("sugier") ||
            norm.contains("cual es el mejor") || norm.contains("que me aconsejas")) {
            return UserIntent.RECOMMENDATION;
        }

        // 9. Search Products / Catalog Inquiry
        if (norm.contains("que sabores") || norm.contains("que granizados") || norm.contains("menu") ||
            norm.contains("carta") || norm.contains("catalogo") || norm.contains("que tienen") ||
            norm.contains("tienen de") || norm.contains("hay de") || norm.contains("muestrame") ||
            norm.contains("mostrar") || norm.contains("que opciones") || norm.contains("que productos")) {
            return UserIntent.SEARCH_PRODUCTS;
        }

        // 10. Order intent
        if (norm.contains("quiero") || norm.contains("dame") || norm.contains("pedir") ||
            norm.contains("agregar") || norm.contains("ponle") || norm.contains("grande") ||
            norm.contains("mediano") || norm.contains("pequeno") || norm.contains("topping") ||
            norm.contains("gomitas") || norm.contains("arequipe") || norm.contains("lechera")) {
            return UserIntent.ORDER_INTENT;
        }

        return UserIntent.GENERAL;
    }

    private boolean isContextualReference(String norm) {
        return norm.contains("entre esos") || norm.contains("escoge uno") || norm.contains("elige uno") ||
               norm.contains("al azar") || norm.contains("por mi") || norm.contains("el primero") ||
               norm.contains("el segundo") || norm.contains("el tercero") || norm.contains("segundo") ||
               norm.contains("tercero") || norm.contains("primero") || norm.contains("cual es el segundo") ||
               norm.contains("cual es el primero") || norm.contains("el que me recomendaste") ||
               norm.equals("ese") || norm.equals("esa") || norm.equals("aquel") || norm.equals("aquella");
    }

    private boolean hasShownProductsInContext(AIConversation conv) {
        if (conv == null) return false;
        if (conv.getMetadata() != null && conv.getMetadata().containsKey("lastShownProducts")) {
            return true;
        }
        // Check previous assistant messages for listed products
        if (conv.getMessages() != null) {
            for (AIMessage msg : conv.getMessages()) {
                if ("assistant".equals(msg.getRole()) && msg.getContent() != null) {
                    String c = msg.getContent().toLowerCase();
                    if (c.contains("1.") || c.contains("2.") || c.contains("•") || c.contains("granizado")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isConfirmationIntent(String norm, AIConversation conv) {
        boolean hasItems = conv.getCart() != null && !conv.getCart().getItems().isEmpty();
        boolean isWaiting = conv.getState() == ConversationState.WAITING_CONFIRMATION;
        if (isWaiting || hasItems) {
            return norm.matches("^(si|sí|dale|confirmo|confirmar|confirmado|de una|pidelo|pídelo|listo|ok|hagale|hágale)[!.\\s]*$") ||
                   norm.contains("confirmo el pedido") || norm.contains("confirmar pedido");
        }
        return false;
    }

    private boolean isCustomerDataInput(String raw, AIConversation conv) {
        if (conv.getState() == ConversationState.COLLECTING_CUSTOMER ||
            (conv.getPendingCustomerFields() != null && !conv.getPendingCustomerFields().isEmpty())) {
            String norm = normalize(raw);
            if (norm.matches("^3\\d{9}$") || norm.matches("^\\d{7,15}$")) return true;
            if (raw.matches("^[\\p{L} ]{2,30}$") && isValidName(raw)) return true;
        }
        return false;
    }

    private GroqChatRequest buildGroqRequest(AIConversation conversation, UserIntent intent) {
        List<GroqMessage> messages = new ArrayList<>();

        // 1. System Prompt
        messages.add(GroqMessage.builder()
                .role("system")
                .content(buildSystemPrompt(conversation, intent))
                .build());

        // 2. Conversation History (Windowed to last 6 messages to preserve context and optimize tokens)
        if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
            List<AIMessage> history = conversation.getMessages();
            int maxHistory = 6;
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

        // Tools assignment based on intent
        List<GroqTool> tools = intent.requiresTools() ? toolRegistry.getGroqTools() : null;
        String toolChoice = intent.requiresTools() ? "auto" : "none";

        return GroqChatRequest.builder()
                .model(groqProperties.getApi().getModel())
                .messages(messages)
                .tools(tools)
                .toolChoice(toolChoice)
                .temperature(0.4)
                .maxTokens(350)
                .build();
    }

    private void extractAndPersistCustomerInfo(String text, AIConversation conv) {
        if (text == null || text.trim().isEmpty() || conv == null) return;
        String raw = text.trim();

        // 1. Extract phone number (7 to 15 digits, Colombian format 3xx xxx xxxx, +57, etc.)
        Pattern phonePattern = Pattern.compile("(?:\\+?57\\s*)?(3\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{4}|\\b\\d{7,15}\\b)");
        Matcher phoneMatcher = phonePattern.matcher(raw);
        if (phoneMatcher.find()) {
            String rawFound = phoneMatcher.group(1);
            String digits = rawFound.replaceAll("[^0-9]", "");
            if (digits.length() >= 7 && digits.length() <= 15) {
                conv.setCustomerPhone(digits);
            }
        }

        // 2. Extract name
        if (conv.getCustomerName() == null || conv.getCustomerName().isEmpty()) {
            Matcher pMatcher = phonePattern.matcher(raw);
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

        if (conv.getCustomerName() == null || conv.getCustomerName().isEmpty()) {
            Pattern explicitNamePattern = Pattern.compile("(?i)(?:mi nombre es|me llamo|soy)\\s+([\\p{L} ]{2,30})");
            Matcher enMatcher = explicitNamePattern.matcher(raw);
            if (enMatcher.find()) {
                String foundName = enMatcher.group(1).trim();
                if (isValidName(foundName)) {
                    conv.setCustomerName(foundName);
                }
            }
        }

        if ((conv.getCustomerName() == null || conv.getCustomerName().isEmpty()) &&
                (conv.getState() == ConversationState.COLLECTING_CUSTOMER || conv.getPendingCustomerFields().contains("NAME"))) {
            if (raw.matches("^[\\p{L} ]{2,30}$") && isValidName(raw)) {
                conv.setCustomerName(raw.trim());
            }
        }

        // 3. Extract Order Notes / Observations
        Pattern notePattern = Pattern.compile("(?i)(?:(?:pon(?:le)?|deja|agrega)?\\s*(?:en|de)?\\s*(?:la\\s+)?(?:nota|observación|observacion|indicación|indicacion)(?:\\s+es)?(?:\\s*:\\s*|\\s+que\\s+|\\s+)|sin\\s+(?:mucho\\s+|tanto\\s+)?hielo|bien\\s+fr[ií]o|poca\\s+az[uú]car|sin\\s+az[uú]car)(.*)");
        Matcher noteMatcher = notePattern.matcher(raw);
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

    private String buildSystemPrompt(AIConversation conv, UserIntent intent) {
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Bogota"));
        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy, hh:mm a", new java.util.Locale("es", "CO"));
        String currentDateTimeFormatted = now.format(dtf);

        StringBuilder clientContext = new StringBuilder();
        if (conv.getCustomerName() != null && !conv.getCustomerName().isEmpty()) {
            clientContext.append("\n- Nombre: ").append(conv.getCustomerName());
        }
        if (conv.getCustomerPhone() != null && !conv.getCustomerPhone().isEmpty()) {
            clientContext.append("\n- Teléfono: ").append(conv.getCustomerPhone());
        }

        StringBuilder cartContext = new StringBuilder();
        if (conv.getCart() != null && conv.getCart().getItems() != null && !conv.getCart().getItems().isEmpty()) {
            cartContext.append("\nCARRITO ACTUAL:");
            for (AICartItem item : conv.getCart().getItems()) {
                cartContext.append("\n• ")
                        .append(item.getQuantity()).append("x ")
                        .append(item.getProductName()).append(" (").append(item.getSize()).append(")");
                if (item.getAddons() != null && !item.getAddons().isEmpty()) {
                    cartContext.append(" + Toppings: ").append(item.getAddons().stream().map(AICartItemAddon::getAddonName).collect(Collectors.joining(", ")));
                }
                cartContext.append(" - $").append(item.getSubtotal());
            }
            cartContext.append("\nTotal: $").append(conv.getCart().getTotal());
        }

        StringBuilder lastProductsContext = new StringBuilder();
        if (conv.getMetadata() != null && conv.getMetadata().containsKey("lastShownProducts")) {
            Object obj = conv.getMetadata().get("lastShownProducts");
            if (obj instanceof List<?> list && !list.isEmpty()) {
                lastProductsContext.append("\nOPCIONES RECIENTES MOSTRADAS AL CLIENTE: ");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) lastProductsContext.append(", ");
                    lastProductsContext.append((i + 1)).append(". ").append(list.get(i));
                }
            }
        }
        if (conv.getMetadata() != null && conv.getMetadata().containsKey("lastRecommendedProduct")) {
            lastProductsContext.append("\nÚLTIMA OPCIÓN ELEGIDA/RECOMENDADA: ").append(conv.getMetadata().get("lastRecommendedProduct"));
        }

        return """
                Eres Lemon Drop AI 🍋, el asistente comercial de Lemon Drop (tienda de granizados y bebidas refrescantes).
                
                INFORMACIÓN DEL NEGOCIO:
                - Fecha/Hora: %s (Colombia)
                - Horario: Lunes a Domingo de 10:00 AM a 9:00 PM.
                - Ubicación: Calle 10 # 40-20, Medellín, Colombia.
                
                ESTADO ACTUAL:%s%s%s
                
                DIRECTIVAS OBLIGATORIAS:
                1. ESTILO: Responde de forma natural, humana, ágil y comercial (1 a 3 frases máximo).
                2. SIN RELLENO: NUNCA digas "¡Entendido! 🎉", "Solo necesito saber...", "Como siempre...", "Como asistente virtual...", "Permíteme ayudarte...", "Con esa información procederé...".
                3. SOBRE LA IA: Si preguntan qué modelo o IA eres, responde brevemente: "Soy la IA de Lemon Drop 🍋✨". No des explicaciones técnicas salvo que lo pidan explícitamente.
                4. ELECCIONES Y REFERENCIAS CONTEXTUALES:
                   - Si el usuario dice "entre esos 3 escoge uno", "escoge uno al azar", "el segundo", "el primero", "el de mango", utiliza directamente las opciones mostradas en el contexto.
                   - Si el usuario dice "quiero ese", "ese", "agrega ese" o "el que me recomendaste", utiliza directamente la ÚLTIMA OPCIÓN ELEGIDA/RECOMENDADA o la opción seleccionada sin volver a consultar el catálogo.
                   - Responde directo (ej. "🎲 Me quedo con el de mango 😋" o "El segundo es el de mango"). NO vuelvas a pedir las opciones.
                5. FLUJO DE PEDIDO PASO A PASO:
                   - 1. Sabor -> 2. Tamaño (Mediano/Grande) -> 3. Toppings -> 4. Cantidad -> 5. Datos -> 6. Confirmación.
                   - Pregunta solo el siguiente dato necesario. Si ya conoces el sabor o tamaño, NO lo vuelvas a preguntar.
                6. RECOMENDACIONES: Recomienda máximo 1 a 3 productos relevantes del catálogo oficial.
                7. HERRAMIENTAS: Para agregar al carrito usa `agregar_producto`, para confirmar usa `confirmar_pedido`. Si el usuario solo saluda o charla, responde amablemente en texto sin llamar herramientas de catálogo.
                """.formatted(currentDateTimeFormatted, clientContext.toString(), cartContext.toString(), lastProductsContext.toString());
    }

    private AIChatResponse buildStandardResponse(AIConversation conv, String message, long startTime,
                                                 boolean cartUpdated, boolean requiresConfirmation, boolean orderConfirmed,
                                                 String intent) {
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
                .intent(intent != null ? intent : (orderConfirmed ? "ORDER_CONFIRMED" : (requiresConfirmation ? "WAITING_CONFIRMATION" : "DISCOVERING")))
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

        AIChatResponse response = buildStandardResponse(conv, msg, startTime, true, false, orderCreated, "ORDER_ACTION");
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
                if (item instanceof Map<?, ?> map && (map.containsKey("name") || map.containsKey("productName"))) {
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
            String name = (String) p.getOrDefault("name", p.getOrDefault("productName", ""));
            if (name == null || name.trim().isEmpty()) continue;

            // Avoid duplicates
            if (target.stream().anyMatch(existing -> existing.getName().equalsIgnoreCase(name))) {
                continue;
            }

            BigDecimal priceFrom = BigDecimal.ZERO;
            if (p.get("priceFrom") instanceof BigDecimal bd) {
                priceFrom = bd;
            } else if (p.get("startingPrice") instanceof BigDecimal bd) {
                priceFrom = bd;
            } else if (p.get("priceFrom") instanceof Number num) {
                priceFrom = BigDecimal.valueOf(num.doubleValue());
            } else if (p.get("startingPrice") instanceof Number num) {
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

    private String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}

