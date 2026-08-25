package com.lemondrop.ai.model;

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
@Document(collection = "ai_audit_logs")
public class AIAuditLog {
    @Id
    private String id;
    private String conversationId;
    private String clientToken;
    private String toolName;
    private String arguments;
    private String resultSummary;
    private String status; // "SUCCESS", "FAILED", "BLOCKED"
    private long executionTimeMs;
    private LocalDateTime timestamp;
}
