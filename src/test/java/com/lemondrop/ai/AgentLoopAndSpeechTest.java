package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.client.GroqWhisperClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIVoiceResponse;
import com.lemondrop.ai.dto.groq.GroqChatRequest;
import com.lemondrop.ai.dto.groq.GroqChatResponse;
import com.lemondrop.ai.dto.groq.GroqMessage;
import com.lemondrop.ai.dto.groq.GroqToolCall;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.repository.AIConversationRepository;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.GroqSpeechService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.service.SecurityAuditService;
import com.lemondrop.ai.tools.AIToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AgentLoopAndSpeechTest {

    private GroqClient groqClient;
    private GroqWhisperClient whisperClient;
    private GroqProperties groqProperties;
    private LemonAiProperties lemonAiProperties;
    private AIConversationRepository conversationRepository;
    private AIConversationService conversationService;
    private AIToolRegistry toolRegistry;
    private SecurityAuditService auditService;
    private ObjectMapper objectMapper;

    private LemonDropAIService aiService;
    private GroqSpeechService speechService;

    @BeforeEach
    void setUp() {
        groqClient = Mockito.mock(GroqClient.class);
        whisperClient = Mockito.mock(GroqWhisperClient.class);
        conversationRepository = Mockito.mock(AIConversationRepository.class);
        auditService = Mockito.mock(SecurityAuditService.class);

        groqProperties = new GroqProperties();
        groqProperties.getApi().setKey("test-key");
        lemonAiProperties = new LemonAiProperties();
        objectMapper = new ObjectMapper();

        conversationService = new AIConversationService(conversationRepository, lemonAiProperties);
        toolRegistry = new AIToolRegistry(objectMapper);

        when(conversationRepository.findByConversationId(anyString())).thenReturn(Optional.empty());
        when(conversationRepository.save(any(AIConversation.class))).thenAnswer(i -> i.getArgument(0));
        when(auditService.sanitizeInput(anyString())).thenAnswer(i -> i.getArgument(0));

        aiService = new LemonDropAIService(
                groqClient, groqProperties, lemonAiProperties,
                conversationService, toolRegistry, auditService, objectMapper
        );

        speechService = new GroqSpeechService(whisperClient, aiService, lemonAiProperties);
    }

    @Test
    void testAgentLoopNormalFlow() {
        when(groqClient.isAvailable()).thenReturn(true);

        GroqChatResponse groqResponse = GroqChatResponse.builder()
                .id("resp-1")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder().role("assistant").content("¡De una! 🍋 Te preparo un granizado delicioso.").build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any(GroqChatRequest.class))).thenReturn(Optional.of(groqResponse));

        AIChatRequest req = AIChatRequest.builder()
                .message("Hola quiero un granizado")
                .build();

        AIChatResponse response = aiService.processMessage(req);

        assertTrue(response.isSuccess());
        assertEquals("¡De una! 🍋 Te preparo un granizado delicioso.", response.getMessage());
        assertNotNull(response.getConversationId());
        assertNotNull(response.getClientToken());
    }

    @Test
    void testGroqSpeechServiceVoiceProcessing() {
        when(whisperClient.isAvailable()).thenReturn(true);
        when(whisperClient.transcribeAudio(any(), any(), any())).thenReturn(Optional.of("Quiero un granizado de mango grande"));
        when(groqClient.isAvailable()).thenReturn(true);

        GroqChatResponse groqResponse = GroqChatResponse.builder()
                .id("resp-voice")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder().role("assistant").content("¡Perfecto! 🥭 Ya te armé el mango grande.").build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any())).thenReturn(Optional.of(groqResponse));

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio", "voice.webm", "audio/webm", new byte[]{1, 2, 3, 4, 5}
        );

        AIVoiceResponse voiceResponse = speechService.processVoiceInput(audioFile, null, null, "Carlos", "3001234567");

        assertTrue(voiceResponse.isSuccess());
        assertEquals("Quiero un granizado de mango grande", voiceResponse.getTranscription());
        assertNotNull(voiceResponse.getChatResponse());
        assertEquals("¡Perfecto! 🥭 Ya te armé el mango grande.", voiceResponse.getChatResponse().getMessage());
    }

    @Test
    void testAudioFileTooLargeRejected() {
        lemonAiProperties.getRateLimit().setMaxAudioSizeBytes(100L); // 100 bytes max
        MockMultipartFile largeFile = new MockMultipartFile(
                "audio", "large.webm", "audio/webm", new byte[500]
        );

        AIVoiceResponse response = speechService.processVoiceInput(largeFile, null, null, null, null);
        assertFalse(response.isSuccess());
        assertTrue(response.getError().contains("tamaño máximo"));
    }

    @Test
    void testConversationContinuityMultiTurn() {
        when(groqClient.isAvailable()).thenReturn(true);

        java.util.Map<String, AIConversation> memoryStore = new java.util.HashMap<>();
        when(conversationRepository.findByConversationId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(memoryStore.get(inv.getArgument(0))));
        when(conversationRepository.save(any(AIConversation.class)))
                .thenAnswer(inv -> {
                    AIConversation c = inv.getArgument(0);
                    memoryStore.put(c.getConversationId(), c);
                    return c;
                });

        GroqChatResponse r1 = GroqChatResponse.builder()
                .id("resp-1")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder().role("assistant").content("¡De una! 🍋 Aquí tienes las opciones de limón.").build())
                        .build()))
                .build();

        GroqChatResponse r2 = GroqChatResponse.builder()
                .id("resp-2")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder().role("assistant").content("¡Listo! ¿Qué tamaño prefieres: pequeño, mediano o grande?").build())
                        .build()))
                .build();

        when(groqClient.sendChatCompletion(any(GroqChatRequest.class))).thenReturn(Optional.of(r1), Optional.of(r2));

        // Turn 1
        AIChatRequest req1 = AIChatRequest.builder()
                .message("Quiero un Granizado de Limón")
                .build();
        AIChatResponse res1 = aiService.processMessage(req1);

        assertTrue(res1.isSuccess());
        String convId = res1.getConversationId();
        String token = res1.getClientToken();
        assertNotNull(convId);
        assertNotNull(token);

        // Turn 2 reusing same convId and token
        AIChatRequest req2 = AIChatRequest.builder()
                .conversationId(convId)
                .clientToken(token)
                .message("el de limón porfa")
                .build();
        AIChatResponse res2 = aiService.processMessage(req2);

        assertTrue(res2.isSuccess());
        assertEquals(convId, res2.getConversationId());
        AIConversation savedConv = memoryStore.get(convId);
        assertNotNull(savedConv);
        assertEquals(4, savedConv.getMessages().size()); // user1, assistant1, user2, assistant2
        assertEquals("Quiero un Granizado de Limón", savedConv.getMessages().get(0).getContent());
        assertEquals("el de limón porfa", savedConv.getMessages().get(2).getContent());
    }
}
