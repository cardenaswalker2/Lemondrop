package com.lemondrop.ai.tools.impl;

import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class GeneralTools {

    private final AIToolRegistry registry;

    public GeneralTools(AIToolRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void registerTools() {
        registerObtenerFechaHoraActual();
        registerActualizarNotaPedido();
    }

    private void registerObtenerFechaHoraActual() {
        Map<String, Object> props = new HashMap<>();
        props.put("timezone", Map.of(
                "type", "string",
                "description", "Zona horaria opcional (por defecto America/Bogota)"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("obtener_fecha_hora_actual")
                .description("Obtiene la fecha, hora actual, día de la semana y zona horaria del negocio para responder consultas temporales con exactitud.")
                .parametersSchema(schema)
                .executor((args, conv) -> {
                    String tzStr = args != null && args.get("timezone") instanceof String s ? s : "America/Bogota";
                    ZoneId zoneId;
                    try {
                        zoneId = ZoneId.of(tzStr);
                    } catch (Exception e) {
                        zoneId = ZoneId.of("America/Bogota");
                    }

                    ZonedDateTime now = ZonedDateTime.now(zoneId);
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "CO"));
                    DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

                    String formattedDate = now.format(dateFormatter);
                    String formattedTime = now.format(timeFormatter);

                    Map<String, Object> data = new HashMap<>();
                    data.put("date", now.toLocalDate().toString());
                    data.put("time", now.toLocalTime().toString());
                    data.put("weekday", now.getDayOfWeek().name().toLowerCase());
                    data.put("timezone", zoneId.getId());
                    data.put("formattedDate", formattedDate);
                    data.put("formattedTime", formattedTime);
                    data.put("fullDescription", "Hoy es " + formattedDate + " y son las " + formattedTime + " (hora de Colombia). 📅⏰");

                    return AIToolResult.builder()
                            .toolName("obtener_fecha_hora_actual")
                            .success(true)
                            .data(data)
                            .message("Hoy es " + formattedDate + ", " + formattedTime)
                            .build();
                })
                .build());
    }

    private void registerActualizarNotaPedido() {
        Map<String, Object> props = new HashMap<>();
        props.put("observations", Map.of(
                "type", "string",
                "description", "Observaciones, notas o instrucciones especiales para la preparación o entrega del pedido (ej. 'sin mucho hielo', 'bien frío', 'recoger a las 5pm', etc.)"
        ));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", java.util.List.of("observations"));

        registry.register(AIToolDefinition.builder()
                .name("actualizar_nota_pedido")
                .description("Guarda o actualiza las observaciones o notas de preparación del pedido del cliente para que aparezcan en la comanda del asesor y cocina.")
                .parametersSchema(schema)
                .executor((args, conv) -> {
                    String obs = null;
                    if (args != null) {
                        if (args.get("observations") instanceof String s && !s.trim().isEmpty()) {
                            obs = s.trim();
                        } else if (args.get("nota") instanceof String n && !n.trim().isEmpty()) {
                            obs = n.trim();
                        } else if (args.get("observaciones") instanceof String o && !o.trim().isEmpty()) {
                            obs = o.trim();
                        }
                    }

                    if (obs == null || obs.isEmpty()) {
                        return AIToolResult.builder()
                                .toolName("actualizar_nota_pedido")
                                .success(false)
                                .message("Por favor indica la nota u observación que deseas agregar al pedido.")
                                .build();
                    }

                    conv.setObservations(obs);
                    if (conv.getCart() != null) {
                        conv.getCart().setObservations(obs);
                    }

                    Map<String, Object> data = new HashMap<>();
                    data.put("observations", obs);

                    return AIToolResult.builder()
                            .toolName("actualizar_nota_pedido")
                            .success(true)
                            .cartModified(true)
                            .data(data)
                            .message("Nota guardada: \"" + obs + "\" 📝")
                            .build();
                })
                .build());
    }
}
