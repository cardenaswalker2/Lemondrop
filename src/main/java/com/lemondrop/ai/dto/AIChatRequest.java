package com.lemondrop.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIChatRequest {
    private String conversationId;
    private String clientToken;
    
    @Size(max = 2000, message = "El mensaje no puede exceder los 2000 caracteres.")
    private String message;
    
    private String customerName;
    private String customerPhone;
    
    // Optional action flag (e.g. direct user click on "CONFIRMAR PEDIDO")
    private String action; // e.g. "CONFIRM_ORDER", "CLEAR_CART"
}
