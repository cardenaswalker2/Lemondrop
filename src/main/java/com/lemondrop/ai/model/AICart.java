package com.lemondrop.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AICart {
    private String cartId;
    @Builder.Default
    private List<AICartItem> items = new ArrayList<>();
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;
    @Builder.Default
    private CartStatus status = CartStatus.DRAFT;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;

    public enum CartStatus {
        DRAFT,
        CONFIRMED,
        EXPIRED,
        CANCELLED
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public void recalculateTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        if (items != null) {
            for (AICartItem item : items) {
                if (item.getSubtotal() != null) {
                    sum = sum.add(item.getSubtotal());
                }
            }
        }
        this.subtotal = sum;
        this.total = sum;
        this.updatedAt = LocalDateTime.now();
    }
}
