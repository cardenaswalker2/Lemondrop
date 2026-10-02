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
    
    // Structured sizes configuration (Size -> availability & price)
    private Map<ProductSize, ProductSizeInfo> sizes;

    // Size to Price mapping (legacy compatibility)
    private Map<ProductSize, BigDecimal> sizePrices;
    
    private boolean available;
    private boolean featured;
    private String badge; // "Más vendido", "Nuevo", "Favorito"
    private boolean active;

    public boolean isSizeAvailable(ProductSize size) {
        if (sizes != null && sizes.containsKey(size)) {
            ProductSizeInfo info = sizes.get(size);
            return info != null && info.isAvailable() && info.getPrice() != null && info.getPrice().compareTo(BigDecimal.ZERO) > 0;
        }
        if (sizePrices != null && sizePrices.containsKey(size)) {
            BigDecimal price = sizePrices.get(size);
            return price != null && price.compareTo(BigDecimal.ZERO) > 0;
        }
        return false;
    }

    public BigDecimal getPriceForSize(ProductSize size) {
        if (sizes != null && sizes.containsKey(size)) {
            ProductSizeInfo info = sizes.get(size);
            if (info != null && info.isAvailable() && info.getPrice() != null && info.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                return info.getPrice();
            }
            return null;
        }
        if (sizePrices != null && sizePrices.containsKey(size)) {
            BigDecimal price = sizePrices.get(size);
            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                return price;
            }
        }
        return null;
    }

    public Map<ProductSize, BigDecimal> getSizePrices() {
        Map<ProductSize, BigDecimal> effectivePrices = new java.util.HashMap<>();
        if (sizes != null) {
            sizes.forEach((sz, info) -> {
                if (info != null && info.isAvailable() && info.getPrice() != null && info.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    effectivePrices.put(sz, info.getPrice());
                }
            });
            if (!effectivePrices.isEmpty()) {
                return effectivePrices;
            }
        }
        if (sizePrices != null) {
            sizePrices.forEach((sz, pr) -> {
                if (pr != null && pr.compareTo(BigDecimal.ZERO) > 0) {
                    effectivePrices.put(sz, pr);
                }
            });
        }
        return effectivePrices;
    }

    public BigDecimal getSmallPrice() {
        return getPriceForSize(ProductSize.SMALL);
    }

    public BigDecimal getMediumPrice() {
        return getPriceForSize(ProductSize.MEDIUM);
    }

    public BigDecimal getLargePrice() {
        return getPriceForSize(ProductSize.LARGE);
    }

    public boolean isHasSmall() {
        return isSizeAvailable(ProductSize.SMALL);
    }

    public boolean isHasMedium() {
        return isSizeAvailable(ProductSize.MEDIUM);
    }

    public boolean isHasLarge() {
        return isSizeAvailable(ProductSize.LARGE);
    }

    public BigDecimal getMinAvailablePrice() {
        BigDecimal min = null;
        for (ProductSize sz : ProductSize.values()) {
            BigDecimal p = getPriceForSize(sz);
            if (p != null && p.compareTo(BigDecimal.ZERO) > 0) {
                if (min == null || p.compareTo(min) < 0) {
                    min = p;
                }
            }
        }
        return min != null ? min : BigDecimal.ZERO;
    }
}
