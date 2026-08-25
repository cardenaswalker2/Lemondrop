package com.lemondrop.ai.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.dto.groq.GroqTool;
import com.lemondrop.ai.model.AIConversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class AIToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(AIToolRegistry.class);

    private final Map<String, AIToolDefinition> tools = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public AIToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(AIToolDefinition tool) {
        tools.put(tool.getName(), tool);
        log.info("AI Tool registrada: {}", tool.getName());
    }

    public List<GroqTool> getGroqTools() {
        return tools.values().stream()
                .map(AIToolDefinition::toGroqTool)
                .collect(Collectors.toList());
    }

    public Optional<AIToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public AIToolResult execute(String toolName, String argumentsJson, AIConversation conversation) {
        AIToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            log.warn("Herramienta no encontrada en el registro: {}", toolName);
            return AIToolResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .message("Herramienta '" + toolName + "' no reconocida en el sistema.")
                    .build();
        }

        try {
            Map<String, Object> arguments = new HashMap<>();
            if (argumentsJson != null && !argumentsJson.trim().isEmpty() && !argumentsJson.trim().equals("{}")) {
                arguments = objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            }

            log.info("Ejecutando tool '{}' con argumentos: {}", toolName, arguments);
            return tool.getExecutor().apply(arguments, conversation);
        } catch (Exception ex) {
            log.error("Error al ejecutar tool '{}': {}", toolName, ex.getMessage(), ex);
            return AIToolResult.builder()
                    .toolName(toolName)
                    .success(false)
                    .message("Error al ejecutar herramienta " + toolName + ": " + ex.getMessage())
                    .build();
        }
    }
}
