package com.lemondrop.ai.repository;

import com.lemondrop.ai.model.AIAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIAuditLogRepository extends MongoRepository<AIAuditLog, String> {
    List<AIAuditLog> findByConversationIdOrderByTimestampDesc(String conversationId);
}
