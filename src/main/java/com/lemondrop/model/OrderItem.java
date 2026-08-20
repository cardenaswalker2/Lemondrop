package com.lemondrop.model;

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
public class OrderItem {
    private String productId;
    private String productName;
    private String flavorId;
    private String flavorName;
    private ProductSize size;
    private Integer quantity;
    private BigDecimal unitPrice;
    
    @Builder.Default
    private List<OrderItemAddon> addons = new ArrayList<>();
    
    private BigDecimal addonTotal;
    private BigDecimal subtotal;
    private String observations;
}
