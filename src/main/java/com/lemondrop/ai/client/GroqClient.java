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

        try {
            if (request.getModel() == null || request.getModel().trim().isEmpty()) {
                request.setModel(groqProperties.getApi().getModel());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqProperties.getApi().getKey());

            HttpEntity<GroqChatRequest> entity = new HttpEntity<>(request, headers);

            log.info("Enviando petición a Groq Chat Completions (modelo: {}, mensajes: {}, tools: {})",
                    request.getModel(),
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
            log.error("Error HTTP al comunicarse con Groq: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (ResourceAccessException ex) {
            log.error("Timeout o error de conexión al comunicarse con Groq: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Error inesperado en GroqClient: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }
}
