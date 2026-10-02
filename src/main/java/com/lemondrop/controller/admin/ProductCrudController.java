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
        model.addAttribute("availSmall", true);
        model.addAttribute("availMedium", true);
        model.addAttribute("availLarge", true);
        model.addAttribute("priceSmall", "");
        model.addAttribute("priceMedium", "");
        model.addAttribute("priceLarge", "");
        return "admin/producto-form";
    }

    @GetMapping("/editar/{id}")
    public String showEditForm(@PathVariable String id, Model model) {
        Optional<Product> productOpt = productService.getById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            model.addAttribute("product", product);
            
            boolean availSmall = product.isSizeAvailable(ProductSize.SMALL);
            boolean availMedium = product.isSizeAvailable(ProductSize.MEDIUM);
            boolean availLarge = product.isSizeAvailable(ProductSize.LARGE);

            // Default fallback if brand new or unconfigured
            if (!availSmall && !availMedium && !availLarge && product.getSizePrices().isEmpty()) {
                availSmall = true;
                availMedium = true;
                availLarge = true;
            }

            model.addAttribute("availSmall", availSmall);
            model.addAttribute("availMedium", availMedium);
            model.addAttribute("availLarge", availLarge);

            model.addAttribute("priceSmall", product.getPriceForSize(ProductSize.SMALL) != null ? product.getPriceForSize(ProductSize.SMALL) : "");
            model.addAttribute("priceMedium", product.getPriceForSize(ProductSize.MEDIUM) != null ? product.getPriceForSize(ProductSize.MEDIUM) : "");
            model.addAttribute("priceLarge", product.getPriceForSize(ProductSize.LARGE) != null ? product.getPriceForSize(ProductSize.LARGE) : "");
            return "admin/producto-form";
        }
        return "redirect:/admin/productos";
    }

    @PostMapping("/guardar")
    public String save(@ModelAttribute Product product,
                       @RequestParam(required = false, defaultValue = "false") boolean availSmall,
                       @RequestParam(required = false) BigDecimal priceSmall,
                       @RequestParam(required = false, defaultValue = "false") boolean availMedium,
                       @RequestParam(required = false) BigDecimal priceMedium,
                       @RequestParam(required = false, defaultValue = "false") boolean availLarge,
                       @RequestParam(required = false) BigDecimal priceLarge,
                       @RequestParam(required = false, defaultValue = "false") boolean available,
                       @RequestParam(required = false, defaultValue = "false") boolean featured) {
        
        Map<ProductSize, com.lemondrop.model.ProductSizeInfo> sizesMap = new HashMap<>();
        Map<ProductSize, BigDecimal> legacySizePrices = new HashMap<>();

        if (availSmall && priceSmall != null && priceSmall.compareTo(BigDecimal.ZERO) > 0) {
            sizesMap.put(ProductSize.SMALL, com.lemondrop.model.ProductSizeInfo.builder()
                    .available(true)
                    .price(priceSmall)
                    .build());
            legacySizePrices.put(ProductSize.SMALL, priceSmall);
        } else {
            sizesMap.put(ProductSize.SMALL, com.lemondrop.model.ProductSizeInfo.builder()
                    .available(false)
                    .price(null)
                    .build());
        }

        if (availMedium && priceMedium != null && priceMedium.compareTo(BigDecimal.ZERO) > 0) {
            sizesMap.put(ProductSize.MEDIUM, com.lemondrop.model.ProductSizeInfo.builder()
                    .available(true)
                    .price(priceMedium)
                    .build());
            legacySizePrices.put(ProductSize.MEDIUM, priceMedium);
        } else {
            sizesMap.put(ProductSize.MEDIUM, com.lemondrop.model.ProductSizeInfo.builder()
                    .available(false)
                    .price(null)
                    .build());
        }

        if (availLarge && priceLarge != null && priceLarge.compareTo(BigDecimal.ZERO) > 0) {
            sizesMap.put(ProductSize.LARGE, com.lemondrop.model.ProductSizeInfo.builder()
                    .available(true)
                    .price(priceLarge)
                    .build());
            legacySizePrices.put(ProductSize.LARGE, priceLarge);
        } else {
            sizesMap.put(ProductSize.LARGE, com.lemondrop.model.ProductSizeInfo.builder()
                    .available(false)
                    .price(null)
                    .build());
        }
        
        product.setSizes(sizesMap);
        product.setSizePrices(legacySizePrices);
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
