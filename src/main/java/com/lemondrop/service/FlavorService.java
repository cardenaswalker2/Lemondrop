package com.lemondrop.service;

import com.lemondrop.model.Flavor;
import com.lemondrop.repository.FlavorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FlavorService {

    private final FlavorRepository flavorRepository;

    public FlavorService(FlavorRepository flavorRepository) {
        this.flavorRepository = flavorRepository;
    }

    public List<Flavor> getAll() {
        return flavorRepository.findAll();
    }

    public List<Flavor> getAvailableFlavors() {
        return flavorRepository.findByAvailableTrue();
    }

    public Optional<Flavor> getById(String id) {
        return flavorRepository.findById(id);
    }

    public Flavor save(Flavor flavor) {
        return flavorRepository.save(flavor);
    }

    public void delete(String id) {
        flavorRepository.deleteById(id);
    }
}
