package com.lemondrop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "addons")
public class Addon {
    @Id
    private String id;
    private String name;
    private String description;
    private String image;
    private boolean available;
    private BigDecimal additionalPrice;
    
    @Builder.Default
    private PriceType priceType = PriceType.PAID;
    
    private String category; // Dulces, Salsas, Frutas, Toppings, Otros
    private Integer displayOrder;

    public PriceType getEffectivePriceType() {
        if (priceType != null) {
            return priceType;
        }
        if (additionalPrice == null || additionalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return PriceType.FREE;
        }
        return PriceType.PAID;
    }

    public boolean isFree() {
        return getEffectivePriceType() == PriceType.FREE;
    }

    public BigDecimal getEffectivePrice() {
        if (isFree()) {
            return BigDecimal.ZERO;
        }
        return additionalPrice != null ? additionalPrice : BigDecimal.ZERO;
    }
}
