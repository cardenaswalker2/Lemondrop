package com.lemondrop.ai.client;

import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.dto.groq.GroqWhisperResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Component
public class GroqWhisperClient {

    private static final Logger log = LoggerFactory.getLogger(GroqWhisperClient.class);

    private final RestTemplate restTemplate;
    private final GroqProperties groqProperties;

    public GroqWhisperClient(RestTemplate groqRestTemplate, GroqProperties groqProperties) {
        this.restTemplate = groqRestTemplate;
        this.groqProperties = groqProperties;
    }

    public boolean isAvailable() {
        return groqProperties.isConfigured();
    }

    public Optional<String> transcribeAudio(byte[] audioBytes, String filename, String language) {
        if (!isAvailable()) {
            log.warn("Groq API key no configurada. Transcripción de voz omitida.");
            return Optional.empty();
        }

        if (audioBytes == null || audioBytes.length == 0) {
            log.warn("Audio vacío recibido para transcripción.");
            return Optional.empty();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(groqProperties.getApi().getKey());

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return filename != null && !filename.isEmpty() ? filename : "audio.webm";
                }
            };

            body.add("file", fileResource);
            body.add("model", groqProperties.getStt().getModel());
            if (language != null && !language.trim().isEmpty()) {
                body.add("language", language);
            } else {
                body.add("language", "es");
            }
            body.add("response_format", "json");

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            log.info("Enviando audio para transcripción a Groq Whisper (tamaño: {} bytes, modelo: {})",
                    audioBytes.length, groqProperties.getStt().getModel());

            ResponseEntity<GroqWhisperResponse> response = restTemplate.exchange(
                    groqProperties.getStt().getUrl(),
                    HttpMethod.POST,
                    entity,
                    GroqWhisperResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String transcription = response.getBody().getText();
                log.info("Transcripción de Groq Whisper exitosa: '{}'", transcription != null ? transcription.trim() : "");
                return Optional.ofNullable(transcription);
            } else {
                log.error("Error en respuesta de transcripción Groq Whisper: {}", response.getStatusCode());
                return Optional.empty();
            }

        } catch (HttpStatusCodeException ex) {
            log.error("Error HTTP en Groq Whisper: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return Optional.empty();
        } catch (ResourceAccessException ex) {
            log.error("Timeout al transcribir audio en Groq Whisper: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Error inesperado en Groq Whisper: {}", ex.getMessage(), ex);
            return Optional.empty();
        }
    }
}
