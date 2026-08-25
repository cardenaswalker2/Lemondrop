package com.lemondrop.ai.service;

import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.model.AICart;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.model.AIMessage;
import com.lemondrop.ai.model.ConversationState;
import com.lemondrop.ai.repository.AIConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AIConversationService {

    private static final Logger log = LoggerFactory.getLogger(AIConversationService.class);

    private final AIConversationRepository conversationRepository;
    private final LemonAiProperties lemonAiProperties;

    public AIConversationService(AIConversationRepository conversationRepository,
                                 LemonAiProperties lemonAiProperties) {
        this.conversationRepository = conversationRepository;
        this.lemonAiProperties = lemonAiProperties;
    }

    public AIConversation getOrCreateConversation(String conversationId, String clientToken, String customerName, String customerPhone) {
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            Optional<AIConversation> optConv = conversationRepository.findByConversationId(conversationId.trim());
            if (optConv.isPresent()) {
                AIConversation conv = optConv.get();
                // Validate client token if provided
                if (clientToken != null && conv.getClientToken() != null && !conv.getClientToken().equals(clientToken)) {
                    log.warn("Token de cliente no coincide para la conversación {}", conversationId);
                    // Create new session to avoid leaking someone else's data
                    return createNewConversation(customerName, customerPhone);
                }

                if (customerName != null && !customerName.trim().isEmpty()) {
                    conv.setCustomerName(customerName.trim());
                }
                if (customerPhone != null && !customerPhone.trim().isEmpty()) {
                    conv.setCustomerPhone(customerPhone.trim());
                }

                // Check cart expiration
                if (conv.getCart() != null && conv.getCart().isExpired()) {
                    conv.getCart().setStatus(AICart.CartStatus.EXPIRED);
                }

                return conv;
            }
        }

        return createNewConversation(customerName, customerPhone);
    }

    public AIConversation createNewConversation(String customerName, String customerPhone) {
        String newId = "conv-" + UUID.randomUUID().toString();
        String newToken = UUID.randomUUID().toString();

        AICart initialCart = AICart.builder()
                .cartId("cart-" + UUID.randomUUID().toString().substring(0, 8))
                .items(new ArrayList<>())
                .status(AICart.CartStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(lemonAiProperties.getCartExpirationMinutes()))
                .build();

        AIConversation conversation = AIConversation.builder()
                .conversationId(newId)
                .clientToken(newToken)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .state(ConversationState.IDLE)
                .cart(initialCart)
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return conversationRepository.save(conversation);
    }

    public Optional<AIConversation> getConversationSecurely(String conversationId, String clientToken) {
        if (conversationId == null || conversationId.trim().isEmpty()) return Optional.empty();
        if (clientToken != null && !clientToken.trim().isEmpty()) {
            return conversationRepository.findByConversationIdAndClientToken(conversationId.trim(), clientToken.trim());
        }
        return Optional.empty();
    }

    public AIConversation save(AIConversation conversation) {
        // Message compaction if too many messages
        if (conversation.getMessages() != null && conversation.getMessages().size() > 24) {
            compactMessages(conversation);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversationRepository.save(conversation);
    }

    private void compactMessages(AIConversation conversation) {
        List<AIMessage> all = conversation.getMessages();
        if (all.size() <= 20) return;

        // Keep system prompt if present, summarize older messages, and keep latest 12 messages
        List<AIMessage> recent = new ArrayList<>(all.subList(all.size() - 12, all.size()));
        
        AIMessage summary = AIMessage.builder()
                .role("system")
                .content("[Contexto previo resumido: El cliente ha estado interactuando con el menú de Lemon Drop y revisando opciones]")
                .timestamp(LocalDateTime.now())
                .build();

        List<AIMessage> compacted = new ArrayList<>();
        compacted.add(summary);
        compacted.addAll(recent);
        conversation.setMessages(compacted);
    }
}
