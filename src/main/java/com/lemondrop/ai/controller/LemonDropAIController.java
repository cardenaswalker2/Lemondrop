package com.lemondrop.ai.controller;

import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.dto.AIVoiceResponse;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.GroqSpeechService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
public class LemonDropAIController {

    private static final Logger log = LoggerFactory.getLogger(LemonDropAIController.class);

    private final LemonDropAIService aiService;
    private final GroqSpeechService speechService;
    private final AIConversationService conversationService;
    private final RateLimiterService rateLimiterService;

    public LemonDropAIController(LemonDropAIService aiService,
                                 GroqSpeechService speechService,
                                 AIConversationService conversationService,
                                 RateLimiterService rateLimiterService) {
        this.aiService = aiService;
        this.speechService = speechService;
        this.conversationService = conversationService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@Valid @RequestBody AIChatRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowChatRequest(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("success", false, "error", "Demasiadas peticiones. Por favor espera un momento."));
        }

        try {
            AIChatResponse response = aiService.processMessage(request);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Error al procesar mensaje de chat AI: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Ocurrió un error inesperado al procesar tu solicitud."));
        }
    }

    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> voice(@RequestParam("audio") MultipartFile audio,
                                   @RequestParam(value = "conversationId", required = false) String conversationId,
                                   @RequestParam(value = "clientToken", required = false) String clientToken,
                                   @RequestParam(value = "customerName", required = false) String customerName,
                                   @RequestParam(value = "customerPhone", required = false) String customerPhone,
                                   HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimiterService.allowVoiceRequest(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("success", false, "error", "Límite de audios por minuto alcanzado. Intenta de nuevo en unos segundos."));
        }

        try {
            AIVoiceResponse response = speechService.processVoiceInput(audio, conversationId, clientToken, customerName, customerPhone);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Error al procesar audio en controller: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Error procesando el audio."));
        }
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<?> getConversation(@PathVariable("id") String id,
                                             @RequestParam(value = "clientToken", required = false) String clientToken,
                                             @RequestHeader(value = "X-Client-Token", required = false) String headerToken) {
        String token = clientToken != null ? clientToken : headerToken;
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "error", "Se requiere token de autorización para consultar la conversación."));
        }

        Optional<AIConversation> optConv = conversationService.getConversationSecurely(id, token);
        if (optConv.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Conversación no encontrada o no autorizada."));
        }

        return ResponseEntity.ok(optConv.get());
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody AIChatRequest request, HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(60000L);
        String clientIp = getClientIp(httpRequest);

        if (!rateLimiterService.allowChatRequest(clientIp)) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Límite de peticiones alcanzado."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event().name("status").data("🍋 Preparando tu pedido..."));
                AIChatResponse response = aiService.processMessage(request);
                emitter.send(SseEmitter.event().name("message").data(response));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
