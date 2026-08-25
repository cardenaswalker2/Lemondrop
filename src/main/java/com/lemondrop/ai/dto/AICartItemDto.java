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
public class AICartItemDto {
    private String id;
    private String productId;
    private String productName;
    private String flavorId;
    private String flavorName;
    private String size;
    private int quantity;
    @Builder.Default
    private List<String> addonNames = new ArrayList<>();
    private BigDecimal unitPrice;
    private BigDecimal addonTotal;
    private BigDecimal subtotal;
    private String observations;
}
