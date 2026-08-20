package com.lemondrop.controller.admin;

import com.lemondrop.model.Product;
import com.lemondrop.model.ProductSize;
import com.lemondrop.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin/productos")
public class ProductCrudController {

    private final ProductService productService;

    public ProductCrudController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", productService.getAllActive());
        return "admin/productos";
    }

    @GetMapping("/nuevo")
    public String showCreateForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("priceSmall", BigDecimal.ZERO);
        model.addAttribute("priceMedium", BigDecimal.ZERO);
        model.addAttribute("priceLarge", BigDecimal.ZERO);
        return "admin/producto-form";
    }

    @GetMapping("/editar/{id}")
    public String showEditForm(@PathVariable String id, Model model) {
        Optional<Product> productOpt = productService.getById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            model.addAttribute("product", product);
            model.addAttribute("priceSmall", product.getSizePrices().getOrDefault(ProductSize.SMALL, BigDecimal.ZERO));
            model.addAttribute("priceMedium", product.getSizePrices().getOrDefault(ProductSize.MEDIUM, BigDecimal.ZERO));
            model.addAttribute("priceLarge", product.getSizePrices().getOrDefault(ProductSize.LARGE, BigDecimal.ZERO));
            return "admin/producto-form";
        }
        return "redirect:/admin/productos";
    }

    @PostMapping("/guardar")
    public String save(@ModelAttribute Product product,
                       @RequestParam BigDecimal priceSmall,
                       @RequestParam BigDecimal priceMedium,
                       @RequestParam BigDecimal priceLarge,
                       @RequestParam(required = false) boolean available,
                       @RequestParam(required = false) boolean featured) {
        
        Map<ProductSize, BigDecimal> sizePrices = new HashMap<>();
        sizePrices.put(ProductSize.SMALL, priceSmall);
        sizePrices.put(ProductSize.MEDIUM, priceMedium);
        sizePrices.put(ProductSize.LARGE, priceLarge);
        
        product.setSizePrices(sizePrices);
        product.setAvailable(available);
        product.setFeatured(featured);
        
        productService.save(product);
        return "redirect:/admin/productos";
    }

    @PostMapping("/eliminar/{id}")
    public String softDelete(@PathVariable String id) {
        productService.softDelete(id);
        return "redirect:/admin/productos";
    }
}
