package com.lemondrop.ai.tools.impl;

import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.model.Flavor;
import com.lemondrop.model.Product;
import com.lemondrop.model.ProductSize;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.ProductService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RecommendationTools {

    private final AIToolRegistry registry;
    private final ProductService productService;
    private final FlavorService flavorService;

    private final String businessName;
    private final String businessSchedule;
    private final String businessWhatsapp;
    private final String businessAddress;

    public RecommendationTools(AIToolRegistry registry,
                               ProductService productService,
                               FlavorService flavorService,
                               @Value("${app.business.name:Lemon Drop}") String businessName,
                               @Value("${app.business.schedule:Lunes a Domingo: 10:00 AM - 9:00 PM}") String businessSchedule,
                               @Value("${app.business.whatsapp:+573001234567}") String businessWhatsapp,
                               @Value("${app.business.address:Calle 10 # 40-20, Medellín, Colombia}") String businessAddress) {
        this.registry = registry;
        this.productService = productService;
        this.flavorService = flavorService;
        this.businessName = businessName;
        this.businessSchedule = businessSchedule;
        this.businessWhatsapp = businessWhatsapp;
        this.businessAddress = businessAddress;
    }

    @PostConstruct
    public void registerTools() {
        registerRecomendarProducto();
        registerConsultarHorarios();
        registerConsultarPromociones();
        registerObtenerConfiguracionNegocio();
    }

    private void registerRecomendarProducto() {
        Map<String, Object> props = new HashMap<>();
        props.put("preference", Map.of("description", "Preferencia del cliente (ej. 'dulce', 'ácido', 'cítrico', 'refrescante', 'popular', 'económico')"));
        props.put("maxBudget", Map.of("description", "Presupuesto numérico opcional en pesos"));
        props.put("size", Map.of("description", "Tamaño deseado opcional: SMALL, MEDIUM, LARGE"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("recomendar_producto")
                .description("Recomienda productos y combinaciones de sabores del catálogo real según presupuesto, preferencias y popularidad.")
                .parametersSchema(schema)
                .executor(this::recomendarProducto)
                .build());
    }

    private AIToolResult recomendarProducto(Map<String, Object> args, AIConversation conv) {
        String pref = (String) args.getOrDefault("preference", "");
        String cleanPref = pref != null ? pref.toLowerCase() : "";

        BigDecimal maxBudget = null;
        if (args.get("maxBudget") instanceof Number num) {
            maxBudget = BigDecimal.valueOf(num.doubleValue());
        }

        List<Product> products = productService.getAllActiveAndAvailable();
        List<Flavor> flavors = flavorService.getAvailableFlavors();

        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (Product p : products) {
            BigDecimal minPrice = p.getSmallPrice() != null && p.getSmallPrice().compareTo(BigDecimal.ZERO) > 0 ?
                    p.getSmallPrice() : p.getMediumPrice();

            if (maxBudget != null && minPrice != null && minPrice.compareTo(maxBudget) > 0) {
                continue; // Exceeds budget
            }

            Map<String, Object> item = new HashMap<>();
            item.put("productName", p.getName());
            item.put("description", p.getDescription());
            item.put("badge", p.getBadge());
            item.put("isFeatured", p.isFeatured());
            item.put("startingPrice", minPrice);

            // Pair with suggested flavors based on taste
            List<String> suggestedFlavors = flavors.stream()
                    .filter(f -> {
                        if (cleanPref.contains("ácido") || cleanPref.contains("citrico") || cleanPref.contains("refrescante")) {
                            return f.getName().equalsIgnoreCase("Limón") || f.getName().equalsIgnoreCase("Maracuyá");
                        }
                        if (cleanPref.contains("dulce") || cleanPref.contains("frutos")) {
                            return f.getName().equalsIgnoreCase("Fresa") || f.getName().equalsIgnoreCase("Cereza");
                        }
                        return true;
                    })
                    .map(Flavor::getName)
                    .collect(Collectors.toList());

            item.put("recommendedFlavors", suggestedFlavors);
            recommendations.add(item);
        }

        return AIToolResult.builder()
                .toolName("recomendar_producto")
                .success(true)
                .data(recommendations)
                .message("Recomendaciones generadas basadas en catálogo en tiempo real.")
                .build();
    }

    private void registerConsultarHorarios() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("consultar_horarios")
                .description("Consulta los horarios de atención y apertura oficiales de Lemon Drop.")
                .parametersSchema(schema)
                .executor(this::consultarHorarios)
                .build());
    }

    private AIToolResult consultarHorarios(Map<String, Object> args, AIConversation conv) {
        return AIToolResult.builder()
                .toolName("consultar_horarios")
                .success(true)
                .data(Map.of(
                        "businessName", businessName,
                        "schedule", businessSchedule,
                        "address", businessAddress
                ))
                .message("Horario oficial de Lemon Drop: " + businessSchedule)
                .build();
    }

    private void registerConsultarPromociones() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("consultar_promociones")
                .description("Consulta las promociones y ofertas especiales activas en Lemon Drop.")
                .parametersSchema(schema)
                .executor(this::consultarPromociones)
                .build());
    }

    private AIToolResult consultarPromociones(Map<String, Object> args, AIConversation conv) {
        // Honest response without inventing promos
        List<Product> featured = productService.getAllActiveAndAvailable().stream()
                .filter(Product::isFeatured)
                .collect(Collectors.toList());

        List<Map<String, Object>> promos = featured.stream().map(p -> Map.<String, Object>of(
                "title", "Producto Destacado: " + p.getName(),
                "description", p.getDescription() != null ? p.getDescription() : "",
                "badge", p.getBadge() != null ? p.getBadge() : "Favorito",
                "priceMedium", p.getMediumPrice()
        )).collect(Collectors.toList());

        return AIToolResult.builder()
                .toolName("consultar_promociones")
                .success(true)
                .data(promos)
                .message(promos.isEmpty() ? "No hay promociones de descuento activas en este momento, pero tenemos nuestros productos destacados." : "Promociones y destacados consultados.")
                .build();
    }

    private void registerObtenerConfiguracionNegocio() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("obtener_configuracion_negocio")
                .description("Obtiene información general oficial del negocio (nombre, dirección, WhatsApp de contacto, horarios).")
                .parametersSchema(schema)
                .executor(this::obtenerConfiguracionNegocio)
                .build());
    }

    private AIToolResult obtenerConfiguracionNegocio(Map<String, Object> args, AIConversation conv) {
        return AIToolResult.builder()
                .toolName("obtener_configuracion_negocio")
                .success(true)
                .data(Map.of(
                        "name", businessName,
                        "schedule", businessSchedule,
                        "whatsapp", businessWhatsapp,
                        "address", businessAddress
                ))
                .message("Configuración oficial de Lemon Drop obtenida.")
                .build();
    }
}
