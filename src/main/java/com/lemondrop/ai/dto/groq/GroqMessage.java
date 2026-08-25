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
public class GroqMessage {
    private String role; // "system", "user", "assistant", "tool"
    private String content;
    private String name;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;
    
    @JsonProperty("tool_calls")
    private List<GroqToolCall> toolCalls;
}
