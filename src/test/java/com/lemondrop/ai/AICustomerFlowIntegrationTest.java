package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.AICart;
import com.lemondrop.ai.model.AICartItem;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.controller.advisor.AdvisorDashboardController;
import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.model.*;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AICustomerFlowIntegrationTest {

    private OrderService orderService;
    private WhatsAppService whatsAppService;
    private AIToolRegistry toolRegistry;
    private AIConversationService conversationService;
    private AdvisorDashboardController advisorController;

    @BeforeEach
    void setUp() {
        orderService = Mockito.mock(OrderService.class);
        whatsAppService = new WhatsAppService("57");
        toolRegistry = new AIToolRegistry(new ObjectMapper());
        conversationService = Mockito.mock(AIConversationService.class);
        advisorController = new AdvisorDashboardController(
                orderService,
                Mockito.mock(com.lemondrop.service.ProductService.class),
                Mockito.mock(com.lemondrop.service.FlavorService.class),
                Mockito.mock(com.lemondrop.service.AddonService.class),
                whatsAppService,
                Mockito.mock(com.lemondrop.repository.OrderStatusHistoryRepository.class),
                Mockito.mock(com.lemondrop.repository.OrderChangeHistoryRepository.class)
        );
    }

    @Test
    void testEndToEndCustomerDataFlowToOrderAndAdvisor() {
        // 1. User Conversation has customer Mateo and phone 3001234567
        AICart cart = AICart.builder()
                .cartId("cart-mateo")
                .items(List.of(AICartItem.builder()
                        .productId("prod-lemon")
                        .productName("Granizado de Limón")
                        .flavorId("flv-lemon")
                        .flavorName("Limón")
                        .size(ProductSize.MEDIUM)
                        .quantity(1)
                        .subtotal(new BigDecimal("7000"))
                        .build()))
                .total(new BigDecimal("7000"))
                .build();

        AIConversation conversation = AIConversation.builder()
                .conversationId("conv-mateo-1")
                .clientToken("token-123")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .cart(cart)
                .build();

        // 2. OrderService mock returns created order with customer details
        Order createdOrder = Order.builder()
                .id("order-id-mateo")
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.RECEIVED)
                .total(new BigDecimal("7000"))
                .createdAt(LocalDateTime.now())
                .observations("Sin azúcar extra")
                .items(List.of(OrderItem.builder()
                        .productName("Granizado de Limón")
                        .flavorName("Limón")
                        .size(ProductSize.MEDIUM)
                        .quantity(1)
                        .subtotal(new BigDecimal("7000"))
                        .build()))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(createdOrder);
        when(orderService.getAllOrders()).thenReturn(List.of(createdOrder));

        // 3. Register tools and execute confirmar_pedido
        com.lemondrop.ai.tools.impl.OrderTools orderTools = new com.lemondrop.ai.tools.impl.OrderTools(toolRegistry, orderService, whatsAppService);
        orderTools.registerTools();

        AIToolResult confirmResult = toolRegistry.execute("confirmar_pedido", "{\"customerName\": \"Mateo\", \"customerPhone\": \"3001234567\"}", conversation);

        assertTrue(confirmResult.isSuccess());
        assertTrue(confirmResult.isOrderCreated());
        assertEquals("LD-2026-00023", conversation.getConfirmedOrderCode());
        assertEquals("Mateo", conversation.getCustomerName());
        assertEquals("3001234567", conversation.getCustomerPhone());

        // 4. Verify WhatsApp message is for RECEIVED state (not ready)
        String waUrl = (String) ((Map<?, ?>) confirmResult.getData()).get("whatsAppUrl");
        assertNotNull(waUrl);
        assertTrue(waUrl.contains("573001234567"));
        assertTrue(waUrl.contains("Pedido+recibido"));
        assertFalse(waUrl.contains("ya+est%C3%A1+listo+para+recoger"));

        // 5. Verify Advisor API receives and returns customerPhone
        ResponseEntity<?> updatesResponse = advisorController.getActiveOrdersApi();
        assertEquals(200, updatesResponse.getStatusCode().value());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeOrders = (List<Map<String, Object>>) updatesResponse.getBody();
        assertNotNull(activeOrders);
        assertEquals(1, activeOrders.size());

        Map<String, Object> advisorOrderView = activeOrders.get(0);
        assertEquals("LD-2026-00023", advisorOrderView.get("code"));
        assertEquals("Mateo", advisorOrderView.get("customerName"));
        assertEquals("3001234567", advisorOrderView.get("customerPhone"));
        assertEquals("RECEIVED", advisorOrderView.get("status"));
        assertNotEquals("Sin teléfono", advisorOrderView.get("customerPhone"));
    }
}
