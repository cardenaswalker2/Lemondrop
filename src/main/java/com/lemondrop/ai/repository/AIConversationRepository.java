package com.lemondrop.ai.repository;

import com.lemondrop.ai.model.AIConversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AIConversationRepository extends MongoRepository<AIConversation, String> {
    Optional<AIConversation> findByConversationId(String conversationId);
    Optional<AIConversation> findByConversationIdAndClientToken(String conversationId, String clientToken);
    List<AIConversation> findByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);
    List<AIConversation> findByUpdatedAtBefore(LocalDateTime threshold);
}
