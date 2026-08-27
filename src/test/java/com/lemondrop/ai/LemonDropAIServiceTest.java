package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.groq.GroqChatRequest;
import com.lemondrop.ai.dto.groq.GroqChatResponse;
import com.lemondrop.ai.dto.groq.GroqMessage;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.service.LemonDropAIService.UserIntent;
import com.lemondrop.ai.service.SecurityAuditService;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.lemondrop.ai.dto.groq.GroqToolCall;
import com.lemondrop.ai.model.ConversationState;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LemonDropAIServiceTest {

    private GroqClient groqClient;
    private GroqProperties groqProperties;
    private LemonAiProperties lemonAiProperties;
    private AIConversationService conversationService;
    private AIToolRegistry toolRegistry;
    private SecurityAuditService auditService;
    private ObjectMapper objectMapper;
    private ProductService productService;
    private LemonDropAIService aiService;

    @BeforeEach
    void setUp() {
        groqClient = mock(GroqClient.class);
        groqProperties = new GroqProperties();
        groqProperties.getApi().setModel("openai/gpt-oss-120b");
        lemonAiProperties = new LemonAiProperties();
        conversationService = mock(AIConversationService.class);
        toolRegistry = mock(AIToolRegistry.class);
        auditService = mock(SecurityAuditService.class);
        when(auditService.sanitizeInput(any())).thenAnswer(inv -> inv.getArgument(0));
        objectMapper = new ObjectMapper();
        productService = mock(ProductService.class);

        when(groqClient.isAvailable()).thenReturn(true);

        aiService = new LemonDropAIService(
                groqClient,
                groqProperties,
                lemonAiProperties,
                conversationService,
                toolRegistry,
                auditService,
                objectMapper,
                productService
        );
    }

    private void mockGroqResponse(String text) {
        GroqChatResponse response = new GroqChatResponse();
        GroqChatResponse.GroqChoice choice = new GroqChatResponse.GroqChoice();
        choice.setMessage(GroqMessage.builder().role("assistant").content(text).build());
        response.setChoices(List.of(choice));
        when(groqClient.sendChatCompletion(any())).thenReturn(Optional.of(response));
    }

    @Test
    void testIntentDetection_AllCases() {
        AIConversation conv = AIConversation.builder().conversationId("conv-1").build();

        assertEquals(UserIntent.GREETING, aiService.detectIntent("Hola", conv));
        assertFalse(UserIntent.GREETING.requiresTools());

        assertEquals(UserIntent.CASUAL_CHAT, aiService.detectIntent("Cómo estás?", conv));
        assertFalse(UserIntent.CASUAL_CHAT.requiresTools());

        assertEquals(UserIntent.AI_IDENTITY, aiService.detectIntent("¿Qué modelo de IA usas?", conv));
        assertFalse(UserIntent.AI_IDENTITY.requiresTools());

        assertEquals(UserIntent.BUSINESS_INFO, aiService.detectIntent("¿Cuál es el horario?", conv));
        assertFalse(UserIntent.BUSINESS_INFO.requiresTools());

        assertEquals(UserIntent.SEARCH_PRODUCTS, aiService.detectIntent("¿Qué sabores tienen?", conv));
        assertTrue(UserIntent.SEARCH_PRODUCTS.requiresTools());

        assertEquals(UserIntent.RECOMMENDATION, aiService.detectIntent("Recomiéndame algo rico", conv));
        assertTrue(UserIntent.RECOMMENDATION.requiresTools());

        assertEquals(UserIntent.ORDER_INTENT, aiService.detectIntent("Quiero uno de mango", conv));
        assertTrue(UserIntent.ORDER_INTENT.requiresTools());
    }

    @Test
    void testGreeting_ReturnsCleanText_NoProducts() {
        AIConversation conv = AIConversation.builder().conversationId("conv-1").build();
        when(conversationService.getOrCreateConversation(any(), any(), any(), any())).thenReturn(conv);
        mockGroqResponse("¡Hola! 🍋 ¿Qué se te antoja hoy?");

        AIChatRequest req = AIChatRequest.builder().message("Hola").build();
        AIChatResponse resp = aiService.processMessage(req);

        assertEquals("¡Hola! 🍋 ¿Qué se te antoja hoy?", resp.getMessage());
        assertTrue(resp.getProducts().isEmpty(), "No debe incluir productos en un saludo");
        assertEquals("GREETING", resp.getIntent());

        ArgumentCaptor<GroqChatRequest> captor = ArgumentCaptor.forClass(GroqChatRequest.class);
        verify(groqClient).sendChatCompletion(captor.capture());
        assertEquals("none", captor.getValue().getToolChoice());
        assertNull(captor.getValue().getTools());
    }

    @Test
    void testAIIdentity_ReturnsBriefResponse_NoProducts() {
        AIConversation conv = AIConversation.builder().conversationId("conv-1").build();
        when(conversationService.getOrCreateConversation(any(), any(), any(), any())).thenReturn(conv);
        mockGroqResponse("Soy la IA de Lemon Drop 🍋✨");

        AIChatRequest req = AIChatRequest.builder().message("¿Qué modelo de IA eres?").build();
        AIChatResponse resp = aiService.processMessage(req);

        assertEquals("Soy la IA de Lemon Drop 🍋✨", resp.getMessage());
        assertTrue(resp.getProducts().isEmpty());
        assertEquals("AI_IDENTITY", resp.getIntent());
    }

    @Test
    void testContextualSelection_UsesHistoryWithoutCallingToolsOrDumpingProducts() {
        AIConversation conv = AIConversation.builder()
                .conversationId("conv-1")
                .metadata(new HashMap<>(Map.of("lastShownProducts", List.of("Granizado de Limón", "Granizado de Mango", "Granizado de Fresa"))))
                .build();
        when(conversationService.getOrCreateConversation(any(), any(), any(), any())).thenReturn(conv);
        mockGroqResponse("🎲 Me quedo con el de mango 😋");

        AIChatRequest req = AIChatRequest.builder().message("entre esos 3 escoge uno").build();
        AIChatResponse resp = aiService.processMessage(req);

        assertEquals("🎲 Me quedo con el de mango 😋", resp.getMessage());
        assertTrue(resp.getProducts().isEmpty(), "No debe volcar catálogo si se está eligiendo por contexto");
        assertEquals("CONTEXTUAL_SELECTION", resp.getIntent());

        ArgumentCaptor<GroqChatRequest> captor = ArgumentCaptor.forClass(GroqChatRequest.class);
        verify(groqClient).sendChatCompletion(captor.capture());
        assertEquals("none", captor.getValue().getToolChoice());
    }

    @Test
    void testCriticalFlow_FullStepByStepSimulation() {
        AIConversation conv = AIConversation.builder()
                .conversationId("conv-sim-1")
                .clientToken("token-123")
                .metadata(new HashMap<>())
                .build();
        when(conversationService.getOrCreateConversation(any(), any(), any(), any())).thenReturn(conv);

        // --- PASO 1: "Hola" ---
        mockGroqResponse("¡Hola! 🍋 ¿Qué se te antoja hoy?");
        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder().message("Hola").build());
        assertEquals("GREETING", r1.getIntent());
        assertTrue(r1.getProducts().isEmpty(), "Paso 1: No debe retornar productos");

        // --- PASO 2: "Qué sabores tienen?" ---
        GroqChatResponse toolCallResp = new GroqChatResponse();
        GroqChatResponse.GroqChoice choiceTool = new GroqChatResponse.GroqChoice();
        choiceTool.setMessage(GroqMessage.builder()
                .role("assistant")
                .toolCalls(List.of(GroqToolCall.builder()
                        .id("call-search")
                        .type("function")
                        .function(GroqToolCall.GroqFunctionCall.builder()
                                .name("buscar_productos")
                                .arguments("{\"query\":\"\"}")
                                .build())
                        .build()))
                .build());
        toolCallResp.setChoices(List.of(choiceTool));

        GroqChatResponse finalResp2 = new GroqChatResponse();
        GroqChatResponse.GroqChoice choiceFinal2 = new GroqChatResponse.GroqChoice();
        choiceFinal2.setMessage(GroqMessage.builder().role("assistant").content("Tenemos Limón 🍋, Mango 🥭 y Fresa 🍓.").build());
        finalResp2.setChoices(List.of(choiceFinal2));

        when(groqClient.sendChatCompletion(any())).thenReturn(Optional.of(toolCallResp), Optional.of(finalResp2));
        when(toolRegistry.execute(eq("buscar_productos"), any(), any())).thenReturn(
                com.lemondrop.ai.dto.AIToolResult.builder()
                        .toolName("buscar_productos")
                        .success(true)
                        .data(List.of(
                                Map.of("name", "Granizado de Limón", "priceFrom", 8000),
                                Map.of("name", "Granizado de Mango", "priceFrom", 8500),
                                Map.of("name", "Granizado de Fresa", "priceFrom", 8000)
                        ))
                        .build()
        );

        AIChatResponse r2 = aiService.processMessage(AIChatRequest.builder().message("Qué sabores tienen?").build());
        assertEquals(3, r2.getProducts().size(), "Paso 2: Debe retornar los 3 productos consultados");
        assertTrue(conv.getMetadata().containsKey("lastShownProducts"), "Debe guardar los 3 productos en metadata");

        // --- PASO 3: "Entre esos tres escoge uno" ---
        mockGroqResponse("🎲 Me quedo con el de mango 😋");
        AIChatResponse r3 = aiService.processMessage(AIChatRequest.builder().message("Entre esos tres escoge uno").build());
        assertEquals("CONTEXTUAL_SELECTION", r3.getIntent());
        assertTrue(r3.getProducts().isEmpty(), "Paso 3: No debe consultar catálogo ni mostrar productos");

        // --- PASO 4: "Quiero ese" ---
        GroqChatResponse toolAddResp = new GroqChatResponse();
        GroqChatResponse.GroqChoice choiceAdd = new GroqChatResponse.GroqChoice();
        choiceAdd.setMessage(GroqMessage.builder()
                .role("assistant")
                .toolCalls(List.of(GroqToolCall.builder()
                        .id("call-add")
                        .type("function")
                        .function(GroqToolCall.GroqFunctionCall.builder()
                                .name("agregar_producto")
                                .arguments("{\"productName\":\"Granizado de Mango\",\"size\":\"MEDIUM\",\"quantity\":1}")
                                .build())
                        .build()))
                .build());
        toolAddResp.setChoices(List.of(choiceAdd));

        GroqChatResponse finalResp4 = new GroqChatResponse();
        GroqChatResponse.GroqChoice choiceFinal4 = new GroqChatResponse.GroqChoice();
        choiceFinal4.setMessage(GroqMessage.builder().role("assistant").content("Listo 🥭 ¿Qué tamaño te gustaría?").build());
        finalResp4.setChoices(List.of(choiceFinal4));

        when(groqClient.sendChatCompletion(any())).thenReturn(Optional.of(toolAddResp), Optional.of(finalResp4));
        when(toolRegistry.execute(eq("agregar_producto"), any(), any())).thenReturn(
                com.lemondrop.ai.dto.AIToolResult.builder()
                        .toolName("agregar_producto")
                        .success(true)
                        .cartModified(true)
                        .message("Producto agregado")
                        .build()
        );

        AIChatResponse r4 = aiService.processMessage(AIChatRequest.builder().message("Quiero ese").build());
        assertTrue(r4.getProducts().isEmpty(), "Paso 4: Products debe ser vacío al agregar al carrito");

        // --- PASO 5: "Grande" ---
        mockGroqResponse("Perfecto, granizado de mango grande. ¿Le ponemos algún topping? 😋");
        AIChatResponse r5 = aiService.processMessage(AIChatRequest.builder().message("Grande").build());
        assertTrue(r5.getProducts().isEmpty(), "Paso 5: Products vacío al ajustar tamaño");

        // --- PASO 6: "Sí, confirmo" ---
        conv.getCart().setItems(List.of(com.lemondrop.ai.model.AICartItem.builder()
                .productName("Granizado de Mango")
                .size(com.lemondrop.model.ProductSize.LARGE)
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(9500))
                .subtotal(BigDecimal.valueOf(9500))
                .build()));
        conv.setState(ConversationState.WAITING_CONFIRMATION);

        GroqChatResponse toolConfirmResp = new GroqChatResponse();
        GroqChatResponse.GroqChoice choiceConfirm = new GroqChatResponse.GroqChoice();
        choiceConfirm.setMessage(GroqMessage.builder()
                .role("assistant")
                .toolCalls(List.of(GroqToolCall.builder()
                        .id("call-confirm")
                        .type("function")
                        .function(GroqToolCall.GroqFunctionCall.builder()
                                .name("confirmar_pedido")
                                .arguments("{}")
                                .build())
                        .build()))
                .build());
        toolConfirmResp.setChoices(List.of(choiceConfirm));

        GroqChatResponse finalResp6 = new GroqChatResponse();
        GroqChatResponse.GroqChoice choiceFinal6 = new GroqChatResponse.GroqChoice();
        choiceFinal6.setMessage(GroqMessage.builder().role("assistant").content("¡Listo! Tu pedido LD-2026-0001 ha sido recibido. 🎉").build());
        finalResp6.setChoices(List.of(choiceFinal6));

        when(groqClient.sendChatCompletion(any())).thenReturn(Optional.of(toolConfirmResp), Optional.of(finalResp6));
        when(toolRegistry.execute(eq("confirmar_pedido"), any(), any())).thenReturn(
                com.lemondrop.ai.dto.AIToolResult.builder()
                        .toolName("confirmar_pedido")
                        .success(true)
                        .orderCreated(true)
                        .data(Map.of("orderCode", "LD-2026-0001", "whatsAppUrl", "https://wa.me/573001234567"))
                        .message("Pedido confirmado exitosamente")
                        .build()
        );

        AIChatResponse r6 = aiService.processMessage(AIChatRequest.builder().message("Sí, confirmo").build());
        assertTrue(r6.getProducts().isEmpty(), "Paso 6: Products vacío al confirmar");
        assertTrue(r6.isOrderConfirmed(), "Paso 6: El pedido debe estar marcado como confirmado");
        assertEquals("LD-2026-0001", r6.getOrderCode());
    }
}
