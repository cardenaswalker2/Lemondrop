package com.lemondrop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private String description;
    private String image;
    private String category;
    
    // Size to Price mapping
    private Map<ProductSize, BigDecimal> sizePrices;
    
    private boolean available;
    private boolean featured;
    private String badge; // "Más vendido", "Nuevo", "Favorito"
    private boolean active;

    public BigDecimal getSmallPrice() {
        return sizePrices != null ? sizePrices.get(ProductSize.SMALL) : BigDecimal.ZERO;
    }

    public BigDecimal getMediumPrice() {
        return sizePrices != null ? sizePrices.get(ProductSize.MEDIUM) : BigDecimal.ZERO;
    }

    public BigDecimal getLargePrice() {
        return sizePrices != null ? sizePrices.get(ProductSize.LARGE) : BigDecimal.ZERO;
    }
}
