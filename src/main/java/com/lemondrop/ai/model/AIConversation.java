package com.lemondrop.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "ai_conversations")
public class AIConversation {
    @Id
    private String id;

    @Indexed(unique = true)
    private String conversationId;

    private String sessionId;
    
    // Token generated on conversation creation for client verification
    private String clientToken;

    private String customerId;
    private String customerName;
    private String customerPhone;

    @Builder.Default
    private ConversationState state = ConversationState.IDLE;

    @Builder.Default
    private AICart cart = new AICart();

    private String orderDraftId;
    private String confirmedOrderCode;
    private String observations;
    @Builder.Default
    private List<String> pendingCustomerFields = new ArrayList<>();

    @Builder.Default
    private List<AIMessage> messages = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void addMessage(AIMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
        this.updatedAt = LocalDateTime.now();
    }
}
