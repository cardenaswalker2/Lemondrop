package com.lemondrop.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.dto.groq.GroqChatRequest;
import com.lemondrop.ai.dto.groq.GroqChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final RestTemplate restTemplate;
    private final GroqProperties groqProperties;
    private final ObjectMapper objectMapper;

    public GroqClient(RestTemplate groqRestTemplate,
                      GroqProperties groqProperties,
                      ObjectMapper objectMapper) {
        this.restTemplate = groqRestTemplate;
        this.groqProperties = groqProperties;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return groqProperties.isConfigured();
    }

    public Optional<GroqChatResponse> sendChatCompletion(GroqChatRequest request) {
        if (!isAvailable()) {
            log.warn("Groq API key no configurada. Llamada a Groq omitida.");
            return Optional.empty();
        }

        if (request.getModel() == null || request.getModel().trim().isEmpty()) {
            request.setModel(groqProperties.getApi().getModel());
        }

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(groqProperties.getApi().getKey());

                HttpEntity<GroqChatRequest> entity = new HttpEntity<>(request, headers);

                log.info("Enviando petición a Groq Chat Completions (intento {}/{}, modelo: {}, mensajes: {}, tools: {})",
                        attempt, maxAttempts, request.getModel(),
                        request.getMessages() != null ? request.getMessages().size() : 0,
                        request.getTools() != null ? request.getTools().size() : 0);

                ResponseEntity<GroqChatResponse> response = restTemplate.exchange(
                        groqProperties.getApi().getUrl(),
                        HttpMethod.POST,
                        entity,
                        GroqChatResponse.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    log.info("Respuesta de Groq recibida exitosamente (id: {})", response.getBody().getId());
                    return Optional.of(response.getBody());
                } else {
                    log.error("Respuesta no exitosa de Groq: código {}", response.getStatusCode());
                    return Optional.empty();
                }

            } catch (HttpStatusCodeException ex) {
                if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempt < maxAttempts) {
                    long sleepMs = 3000L * attempt;
                    String body = ex.getResponseBodyAsString();
                    if (body != null && body.contains("Please try again in ")) {
                        try {
                            int startIdx = body.indexOf("Please try again in ") + "Please try again in ".length();
                            int endIdx = body.indexOf("s.", startIdx);
                            if (endIdx > startIdx) {
                                double secs = Double.parseDouble(body.substring(startIdx, endIdx).trim());
                                sleepMs = (long) (Math.ceil(secs) * 1000L + 600L);
                            }
                        } catch (Exception ignored) {}
                    }
                    log.warn("Rate limit temporal en Groq (429). Esperando {}ms antes de reintentar... (intento {}/{})", sleepMs, attempt, maxAttempts);
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                    continue;
                }
                if ((ex.getStatusCode() == HttpStatus.BAD_REQUEST || ex.getStatusCode() == HttpStatus.NOT_FOUND) && attempt < maxAttempts) {
                    String body = ex.getResponseBodyAsString();
                    if (body != null && body.contains("model")) {
                        String fallback = request.getModel().contains("8b") ? "llama-3.3-70b-versatile" : "llama-3.1-8b-instant";
                        log.warn("Modelo '{}' no disponible en Groq. Cambiando a fallback '{}'...", request.getModel(), fallback);
                        request.setModel(fallback);
                        continue;
                    }
                }
                log.error("Error HTTP al comunicarse con Groq: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                return Optional.empty();
            } catch (ResourceAccessException ex) {
                if (attempt < maxAttempts) {
                    log.warn("Timeout de conexión en Groq. Reintentando... (intento {}/{})", attempt, maxAttempts);
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                    continue;
                }
                log.error("Timeout o error de conexión al comunicarse con Groq: {}", ex.getMessage());
                return Optional.empty();
            } catch (Exception ex) {
                log.error("Error inesperado en GroqClient: {}", ex.getMessage(), ex);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
