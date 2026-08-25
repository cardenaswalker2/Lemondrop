package com.lemondrop.ai.dto.groq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroqChatRequest {
    private String model;
    private List<GroqMessage> messages;
    private List<GroqTool> tools;
    
    @JsonProperty("tool_choice")
    private Object toolChoice; // "auto" or null
    
    private Double temperature;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
