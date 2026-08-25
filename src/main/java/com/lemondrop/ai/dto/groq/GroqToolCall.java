package com.lemondrop.ai.dto.groq;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroqToolCall {
    private String id;
    @Builder.Default
    private String type = "function";
    private GroqFunctionCall function;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroqFunctionCall {
        private String name;
        private String arguments; // JSON string of arguments
    }
}
