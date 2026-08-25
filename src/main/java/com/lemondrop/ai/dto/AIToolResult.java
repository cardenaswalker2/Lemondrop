package com.lemondrop.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIToolResult {
    private String toolName;
    private boolean success;
    private Object data;
    private String message;
    private boolean cartModified;
    private boolean requiresConfirmation;
    private boolean orderCreated;
    private Map<String, Object> extraData;
}
