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
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    private String type; // "NUEVO_PEDIDO", "STOCK_BAJO", "PEDIDO_CANCELADO"
    private String title;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
