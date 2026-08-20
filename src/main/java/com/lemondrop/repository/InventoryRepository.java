package com.lemondrop.repository;

import com.lemondrop.model.InventoryItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {
    Optional<InventoryItem> findByNameIgnoreCase(String name);
}
