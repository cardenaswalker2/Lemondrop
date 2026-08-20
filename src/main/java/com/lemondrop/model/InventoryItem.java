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
@Document(collection = "inventory")
public class InventoryItem {
    @Id
    private String id;
    private String name;
    private String category; // e.g. "Ingredientes", "Insumos"
    private BigDecimal quantity;
    private String unit; // e.g. "Kg", "Unidades", "Litros"
    private BigDecimal minStock;
}
