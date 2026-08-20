package com.lemondrop.repository;

import com.lemondrop.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByActiveTrue();
    List<Product> findByActiveTrueAndAvailableTrue();
    List<Product> findByActiveTrueAndFeaturedTrue();
}
