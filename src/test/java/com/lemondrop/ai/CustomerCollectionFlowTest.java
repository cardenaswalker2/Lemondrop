package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.dto.groq.GroqChatRequest;
import com.lemondrop.ai.dto.groq.GroqChatResponse;
import com.lemondrop.ai.dto.groq.GroqFunction;
import com.lemondrop.ai.dto.groq.GroqMessage;
import com.lemondrop.ai.dto.groq.GroqToolCall;
import com.lemondrop.ai.model.AICart;
import com.lemondrop.ai.model.AICartItem;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.model.ConversationState;
import com.lemondrop.ai.repository.AIConversationRepository;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.service.SecurityAuditService;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.ai.tools.impl.GeneralTools;
import com.lemondrop.model.ProductSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class CustomerCollectionFlowTest {

    private GroqClient groqClient;
    private GroqProperties groqProperties;
    private LemonAiProperties lemonAiProperties;
    private AIConversationRepository conversationRepository;
    private AIConversationService conversationService;
    private AIToolRegistry toolRegistry;
    private SecurityAuditService auditService;
    private ObjectMapper objectMapper;
    private LemonDropAIService aiService;
    private GeneralTools generalTools;
    private Map<String, AIConversation> memoryStore;

    @BeforeEach
    void setUp() {
        groqClient = Mockito.mock(GroqClient.class);
        conversationRepository = Mockito.mock(AIConversationRepository.class);
        auditService = Mockito.mock(SecurityAuditService.class);

        groqProperties = new GroqProperties();
        groqProperties.getApi().setKey("test-key");
        lemonAiProperties = new LemonAiProperties();
        objectMapper = new ObjectMapper();

        conversationService = new AIConversationService(conversationRepository, lemonAiProperties);
        toolRegistry = new AIToolRegistry(objectMapper);

        // Register dummy agregar_producto tool
        toolRegistry.register(AIToolDefinition.builder()
                .name("agregar_producto")
                .description("Agrega producto")
                .parametersSchema(Map.of())
                .executor((args, conv) -> {
                    AICartItem item = AICartItem.builder()
                            .productName((String) args.getOrDefault("productName", "Granizado de Limón"))
                            .size(ProductSize.MEDIUM)
                            .quantity(1)
                            .unitPrice(BigDecimal.valueOf(7000))
                            .subtotal(BigDecimal.valueOf(7000))
                            .build();
                    conv.getCart().getItems().add(item);
                    conv.getCart().setTotal(BigDecimal.valueOf(7000));
                    return AIToolResult.builder()
                            .toolName("agregar_producto")
                            .success(true)
                            .cartModified(true)
                            .message("Producto agregado")
                            .build();
                })
                .build());

        // Register dummy confirmar_pedido tool
        toolRegistry.register(AIToolDefinition.builder()
                .name("confirmar_pedido")
                .description("Confirma pedido")
                .parametersSchema(Map.of())
                .executor((args, conv) -> {
                    conv.setState(ConversationState.ORDER_CONFIRMED);
                    conv.setConfirmedOrderCode("LD-2026-00099");
                    return AIToolResult.builder()
                            .toolName("confirmar_pedido")
                            .success(true)
                            .orderCreated(true)
                            .data(Map.of("orderCode", "LD-2026-00099", "whatsAppUrl", "https://wa.me/573005722844"))
                            .message("Pedido confirmado exitosamente")
                            .build();
                })
                .build());

        // Register dummy buscar_productos tool
        toolRegistry.register(AIToolDefinition.builder()
                .name("buscar_productos")
                .description("Busca productos")
                .parametersSchema(Map.of())
                .executor((args, conv) -> AIToolResult.builder()
                        .toolName("buscar_productos")
                        .success(true)
                        .data(List.of(
                                Map.of("id", "p1", "name", "Granizado de Limón", "priceFrom", BigDecimal.valueOf(5000), "available", true),
                                Map.of("id", "p2", "name", "Granizado de Mango", "priceFrom", BigDecimal.valueOf(5000), "available", true)
                        ))
                        .message("Granizados encontrados")
                        .build())
                .build());

        // Register GeneralTools
        generalTools = new GeneralTools(toolRegistry);
        generalTools.registerTools();

        memoryStore = new HashMap<>();
        when(conversationRepository.findByConversationId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(memoryStore.get(inv.getArgument(0))));
        when(conversationRepository.save(any(AIConversation.class)))
                .thenAnswer(inv -> {
                    AIConversation c = inv.getArgument(0);
                    memoryStore.put(c.getConversationId(), c);
                    return c;
                });
        when(auditService.sanitizeInput(anyString())).thenAnswer(i -> i.getArgument(0));

        aiService = new LemonDropAIService(
                groqClient, groqProperties, lemonAiProperties,
                conversationService, toolRegistry, auditService, objectMapper
        );
    }

    @Test
    @DisplayName("Caso 1: Pregunta libre general ('¿eres una IA?') obtiene respuesta directa de Groq sin fallback de producto")
    void testGeneralQuestionDoesNotTriggerOrderFallback() {
        when(groqClient.isAvailable()).thenReturn(true);

        GroqChatResponse groqResp = GroqChatResponse.builder()
                .id("chat-1")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .content("¡Hola! 🍋 Sí, soy Lemon Drop AI, el asistente inteligente oficial de Lemon Drop. Te puedo ayudar a consultar el menú, armar tu pedido o resolver cualquier duda que tengas.")
                                .build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any(GroqChatRequest.class))).thenReturn(Optional.of(groqResp));

        AIChatResponse response = aiService.processMessage(AIChatRequest.builder()
                .message("eres una ia?")
                .build());

        assertTrue(response.isSuccess());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("Lemon Drop AI"));
        // Does NOT contain the hardcoded fallback
        assertFalse(response.getMessage().contains("Cuéntame qué sabor o tamaño de granizado deseas que te preparemos hoy"));
    }

    @Test
    @DisplayName("Caso 2: Consulta de catálogo ('¿cuáles hay?') ejecuta tool de catálogo a través del Agent Loop")
    void testCatalogQuestionUsesCatalogTool() {
        when(groqClient.isAvailable()).thenReturn(true);

        // Turn 1: Groq requests tool call
        GroqToolCall toolCall = GroqToolCall.builder()
                .id("call-123")
                .type("function")
                .function(GroqToolCall.GroqFunctionCall.builder()
                        .name("buscar_productos")
                        .arguments("{\"query\":\"granizados\"}")
                        .build())
                .build();

        GroqChatResponse iter1 = GroqChatResponse.builder()
                .id("resp-1")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .toolCalls(List.of(toolCall))
                                .build())
                        .build()))
                .build();

        // Turn 2: Groq receives tool result and produces final answer
        GroqChatResponse iter2 = GroqChatResponse.builder()
                .id("resp-2")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .content("¡Tenemos deliciosos granizados de Limón y Mango! 🍋🥭 ¿Cuál te gustaría probar?")
                                .build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any(GroqChatRequest.class)))
                .thenReturn(Optional.of(iter1), Optional.of(iter2));

        AIChatResponse response = aiService.processMessage(AIChatRequest.builder()
                .message("cuales hay?")
                .build());

        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("Limón"));
        assertNotNull(response.getProducts());
        assertEquals(2, response.getProducts().size());
    }

    @Test
    @DisplayName("Caso 3: Pregunta general durante WAITING_CONFIRMATION preserva el pedido intacto")
    void testGeneralQuestionDuringWaitingConfirmationPreservesOrder() {
        when(groqClient.isAvailable()).thenReturn(true);

        // Pre-create conversation in WAITING_CONFIRMATION with active cart
        AIConversation conv = AIConversation.builder()
                .conversationId("conv-waiting")
                .clientToken("token-123")
                .customerName("Juan")
                .customerPhone("3005722844")
                .state(ConversationState.WAITING_CONFIRMATION)
                .cart(AICart.builder()
                        .cartId("cart-1")
                        .subtotal(BigDecimal.valueOf(7000))
                        .total(BigDecimal.valueOf(7000))
                        .items(List.of(AICartItem.builder()
                                .productName("Granizado de Limón")
                                .size(ProductSize.MEDIUM)
                                .quantity(1)
                                .subtotal(BigDecimal.valueOf(7000))
                                .build()))
                        .build())
                .build();
        memoryStore.put("conv-waiting", conv);

        GroqChatResponse groqResp = GroqChatResponse.builder()
                .id("resp-convo")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .content("¡Así es! Soy una IA conversacional lista para ayudarte. Cuando quieras, confirmamos tu Granizado de Limón mediano.")
                                .build())
                        .build()))
                .build();
        when(groqClient.sendChatCompletion(any(GroqChatRequest.class))).thenReturn(Optional.of(groqResp));

        AIChatResponse response = aiService.processMessage(AIChatRequest.builder()
                .conversationId("conv-waiting")
                .clientToken("token-123")
                .message("eres una ia?")
                .build());

        assertTrue(response.isSuccess());
        // Cart is NOT lost!
        assertNotNull(response.getCart());
        assertEquals(1, response.getCart().getItems().size());
        assertEquals("Juan", response.getCustomerName());
        assertEquals("3005722844", response.getCustomerPhone());
        assertFalse(response.isOrderConfirmed());
    }

    @Test
    @DisplayName("Caso 4: Solo se confirma el pedido cuando el usuario confirma explícitamente")
    void testConfirmationOnlyConfirmsWhenExplicit() {
        when(groqClient.isAvailable()).thenReturn(true);

        AIConversation conv = AIConversation.builder()
                .conversationId("conv-confirm")
                .clientToken("token-456")
                .customerName("Carlos")
                .customerPhone("3101234567")
                .state(ConversationState.WAITING_CONFIRMATION)
                .cart(AICart.builder()
                        .cartId("cart-2")
                        .subtotal(BigDecimal.valueOf(7000))
                        .total(BigDecimal.valueOf(7000))
                        .items(List.of(AICartItem.builder()
                                .productName("Granizado de Mango")
                                .size(ProductSize.MEDIUM)
                                .quantity(1)
                                .subtotal(BigDecimal.valueOf(7000))
                                .build()))
                        .build())
                .build();
        memoryStore.put("conv-confirm", conv);

        // Turn 1: User sends "eres una ia?" -> NO confirmation tool called
        GroqChatResponse groqResp1 = GroqChatResponse.builder()
                .id("r1")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .content("¡Sí! Soy Lemon Drop AI.")
                                .build())
                        .build()))
                .build();
        when(groqClient.sendChatCompletion(any(GroqChatRequest.class))).thenReturn(Optional.of(groqResp1));

        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder()
                .conversationId("conv-confirm")
                .clientToken("token-456")
                .message("eres una ia?")
                .build());

        assertFalse(r1.isOrderConfirmed());
        assertNull(r1.getOrderCode());

        // Turn 2: User sends "sí, confírmalo" -> Groq calls confirmar_pedido tool
        GroqToolCall confirmCall = GroqToolCall.builder()
                .id("call-confirm")
                .type("function")
                .function(GroqToolCall.GroqFunctionCall.builder()
                        .name("confirmar_pedido")
                        .arguments("{\"customerName\":\"Carlos\",\"customerPhone\":\"3101234567\"}")
                        .build())
                .build();

        GroqChatResponse iterConfirm1 = GroqChatResponse.builder()
                .id("rc1")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .toolCalls(List.of(confirmCall))
                                .build())
                        .build()))
                .build();

        GroqChatResponse iterConfirm2 = GroqChatResponse.builder()
                .id("rc2")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .content("🎉 ¡Listo, Carlos! Tu pedido LD-2026-00099 ha sido recibido.")
                                .build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any(GroqChatRequest.class)))
                .thenReturn(Optional.of(iterConfirm1), Optional.of(iterConfirm2));

        AIChatResponse r2 = aiService.processMessage(AIChatRequest.builder()
                .conversationId("conv-confirm")
                .clientToken("token-456")
                .message("sí, confírmalo")
                .build());

        assertTrue(r2.isOrderConfirmed());
        assertEquals("LD-2026-00099", r2.getOrderCode());
    }

    @Test
    @DisplayName("Caso 5: Extracción y persistencia de nombre, teléfono y observaciones en una sola frase")
    void testCustomerNamePhoneAndNotePersist() {
        when(groqClient.isAvailable()).thenReturn(true);

        GroqChatResponse groqResp = GroqChatResponse.builder()
                .id("resp-note")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder()
                                .role("assistant")
                                .content("¡Perfecto, Juan! Ya anoté tu número 3005722844 y tu indicación de no ponerle mucho hielo. 📝🍧")
                                .build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any(GroqChatRequest.class))).thenReturn(Optional.of(groqResp));

        AIChatResponse response = aiService.processMessage(AIChatRequest.builder()
                .message("Soy Juan, mi número es 3005722844 y pon de nota que no quiero mucho hielo")
                .build());

        assertTrue(response.isSuccess());
        assertEquals("Juan", response.getCustomerName());
        assertEquals("3005722844", response.getCustomerPhone());
        assertNotNull(response.getObservations());
        assertTrue(response.getObservations().toLowerCase().contains("no quiero mucho hielo"));
    }
}
