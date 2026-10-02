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
        List<Addon> list = addonRepository.findAll();
        list.sort((a, b) -> {
            int ordA = a.getDisplayOrder() != null ? a.getDisplayOrder() : 999;
            int ordB = b.getDisplayOrder() != null ? b.getDisplayOrder() : 999;
            return Integer.compare(ordA, ordB);
        });
        return list;
    }

    public List<Addon> getAvailableAddons() {
        List<Addon> list = addonRepository.findByAvailableTrue();
        list.sort((a, b) -> {
            int ordA = a.getDisplayOrder() != null ? a.getDisplayOrder() : 999;
            int ordB = b.getDisplayOrder() != null ? b.getDisplayOrder() : 999;
            return Integer.compare(ordA, ordB);
        });
        return list;
    }

    public Optional<Addon> getById(String id) {
        return addonRepository.findById(id);
    }

    public Addon save(Addon addon) {
        if (addon.getPriceType() == com.lemondrop.model.PriceType.FREE) {
            addon.setAdditionalPrice(java.math.BigDecimal.ZERO);
        } else {
            if (addon.getAdditionalPrice() == null) {
                addon.setAdditionalPrice(java.math.BigDecimal.ZERO);
            }
            if (addon.getPriceType() == null) {
                addon.setPriceType(addon.getAdditionalPrice().compareTo(java.math.BigDecimal.ZERO) > 0 ?
                        com.lemondrop.model.PriceType.PAID : com.lemondrop.model.PriceType.FREE);
            }
        }
        if (addon.getDisplayOrder() == null) {
            addon.setDisplayOrder(0);
        }
        return addonRepository.save(addon);
    }

    public void delete(String id) {
        addonRepository.deleteById(id);
    }
}
