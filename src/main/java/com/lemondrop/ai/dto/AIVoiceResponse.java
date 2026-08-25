package com.lemondrop.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIVoiceResponse {
    private String transcription;
    private AIChatResponse chatResponse;
    private long sttDurationMs;
    private boolean success;
    private String error;
}
