package com.lemondrop.service;

import com.lemondrop.model.Order;
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
        String cleanPhone = order.getCustomerPhone().replaceAll("[^0-9]", "");
        
        // Ensure cleanPhone has country code prefix
        String fullPhone = cleanPhone;
        if (!cleanPhone.startsWith(countryCode) && cleanPhone.length() == 10) {
            fullPhone = countryCode + cleanPhone;
        }

        String message = String.format(
                "Hola %s 👋🍧\n" +
                "Tu pedido de LEMON DROP ya está listo para recoger.\n" +
                "Pedido: %s\n" +
                "¡Gracias por elegir LEMON DROP! 🍋💛",
                order.getCustomerName(),
                order.getOrderCode()
        );

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return "https://wa.me/" + fullPhone + "?text=" + encodedMessage;
    }
}
