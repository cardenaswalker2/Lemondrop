package com.lemondrop.ai.tools;

import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.dto.groq.GroqFunction;
import com.lemondrop.ai.dto.groq.GroqTool;
import com.lemondrop.ai.model.AIConversation;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.function.BiFunction;

@Getter
@Builder
public class AIToolDefinition {
    private String name;
    private String description;
    private Map<String, Object> parametersSchema;
    private BiFunction<Map<String, Object>, AIConversation, AIToolResult> executor;

    public GroqTool toGroqTool() {
        return GroqTool.builder()
                .type("function")
                .function(GroqFunction.builder()
                        .name(name)
                        .description(description)
                        .parameters(parametersSchema)
                        .build())
                .build();
    }
}
