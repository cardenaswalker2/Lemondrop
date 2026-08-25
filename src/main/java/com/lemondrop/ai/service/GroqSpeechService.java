package com.lemondrop.ai.service;

import com.lemondrop.ai.client.GroqWhisperClient;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIVoiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@Service
public class GroqSpeechService {

    private static final Logger log = LoggerFactory.getLogger(GroqSpeechService.class);

    private final GroqWhisperClient whisperClient;
    private final LemonDropAIService aiService;
    private final LemonAiProperties lemonAiProperties;

    public GroqSpeechService(GroqWhisperClient whisperClient,
                             LemonDropAIService aiService,
                             LemonAiProperties lemonAiProperties) {
        this.whisperClient = whisperClient;
        this.aiService = aiService;
        this.lemonAiProperties = lemonAiProperties;
    }

    public AIVoiceResponse processVoiceInput(MultipartFile audioFile, String conversationId, String clientToken, String customerName, String customerPhone) {
        long startTime = System.currentTimeMillis();

        if (audioFile == null || audioFile.isEmpty()) {
            return AIVoiceResponse.builder()
                    .success(false)
                    .error("El archivo de audio está vacío.")
                    .build();
        }

        long maxSize = lemonAiProperties.getRateLimit().getMaxAudioSizeBytes();
        if (audioFile.getSize() > maxSize) {
            return AIVoiceResponse.builder()
                    .success(false)
                    .error("El archivo de audio supera el tamaño máximo permitido (5MB).")
                    .build();
        }

        try {
            byte[] bytes = audioFile.getBytes();
            String originalFilename = audioFile.getOriginalFilename();

            Optional<String> optText = whisperClient.transcribeAudio(bytes, originalFilename, "es");
            long sttDuration = System.currentTimeMillis() - startTime;

            if (optText.isEmpty() || optText.get().trim().isEmpty()) {
                log.warn("No se pudo obtener transcripción válida del audio.");
                AIChatResponse fallbackResponse = AIChatResponse.builder()
                        .conversationId(conversationId)
                        .clientToken(clientToken)
                        .message("No logré escuchar con claridad lo que dijiste 🎙️. ¿Podrías repetirlo o escribirlo?")
                        .success(true)
                        .build();

                return AIVoiceResponse.builder()
                        .transcription("")
                        .chatResponse(fallbackResponse)
                        .sttDurationMs(sttDuration)
                        .success(true)
                        .build();
            }

            String transcription = optText.get().trim();
            log.info("Audio transcrito exitosamente: '{}'", transcription);

            AIChatRequest chatReq = AIChatRequest.builder()
                    .conversationId(conversationId)
                    .clientToken(clientToken)
                    .message(transcription)
                    .customerName(customerName)
                    .customerPhone(customerPhone)
                    .build();

            AIChatResponse chatResponse = aiService.processMessage(chatReq);

            return AIVoiceResponse.builder()
                    .transcription(transcription)
                    .chatResponse(chatResponse)
                    .sttDurationMs(sttDuration)
                    .success(true)
                    .build();

        } catch (Exception ex) {
            log.error("Error al procesar entrada de voz: {}", ex.getMessage(), ex);
            return AIVoiceResponse.builder()
                    .success(false)
                    .error("Error al procesar el audio: " + ex.getMessage())
                    .build();
        }
    }
}
