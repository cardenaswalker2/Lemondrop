package com.lemondrop.controller.admin;

import com.lemondrop.model.InventoryItem;
import com.lemondrop.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;

@Controller
@RequestMapping("/admin/inventario")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("items", inventoryService.getAll());
        return "admin/inventario";
    }

    @PostMapping("/guardar")
    public String save(@ModelAttribute InventoryItem item) {
        if (item.getQuantity() == null) {
            item.setQuantity(BigDecimal.ZERO);
        }
        if (item.getMinStock() == null) {
            item.setMinStock(BigDecimal.ZERO);
        }
        inventoryService.save(item);
        return "redirect:/admin/inventario";
    }
}
