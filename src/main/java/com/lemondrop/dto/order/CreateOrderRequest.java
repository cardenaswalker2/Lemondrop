package com.lemondrop.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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
public class CreateOrderRequest {
    @NotBlank(message = "El nombre es obligatorio.")
    private String customerName;
    
    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = "^[0-9]{7,15}$", message = "El teléfono debe contener entre 7 y 15 dígitos numéricos.")
    private String customerPhone;
    
    private String observations;
    
    @NotEmpty(message = "El pedido debe contener al menos un granizado.")
    @Valid
    @Builder.Default
    private List<OrderItemDto> items = new ArrayList<>();
}
