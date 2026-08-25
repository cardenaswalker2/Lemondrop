package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.AICartItem;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.repository.AIConversationRepository;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.service.SecurityAuditService;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.model.ProductSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.HashMap;
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
                .executor((args, conv) -> AIToolResult.builder()
                        .toolName("confirmar_pedido")
                        .success(true)
                        .orderCreated(true)
                        .data(Map.of("orderCode", "LD-2026-00099", "whatsAppUrl", "https://wa.me/573005722844"))
                        .message("Pedido confirmado")
                        .build())
                .build());

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
    @DisplayName("Caso 1: Extracción combinada de nombre y teléfono en un solo mensaje")
    void testExtractNameAndPhoneCombinedMessage() {
        when(groqClient.isAvailable()).thenReturn(false);

        // Turn 1: Add product to cart
        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder()
                .message("Quiero un Granizado de Limón mediano con arequipe")
                .build());
        assertTrue(r1.isSuccess());
        assertEquals("COLLECTING_CUSTOMER", r1.getState());
        assertEquals(2, r1.getPendingCustomerFields().size());

        // Turn 2: User provides "Juan y el número es 3005722844"
        AIChatResponse r2 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Juan y el número es 3005722844")
                .build());

        assertTrue(r2.isSuccess());
        assertEquals("WAITING_CONFIRMATION", r2.getState());
        assertEquals("Juan", r2.getCustomerName());
        assertEquals("3005722844", r2.getCustomerPhone());
        assertTrue(r2.getPendingCustomerFields().isEmpty());
        assertTrue(r2.getMessage().contains("Juan"));
        assertTrue(r2.getMessage().contains("¿Confirmamos"));
        assertNull(r2.getProducts());
    }

    @Test
    @DisplayName("Caso 2: Extracción con coma (ej. Carlos, 3101234567)")
    void testExtractNameAndPhoneCommaSeparated() {
        when(groqClient.isAvailable()).thenReturn(false);

        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder()
                .message("Granizado de Limón grande")
                .build());

        AIChatResponse r2 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Carlos, 3101234567")
                .build());

        assertEquals("WAITING_CONFIRMATION", r2.getState());
        assertEquals("Carlos", r2.getCustomerName());
        assertEquals("3101234567", r2.getCustomerPhone());
        assertTrue(r2.getPendingCustomerFields().isEmpty());
    }

    @Test
    @DisplayName("Caso 3 y 4: Manejo progresivo con preguntas intermedias del usuario")
    void testPartialDataOnlyNameFirstThenPhone() {
        when(groqClient.isAvailable()).thenReturn(false);

        // Turn 1: Add product
        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder()
                .message("Granizado de Limón grande")
                .build());

        // Turn 2: Only name
        AIChatResponse r2 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Juan")
                .build());

        assertEquals("COLLECTING_CUSTOMER", r2.getState());
        assertEquals("Juan", r2.getCustomerName());
        assertTrue(r2.getPendingCustomerFields().contains("PHONE"));
        assertTrue(r2.getMessage().contains("número de WhatsApp") || r2.getMessage().contains("teléfono"));

        // Turn 3: User asks "me estabas pidiendo el número no?"
        AIChatResponse r3 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("me estabas pidiendo el número no?")
                .build());

        assertEquals("COLLECTING_CUSTOMER", r3.getState());
        assertTrue(r3.getMessage().contains("número de WhatsApp") || r3.getMessage().contains("teléfono"));
        assertNull(r3.getProducts()); // Does not flood catalogue

        // Turn 4: User gives phone
        AIChatResponse r4 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("3005722844")
                .build());

        assertEquals("WAITING_CONFIRMATION", r4.getState());
        assertEquals("3005722844", r4.getCustomerPhone());
        assertTrue(r4.getMessage().contains("¿Confirmamos"));

        // Turn 5: User confirms "Sí"
        AIChatResponse r5 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Sí")
                .build());

        assertEquals("ORDER_CONFIRMED", r5.getState());
        assertTrue(r5.isOrderConfirmed());
        assertNotNull(r5.getOrderCode());
        assertTrue(r5.getMessage().contains("Pedido recibido"));
    }

    @Test
    @DisplayName("Caso 5: Solo teléfono primero, luego nombre")
    void testPhoneFirstThenName() {
        when(groqClient.isAvailable()).thenReturn(false);

        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder()
                .message("Granizado de Limón mediano")
                .build());

        // Send phone only
        AIChatResponse r2 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("3005722844")
                .build());

        assertEquals("COLLECTING_CUSTOMER", r2.getState());
        assertEquals("3005722844", r2.getCustomerPhone());
        assertTrue(r2.getPendingCustomerFields().contains("NAME"));
        assertTrue(r2.getMessage().contains("nombre"));

        // Send name only
        AIChatResponse r3 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Juan")
                .build());

        assertEquals("WAITING_CONFIRMATION", r3.getState());
        assertEquals("Juan", r3.getCustomerName());
        assertTrue(r3.getPendingCustomerFields().isEmpty());
    }

    @Test
    @DisplayName("Caso 6: Confirmación con frase alternativa (ej. 'Dale pídelo')")
    void testAlternativeConfirmationPhrases() {
        when(groqClient.isAvailable()).thenReturn(false);

        AIChatResponse r1 = aiService.processMessage(AIChatRequest.builder()
                .message("Granizado de Limón grande")
                .build());

        aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Juan y el número es 3005722844")
                .build());

        AIChatResponse r3 = aiService.processMessage(AIChatRequest.builder()
                .conversationId(r1.getConversationId())
                .clientToken(r1.getClientToken())
                .message("Dale pídelo")
                .build());

        assertEquals("ORDER_CONFIRMED", r3.getState());
        assertTrue(r3.isOrderConfirmed());
        assertNotNull(r3.getOrderCode());
    }
}
