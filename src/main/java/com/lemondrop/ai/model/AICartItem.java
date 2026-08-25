package com.lemondrop.ai.model;

import com.lemondrop.model.ProductSize;
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
public class AICartItem {
    private String id;
    private String productId;
    private String productName;
    private String flavorId;
    private String flavorName;
    private ProductSize size;
    @Builder.Default
    private int quantity = 1;
    @Builder.Default
    private List<AICartItemAddon> addons = new ArrayList<>();
    private BigDecimal unitPrice;
    private BigDecimal addonTotal;
    private BigDecimal subtotal;
    private String observations;
}
