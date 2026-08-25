package com.lemondrop.ai;

import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.repository.AIAuditLogRepository;
import com.lemondrop.ai.repository.AIConversationRepository;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.RateLimiterService;
import com.lemondrop.ai.service.SecurityAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SecurityAndRateLimitTest {

    private RateLimiterService rateLimiterService;
    private SecurityAuditService auditService;
    private AIConversationRepository conversationRepository;
    private AIAuditLogRepository auditLogRepository;
    private AIConversationService conversationService;
    private LemonAiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LemonAiProperties();
        properties.getRateLimit().setMessagesPerMinute(3);
        properties.getRateLimit().setAudiosPerMinute(2);

        rateLimiterService = new RateLimiterService(properties);
        auditLogRepository = Mockito.mock(AIAuditLogRepository.class);
        auditService = new SecurityAuditService(auditLogRepository);

        conversationRepository = Mockito.mock(AIConversationRepository.class);
        conversationService = new AIConversationService(conversationRepository, properties);
    }

    @Test
    void testChatRateLimiting() {
        String clientIp = "192.168.1.100";

        assertTrue(rateLimiterService.allowChatRequest(clientIp)); // 1
        assertTrue(rateLimiterService.allowChatRequest(clientIp)); // 2
        assertTrue(rateLimiterService.allowChatRequest(clientIp)); // 3
        assertFalse(rateLimiterService.allowChatRequest(clientIp)); // 4 (blocked)
    }

    @Test
    void testSanitizeInput() {
        String tainted = "Quiero mango \u0000\u0007 con gomitas";
        String clean = auditService.sanitizeInput(tainted);
        assertEquals("Quiero mango  con gomitas", clean);
    }

    @Test
    void testSecureConversationAccessWithToken() {
        AIConversation conv = AIConversation.builder()
                .conversationId("conv-123")
                .clientToken("token-secret-xyz")
                .build();

        when(conversationRepository.findByConversationIdAndClientToken("conv-123", "token-secret-xyz"))
                .thenReturn(Optional.of(conv));

        // Correct token
        Optional<AIConversation> accessOk = conversationService.getConversationSecurely("conv-123", "token-secret-xyz");
        assertTrue(accessOk.isPresent());

        // Missing or wrong token
        Optional<AIConversation> accessDenied = conversationService.getConversationSecurely("conv-123", "wrong-token");
        assertTrue(accessDenied.isEmpty());
    }
}
