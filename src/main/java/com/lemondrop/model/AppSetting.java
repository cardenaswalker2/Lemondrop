package com.lemondrop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "app_settings")
public class AppSetting {
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String key;
    
    private String value;
    private String label;
    private String description;
    private String category; // e.g. "GENERAL", "NOTIFICATIONS", "OPERATIONS"
    
    private String updatedBy;
    private LocalDateTime updatedAt;
}
