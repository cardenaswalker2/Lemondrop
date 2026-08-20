package com.lemondrop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String orderCode;
    
    private String customerName;
    private String customerPhone;
    
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
    
    private BigDecimal subtotal;
    private BigDecimal total;
    private OrderStatus status;
    private String observations;
    private String advisorNotes;
    
    private String createdBy; // "GUEST", username
    private String lastModifiedBy; // username or "SYSTEM"
    
    // Status tracking timestamps
    private LocalDateTime receivedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime almostReadyAt;
    private LocalDateTime readyAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime cancelledAt;
    
    private String cancellationReason;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
