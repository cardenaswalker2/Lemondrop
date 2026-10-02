package com.lemondrop.controller.admin;

import com.lemondrop.model.Addon;
import com.lemondrop.service.AddonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
@RequestMapping("/admin/complementos")
public class AddonCrudController {

    private final AddonService addonService;

    public AddonCrudController(AddonService addonService) {
        this.addonService = addonService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("addons", addonService.getAll());
        return "admin/complementos";
    }

    @PostMapping("/guardar")
    public String save(@ModelAttribute Addon addon,
                       @RequestParam(required = false, defaultValue = "false") boolean available,
                       @RequestParam(required = false, defaultValue = "PAID") com.lemondrop.model.PriceType priceType,
                       @RequestParam(required = false) BigDecimal additionalPrice,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) Integer displayOrder) {
        addon.setAvailable(available);
        addon.setPriceType(priceType);
        addon.setCategory(category != null && !category.trim().isEmpty() ? category.trim() : "Toppings");
        addon.setDisplayOrder(displayOrder != null ? displayOrder : 0);
        
        if (priceType == com.lemondrop.model.PriceType.FREE) {
            addon.setAdditionalPrice(BigDecimal.ZERO);
        } else {
            addon.setAdditionalPrice(additionalPrice != null ? additionalPrice : BigDecimal.ZERO);
        }
        
        addonService.save(addon);
        return "redirect:/admin/complementos";
    }

    @PostMapping("/eliminar/{id}")
    public String delete(@PathVariable String id) {
        addonService.delete(id);
        return "redirect:/admin/complementos";
    }
}
