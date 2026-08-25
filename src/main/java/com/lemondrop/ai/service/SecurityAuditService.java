package com.lemondrop.ai.service;

import com.lemondrop.ai.model.AIAuditLog;
import com.lemondrop.ai.repository.AIAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final AIAuditLogRepository auditLogRepository;

    public SecurityAuditService(AIAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logToolExecution(String conversationId, String clientToken, String toolName, String arguments, String resultSummary, String status, long durationMs) {
        try {
            // Sanitize sensitive values before storing
            String safeArgs = arguments != null ? arguments.replaceAll("(?i)password|secret|key|token", "***") : "";
            if (safeArgs.length() > 1000) safeArgs = safeArgs.substring(0, 1000) + "...";

            String safeSummary = resultSummary != null ? resultSummary : "";
            if (safeSummary.length() > 1000) safeSummary = safeSummary.substring(0, 1000) + "...";

            AIAuditLog entry = AIAuditLog.builder()
                    .conversationId(conversationId)
                    .clientToken(clientToken)
                    .toolName(toolName)
                    .arguments(safeArgs)
                    .resultSummary(safeSummary)
                    .status(status)
                    .executionTimeMs(durationMs)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditLogRepository.save(entry);
            log.info("AI Audit: tool={}, status={}, duration={}ms, conv={}", toolName, status, durationMs, conversationId);
        } catch (Exception ex) {
            log.error("No se pudo guardar log de auditoría IA: {}", ex.getMessage());
        }
    }

    public String sanitizeInput(String input) {
        if (input == null) return "";
        // Trim and remove null bytes or control characters
        return input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim();
    }
}
