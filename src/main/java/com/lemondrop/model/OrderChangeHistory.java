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
@Document(collection = "order_change_history")
public class OrderChangeHistory {
    @Id
    private String id;
    private String orderId;
    private String orderCode;
    private String propertyName; // e.g. "flavor", "quantity", "addon"
    private String oldValue;
    private String newValue;
    private String updatedBy; // e.g. username of Advisor/Admin, or "SYSTEM"
    private LocalDateTime updatedAt;
    private String reason;
}
