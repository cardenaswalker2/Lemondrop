package com.lemondrop.dto.order;

import com.lemondrop.model.ProductSize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDto {
    @NotNull(message = "El producto es obligatorio.")
    private String productId;
    
    @NotNull(message = "El sabor es obligatorio.")
    private String flavorId;
    
    @NotNull(message = "El tamaño es obligatorio.")
    private ProductSize size;
    
    @NotNull(message = "La cantidad es obligatoria.")
    @Min(value = 1, message = "La cantidad mínima es 1.")
    private Integer quantity;
    
    @Builder.Default
    private List<String> addonIds = new ArrayList<>();
    
    private String observations;
}
