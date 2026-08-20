package com.lemondrop.controller.guest;

import com.lemondrop.service.ProductService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.AddonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;

    public HomeController(ProductService productService, FlavorService flavorService, AddonService addonService) {
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("featuredProducts", productService.getFeatured());
        model.addAttribute("flavors", flavorService.getAvailableFlavors());
        model.addAttribute("addons", addonService.getAvailableAddons());
        return "public/home";
    }

    @GetMapping("/login")
    public String login() {
        return "public/login";
    }
}
