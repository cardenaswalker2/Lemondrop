package com.lemondrop.repository;

import com.lemondrop.model.Flavor;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface FlavorRepository extends MongoRepository<Flavor, String> {
    List<Flavor> findByAvailableTrue();
}
