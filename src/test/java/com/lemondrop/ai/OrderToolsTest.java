package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.*;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.ai.tools.impl.OrderTools;
import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.ProductSize;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OrderToolsTest {

    private AIToolRegistry registry;
    private OrderService orderService;
    private WhatsAppService whatsAppService;
    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        registry = new AIToolRegistry(new ObjectMapper());
        orderService = Mockito.mock(OrderService.class);
        whatsAppService = Mockito.mock(WhatsAppService.class);

        orderTools = new OrderTools(registry, orderService, whatsAppService);
        orderTools.registerTools();
    }

    @Test
    void testCrearBorradorPedido() {
        AICart cart = AICart.builder()
                .cartId("cart-1")
                .items(List.of(AICartItem.builder()
                        .productName("Granizado")
                        .size(ProductSize.MEDIUM)
                        .subtotal(new BigDecimal("6000"))
                        .build()))
                .total(new BigDecimal("6000"))
                .build();

        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(cart)
                .build();

        AIToolResult result = registry.execute("crear_borrador_pedido", "{\"customerName\": \"Carlos\", \"customerPhone\": \"3001234567\"}", conv);

        assertTrue(result.isSuccess());
        assertTrue(result.isRequiresConfirmation());
        assertEquals(ConversationState.WAITING_CONFIRMATION, conv.getState());
        assertEquals("Carlos", conv.getCustomerName());
    }

    @Test
    void testConfirmarPedidoCallsRealOrderService() {
        AICart cart = AICart.builder()
                .cartId("cart-1")
                .items(List.of(AICartItem.builder()
                        .productId("p1")
                        .flavorId("f1")
                        .size(ProductSize.LARGE)
                        .quantity(1)
                        .subtotal(new BigDecimal("8000"))
                        .build()))
                .total(new BigDecimal("8000"))
                .build();

        AIConversation conv = AIConversation.builder()
                .conversationId("c123")
                .customerName("Ana")
                .customerPhone("3109876543")
                .cart(cart)
                .build();

        Order mockOrder = Order.builder()
                .id("order-id-1")
                .orderCode("LD-2026-00042")
                .customerName("Ana")
                .customerPhone("3109876543")
                .total(new BigDecimal("8000"))
                .status(OrderStatus.RECEIVED)
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(mockOrder);
        when(whatsAppService.generateWhatsAppUrl(any(Order.class))).thenReturn("https://wa.me/573109876543?text=hola");

        AIToolResult result = registry.execute("confirmar_pedido", "{}", conv);

        assertTrue(result.isSuccess());
        assertTrue(result.isOrderCreated());
        assertEquals("LD-2026-00042", conv.getConfirmedOrderCode());
        assertEquals(ConversationState.ORDER_CONFIRMED, conv.getState());
        assertEquals(AICart.CartStatus.CONFIRMED, conv.getCart().getStatus());
    }

    @Test
    void testConsultarPedidoSecurity() {
        Order mockOrder = Order.builder()
                .orderCode("LD-2026-00042")
                .customerPhone("3001234567")
                .customerName("Juan")
                .status(OrderStatus.PREPARING)
                .total(new BigDecimal("10000"))
                .items(new ArrayList<>())
                .build();

        when(orderService.getOrderByCode("LD-2026-00042")).thenReturn(Optional.of(mockOrder));

        AIConversation conv = AIConversation.builder().conversationId("c1").build();

        // Valid phone matching order
        AIToolResult resMatch = registry.execute("consultar_pedido", "{\"orderCode\": \"LD-2026-00042\", \"customerPhone\": \"3001234567\"}", conv);
        assertTrue(resMatch.isSuccess());

        // Mismatched phone
        AIToolResult resMismatch = registry.execute("consultar_pedido", "{\"orderCode\": \"LD-2026-00042\", \"customerPhone\": \"3999999999\"}", conv);
        assertFalse(resMismatch.isSuccess());
    }

    @Test
    void testCancelarPedidoRules() {
        Order receivedOrder = Order.builder()
                .id("ord-1")
                .orderCode("LD-2026-00001")
                .customerPhone("3001234567")
                .status(OrderStatus.RECEIVED)
                .build();

        Order preparingOrder = Order.builder()
                .id("ord-2")
                .orderCode("LD-2026-00002")
                .customerPhone("3001234567")
                .status(OrderStatus.PREPARING)
                .build();

        when(orderService.getOrderByCode("LD-2026-00001")).thenReturn(Optional.of(receivedOrder));
        when(orderService.getOrderByCode("LD-2026-00002")).thenReturn(Optional.of(preparingOrder));

        AIConversation conv = AIConversation.builder().conversationId("c1").customerPhone("3001234567").build();

        // Canceling RECEIVED order succeeds
        AIToolResult can1 = registry.execute("cancelar_pedido", "{\"orderCode\": \"LD-2026-00001\"}", conv);
        assertTrue(can1.isSuccess());

        // Canceling PREPARING order fails (business rule violation prevention)
        AIToolResult can2 = registry.execute("cancelar_pedido", "{\"orderCode\": \"LD-2026-00002\"}", conv);
        assertFalse(can2.isSuccess());
        assertTrue(can2.getMessage().contains("ya está en estado"));
    }

    @Test
    void testConfirmarPedidoRequiresCustomerName() {
        AICart cart = AICart.builder()
                .cartId("cart-1")
                .items(List.of(AICartItem.builder().productId("p1").flavorId("f1").size(ProductSize.MEDIUM).quantity(1).subtotal(new BigDecimal("6000")).build()))
                .total(new BigDecimal("6000"))
                .build();

        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(cart)
                .customerPhone("3001234567")
                .build(); // No customerName

        AIToolResult result = registry.execute("confirmar_pedido", "{}", conv);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Necesito tu nombre"));
    }

    @Test
    void testConfirmarPedidoRequiresCustomerPhone() {
        AICart cart = AICart.builder()
                .cartId("cart-1")
                .items(List.of(AICartItem.builder().productId("p1").flavorId("f1").size(ProductSize.MEDIUM).quantity(1).subtotal(new BigDecimal("6000")).build()))
                .total(new BigDecimal("6000"))
                .build();

        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(cart)
                .customerName("Mateo")
                .build(); // No customerPhone

        AIToolResult result = registry.execute("confirmar_pedido", "{}", conv);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("falta tu número de teléfono"));
    }

    @Test
    void testConfirmarPedidoNormalizesPhone() {
        AICart cart = AICart.builder()
                .cartId("cart-1")
                .items(List.of(AICartItem.builder().productId("p1").flavorId("f1").size(ProductSize.MEDIUM).quantity(1).subtotal(new BigDecimal("6000")).build()))
                .total(new BigDecimal("6000"))
                .build();

        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(cart)
                .customerName("Mateo")
                .customerPhone("+57 300 123-4567")
                .build();

        Order mockOrder = Order.builder()
                .id("order-id-1")
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.RECEIVED)
                .total(new BigDecimal("6000"))
                .build();

        when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(mockOrder);
        when(whatsAppService.generateWhatsAppUrl(any(Order.class))).thenReturn("https://wa.me/573001234567?text=hola");

        AIToolResult result = registry.execute("confirmar_pedido", "{}", conv);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("Pedido recibido"));
        assertEquals("3001234567", conv.getCustomerPhone());
    }
}
