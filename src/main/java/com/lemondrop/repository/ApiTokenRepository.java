package com.lemondrop.repository;

import com.lemondrop.model.ApiToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface ApiTokenRepository extends MongoRepository<ApiToken, String> {
    Optional<ApiToken> findByToken(String token);
    void deleteByUsername(String username);
}
