package com.lemondrop.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GroqConfig {

    @Bean
    @ConfigurationProperties(prefix = "groq")
    public GroqProperties groqProperties() {
        return new GroqProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "lemon.ai")
    public LemonAiProperties lemonAiProperties() {
        return new LemonAiProperties();
    }

    @Bean
    public RestTemplate groqRestTemplate(GroqProperties groqProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(groqProperties.getTimeout().getConnect());
        factory.setReadTimeout(groqProperties.getTimeout().getRead());
        return new RestTemplate(factory);
    }

    @Data
    public static class GroqProperties {
        private Api api = new Api();
        private Stt stt = new Stt();
        private Timeout timeout = new Timeout();

        public boolean isConfigured() {
            return api.getKey() != null && !api.getKey().trim().isEmpty();
        }

        @Data
        public static class Api {
            private String key;
            private String url = "https://api.groq.com/openai/v1/chat/completions";
            private String model = "openai/gpt-oss-120b";
        }

        @Data
        public static class Stt {
            private String url = "https://api.groq.com/openai/v1/audio/transcriptions";
            private String model = "whisper-large-v3-turbo";
        }

        @Data
        public static class Timeout {
            private int connect = 10000;
            private int read = 60000;
        }
    }

    @Data
    public static class LemonAiProperties {
        private boolean debugLogging = false;
        private int maxToolIterations = 8;
        private int maxMessageLength = 2000;
        private int cartExpirationMinutes = 60;
        private RateLimit rateLimit = new RateLimit();

        @Data
        public static class RateLimit {
            private int messagesPerMinute = 30;
            private int audiosPerMinute = 10;
            private long maxAudioSizeBytes = 5242880L; // 5MB
        }
    }
}
