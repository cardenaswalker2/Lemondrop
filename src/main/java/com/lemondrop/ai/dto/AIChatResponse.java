package com.lemondrop.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIChatResponse {
    private String conversationId;
    private String clientToken;
    private String message;
    private String state;
    private String intent;
    private String customerName;
    private String customerPhone;
    private List<String> pendingCustomerFields;
    
    @Builder.Default
    private boolean cartUpdated = false;
    
    @Builder.Default
    private boolean orderReadyForConfirmation = false;
    
    @Builder.Default
    private boolean requiresConfirmation = false;
    
    @Builder.Default
    private boolean orderConfirmed = false;
    
    private AICartDto cart;
    private String orderCode;
    private String whatsAppUrl;
    
    private List<AIProductCardDto> products;
    private List<String> suggestions;
    private long executionTimeMs;
    private boolean success;
    private String error;
}
