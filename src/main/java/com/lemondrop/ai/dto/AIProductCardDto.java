package com.lemondrop.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIProductCardDto {
    private String id;
    private String name;
    private String description;
    private String image;
    private String category;
    private String badge;
    private BigDecimal priceFrom;
    private Map<String, BigDecimal> prices;
    private boolean available;
}
