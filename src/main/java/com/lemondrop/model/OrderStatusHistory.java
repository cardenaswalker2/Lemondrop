package com.lemondrop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "order_status_history")
public class OrderStatusHistory {
    @Id
    private String id;
    private String orderId;
    private String orderCode;
    private OrderStatus previousStatus;
    private OrderStatus newStatus;
    private String updatedBy; // "ADMIN", "ASESOR", "SYSTEM", "GUEST"
    private LocalDateTime updatedAt;
    private String notes;
}
