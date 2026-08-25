package com.lemondrop.ai.dto.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroqChatResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private List<GroqChoice> choices;
    private GroqUsage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroqChoice {
        private int index;
        private GroqMessage message;
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GroqUsage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
