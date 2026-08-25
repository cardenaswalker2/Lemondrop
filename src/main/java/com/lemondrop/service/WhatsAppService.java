package com.lemondrop.service;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WhatsAppService {

    private final String countryCode;

    public WhatsAppService(@Value("${app.whatsapp.country-code:57}") String countryCode) {
        this.countryCode = countryCode;
    }

    public String generateWhatsAppUrl(Order order) {
        if (order == null) return "";

        String rawPhone = order.getCustomerPhone() != null ? order.getCustomerPhone() : "";
        String cleanPhone = rawPhone.replaceAll("[^0-9]", "");

        if (cleanPhone.isEmpty()) {
            cleanPhone = "3000000000";
        }

        // Ensure cleanPhone has country code prefix
        String fullPhone = cleanPhone;
        if (!cleanPhone.startsWith(countryCode) && cleanPhone.length() == 10) {
            fullPhone = countryCode + cleanPhone;
        }

        String customerName = (order.getCustomerName() != null && !order.getCustomerName().trim().isEmpty())
                ? order.getCustomerName().trim()
                : "Cliente";

        String message = buildMessageForStatus(order.getStatus(), customerName, order.getOrderCode());

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return "https://wa.me/" + fullPhone + "?text=" + encodedMessage;
    }

    public String buildMessageForStatus(OrderStatus status, String customerName, String orderCode) {
        if (status == null) {
            status = OrderStatus.RECEIVED;
        }

        return switch (status) {
            case RECEIVED -> String.format(
                    "¡Hola %s! 👋🍋\n\n" +
                    "Recibimos tu pedido de LEMON DROP.\n\n" +
                    "📦 Pedido: %s\n" +
                    "🟢 Estado: Pedido recibido\n\n" +
                    "Tu pedido ya fue registrado correctamente y se encuentra en proceso de preparación.\n\n" +
                    "Te avisaremos por este medio cuando esté listo para recoger. 📲\n\n" +
                    "Si tienes alguna duda, puedes escribirnos por aquí.\n\n" +
                    "¡Gracias por elegir LEMON DROP! 🍋💛",
                    customerName, orderCode
            );
            case ACCEPTED, PREPARING, ALMOST_READY -> String.format(
                    "¡Hola %s! 👋🍧\n\n" +
                    "¡Tu pedido de LEMON DROP ya se está preparando en la cocina!\n\n" +
                    "📦 Pedido: %s\n" +
                    "🟡 Estado: %s\n\n" +
                    "Te avisaremos apenas esté listo para entregar. 🍋✨",
                    customerName, orderCode, status.getDisplayName()
            );
            case READY -> String.format(
                    "¡Hola %s! 🎉🍧\n\n" +
                    "¡Tu pedido de LEMON DROP ya está listo para recoger!\n\n" +
                    "📦 Pedido: %s\n" +
                    "🟢 Estado: Listo para entrega\n\n" +
                    "Acércate a nuestro stand y muestra tu código para retirarlo al instante. 🍋💛",
                    customerName, orderCode
            );
            case DELIVERED -> String.format(
                    "¡Hola %s! 🍋💛\n\n" +
                    "¡Tu pedido %s ha sido entregado!\n\n" +
                    "Esperamos que disfrutes tu granizado Lemon Drop. ¡Muchas gracias por tu compra! ✨🍧",
                    customerName, orderCode
            );
            case CANCELLED -> String.format(
                    "Hola %s. 🍋\n\n" +
                    "Tu pedido %s de Lemon Drop ha sido cancelado.\n\n" +
                    "Si tienes dudas o deseas hacer un nuevo pedido, no dudes en escribirnos por aquí. ✨",
                    customerName, orderCode
            );
        };
    }
}
