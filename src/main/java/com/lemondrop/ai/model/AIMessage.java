package com.lemondrop.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIMessage {
    private String role; // "system", "user", "assistant", "tool"
    private String content;
    private String toolCallId;
    private String toolName;
    private List<AIToolCall> toolCalls;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AIToolCall {
        private String id;
        private String type; // "function"
        private AIFunctionCall function;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AIFunctionCall {
        private String name;
        private String arguments;
    }
}
