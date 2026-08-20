package com.lemondrop.service;

import com.lemondrop.model.Addon;
import com.lemondrop.repository.AddonRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AddonService {

    private final AddonRepository addonRepository;

    public AddonService(AddonRepository addonRepository) {
        this.addonRepository = addonRepository;
    }

    public List<Addon> getAll() {
        return addonRepository.findAll();
    }

    public List<Addon> getAvailableAddons() {
        return addonRepository.findByAvailableTrue();
    }

    public Optional<Addon> getById(String id) {
        return addonRepository.findById(id);
    }

    public Addon save(Addon addon) {
        return addonRepository.save(addon);
    }

    public void delete(String id) {
        addonRepository.deleteById(id);
    }
}
