package com.lemondrop.controller.admin;

import com.lemondrop.model.Flavor;
import com.lemondrop.service.FlavorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
@RequestMapping("/admin/sabores")
public class FlavorCrudController {

    private final FlavorService flavorService;

    public FlavorCrudController(FlavorService flavorService) {
        this.flavorService = flavorService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("flavors", flavorService.getAll());
        return "admin/sabores";
    }

    @PostMapping("/guardar")
    public String save(@ModelAttribute Flavor flavor,
                       @RequestParam(required = false) boolean available) {
        flavor.setAvailable(available);
        if (flavor.getAdditionalPrice() == null) {
            flavor.setAdditionalPrice(BigDecimal.ZERO);
        }
        flavorService.save(flavor);
        return "redirect:/admin/sabores";
    }

    @PostMapping("/eliminar/{id}")
    public String delete(@PathVariable String id) {
        flavorService.delete(id);
        return "redirect:/admin/sabores";
    }
}
