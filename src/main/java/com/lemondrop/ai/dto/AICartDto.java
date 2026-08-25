package com.lemondrop.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AICartDto {
    private String cartId;
    @Builder.Default
    private List<AICartItemDto> items = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal total;
    private String status;
    private String observations;
    private int totalItems;
}
