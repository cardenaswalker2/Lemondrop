package com.lemondrop.ai.service;

import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private final LemonAiProperties lemonAiProperties;

    private final Map<String, UserRateBucket> chatLimits = new ConcurrentHashMap<>();
    private final Map<String, UserRateBucket> voiceLimits = new ConcurrentHashMap<>();

    public RateLimiterService(LemonAiProperties lemonAiProperties) {
        this.lemonAiProperties = lemonAiProperties;
    }

    public boolean allowChatRequest(String clientIdentifier) {
        if (clientIdentifier == null || clientIdentifier.isEmpty()) return true;
        int maxAllowed = lemonAiProperties.getRateLimit().getMessagesPerMinute();
        return checkRate(chatLimits, clientIdentifier, maxAllowed);
    }

    public boolean allowVoiceRequest(String clientIdentifier) {
        if (clientIdentifier == null || clientIdentifier.isEmpty()) return true;
        int maxAllowed = lemonAiProperties.getRateLimit().getAudiosPerMinute();
        return checkRate(voiceLimits, clientIdentifier, maxAllowed);
    }

    private boolean checkRate(Map<String, UserRateBucket> map, String key, int maxLimit) {
        long currentMinute = System.currentTimeMillis() / 60000;
        UserRateBucket bucket = map.compute(key, (k, v) -> {
            if (v == null || v.minuteWindow != currentMinute) {
                return new UserRateBucket(currentMinute, new AtomicInteger(1));
            }
            v.counter.incrementAndGet();
            return v;
        });
        return bucket.counter.get() <= maxLimit;
    }

    private static class UserRateBucket {
        final long minuteWindow;
        final AtomicInteger counter;

        UserRateBucket(long minuteWindow, AtomicInteger counter) {
            this.minuteWindow = minuteWindow;
            this.counter = counter;
        }
    }
}
