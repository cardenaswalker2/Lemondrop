package com.lemondrop.service;

import com.lemondrop.model.Product;
import com.lemondrop.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllActive() {
        return productRepository.findByActiveTrue();
    }

    public List<Product> getAllActiveAndAvailable() {
        return productRepository.findByActiveTrueAndAvailableTrue();
    }

    public List<Product> getFeatured() {
        return productRepository.findByActiveTrueAndFeaturedTrue();
    }

    public Optional<Product> getById(String id) {
        return productRepository.findById(id);
    }

    public Product save(Product product) {
        product.setActive(true);
        return productRepository.save(product);
    }

    public void softDelete(String id) {
        productRepository.findById(id).ifPresent(product -> {
            product.setActive(false);
            productRepository.save(product);
        });
    }
}
