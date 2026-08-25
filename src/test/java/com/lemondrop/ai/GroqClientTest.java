package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.dto.groq.GroqChatRequest;
import com.lemondrop.ai.dto.groq.GroqChatResponse;
import com.lemondrop.ai.dto.groq.GroqMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class GroqClientTest {

    private RestTemplate restTemplate;
    private GroqProperties groqProperties;
    private ObjectMapper objectMapper;
    private GroqClient groqClient;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        groqProperties = new GroqProperties();
        objectMapper = new ObjectMapper();
    }

    @Test
    void testGroqClientWhenApiKeyNotConfigured() {
        groqProperties.getApi().setKey("");
        groqClient = new GroqClient(restTemplate, groqProperties, objectMapper);

        assertFalse(groqClient.isAvailable());
        Optional<GroqChatResponse> response = groqClient.sendChatCompletion(
                GroqChatRequest.builder().messages(List.of(GroqMessage.builder().role("user").content("hola").build())).build()
        );
        assertTrue(response.isEmpty());
    }

    @Test
    void testGroqClientSuccess() {
        groqProperties.getApi().setKey("gsk_test_key");
        groqClient = new GroqClient(restTemplate, groqProperties, objectMapper);

        assertTrue(groqClient.isAvailable());

        GroqChatResponse mockResponse = GroqChatResponse.builder()
                .id("chatcmpl-123")
                .choices(List.of(GroqChatResponse.GroqChoice.builder()
                        .message(GroqMessage.builder().role("assistant").content("¡Hola! 🍋").build())
                        .build()))
                .build();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        Optional<GroqChatResponse> response = groqClient.sendChatCompletion(
                GroqChatRequest.builder().messages(List.of(GroqMessage.builder().role("user").content("hola").build())).build()
        );

        assertTrue(response.isPresent());
        assertEquals("¡Hola! 🍋", response.get().getChoices().get(0).getMessage().getContent());
    }

    @Test
    void testGroqClientHttpErrorHandling() {
        groqProperties.getApi().setKey("gsk_test_key");
        groqClient = new GroqClient(restTemplate, groqProperties, objectMapper);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(GroqChatResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Invalid API Key"));

        Optional<GroqChatResponse> response = groqClient.sendChatCompletion(
                GroqChatRequest.builder().messages(List.of(GroqMessage.builder().role("user").content("hola").build())).build()
        );

        assertTrue(response.isEmpty());
    }
}
