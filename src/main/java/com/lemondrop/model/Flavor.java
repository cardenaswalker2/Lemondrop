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
@Document(collection = "flavors")
public class Flavor {
    @Id
    private String id;
    private String name;
    private String description;
    private String image;
    private boolean available;
    private BigDecimal additionalPrice;
}
