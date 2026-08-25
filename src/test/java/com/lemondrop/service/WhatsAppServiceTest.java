package com.lemondrop.service;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppServiceTest {

    private WhatsAppService whatsAppService;

    @BeforeEach
    void setUp() {
        whatsAppService = new WhatsAppService("57");
    }

    @Test
    void testReceivedStatusMessageReflectsReceivedNotReady() {
        Order order = Order.builder()
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.RECEIVED)
                .total(new BigDecimal("8000"))
                .build();

        String message = whatsAppService.buildMessageForStatus(order.getStatus(), order.getCustomerName(), order.getOrderCode());

        assertTrue(message.contains("Recibimos tu pedido"));
        assertTrue(message.contains("Pedido recibido"));
        assertTrue(message.contains("en proceso de preparación"));
        assertTrue(message.contains("Te avisaremos por este medio cuando esté listo"));
        
        // Critical requirement: NEVER state "ya está listo para recoger" when in RECEIVED state
        assertFalse(message.contains("ya está listo para recoger"));
        assertFalse(message.contains("cuando vayas a recoger"));
    }

    @Test
    void testPreparingStatusMessage() {
        Order order = Order.builder()
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.PREPARING)
                .total(new BigDecimal("8000"))
                .build();

        String message = whatsAppService.buildMessageForStatus(order.getStatus(), order.getCustomerName(), order.getOrderCode());

        assertTrue(message.contains("se está preparando"));
        assertFalse(message.contains("ya está listo para recoger"));
    }

    @Test
    void testReadyStatusMessageExplicitlyStatesReady() {
        Order order = Order.builder()
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.READY)
                .total(new BigDecimal("8000"))
                .build();

        String message = whatsAppService.buildMessageForStatus(order.getStatus(), order.getCustomerName(), order.getOrderCode());

        assertTrue(message.contains("ya está listo para recoger"));
        assertTrue(message.contains("Listo para entrega"));
    }

    @Test
    void testDeliveredStatusMessage() {
        Order order = Order.builder()
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.DELIVERED)
                .total(new BigDecimal("8000"))
                .build();

        String message = whatsAppService.buildMessageForStatus(order.getStatus(), order.getCustomerName(), order.getOrderCode());

        assertTrue(message.contains("ha sido entregado"));
        assertTrue(message.contains("Muchas gracias por tu compra"));
    }

    @Test
    void testGenerateWhatsAppUrlAppliesCountryPrefix() {
        Order order = Order.builder()
                .orderCode("LD-2026-00023")
                .customerName("Mateo")
                .customerPhone("3001234567")
                .status(OrderStatus.RECEIVED)
                .build();

        String url = whatsAppService.generateWhatsAppUrl(order);

        assertTrue(url.startsWith("https://wa.me/573001234567?text="));
        assertTrue(url.contains("Recibimos+tu+pedido"));
    }
}
