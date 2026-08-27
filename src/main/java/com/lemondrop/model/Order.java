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
    
    @Builder.Default
    private String priority = "NORMAL"; // "NORMAL", "ALTA"
    private String assignedAdvisor; // username of Advisor
    
    @Builder.Default
    private boolean deleted = false;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private String deletionReason;
    
    @Builder.Default
    private boolean closed = false;
    private LocalDateTime closedAt;
    private String closedBy;
    private String reopenReason;
    
    @Indexed(unique = true, sparse = true)
    private String requestId;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BigDecimal getTotal() {
        if (total != null) return total;
        if (subtotal != null) return subtotal;
        return BigDecimal.ZERO;
    }

    public OrderStatus getStatus() {
        return status != null ? status : OrderStatus.RECEIVED;
    }

    public String getPriority() {
        return priority != null && !priority.trim().isEmpty() ? priority : "NORMAL";
    }

    public String getCustomerName() {
        return customerName != null && !customerName.trim().isEmpty() ? customerName : "Cliente";
    }

    public String getCustomerPhone() {
        return customerPhone != null ? customerPhone : "";
    }

    public String getOrderCode() {
        return orderCode != null && !orderCode.trim().isEmpty() ? orderCode : (id != null ? id : "LD-ORD");
    }
}
