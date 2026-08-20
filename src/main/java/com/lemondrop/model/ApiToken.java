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
@Document(collection = "api_tokens")
public class ApiToken {
    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    private String username;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
