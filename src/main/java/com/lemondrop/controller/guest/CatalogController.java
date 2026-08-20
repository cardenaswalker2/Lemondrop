package com.lemondrop.controller.guest;

import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.model.Order;
import com.lemondrop.service.ProductService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.AddonService;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.WhatsAppService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping
public class CatalogController {

    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;
    private final OrderService orderService;
    private final WhatsAppService whatsAppService;

    public CatalogController(ProductService productService,
                             FlavorService flavorService,
                             AddonService addonService,
                             OrderService orderService,
                             WhatsAppService whatsAppService) {
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.orderService = orderService;
        this.whatsAppService = whatsAppService;
    }

    @GetMapping("/catalogo")
    public String catalog(Model model) {
        model.addAttribute("products", productService.getAllActiveAndAvailable());
        model.addAttribute("flavors", flavorService.getAvailableFlavors());
        model.addAttribute("addons", addonService.getAvailableAddons());
        return "public/catalogo";
    }

    @PostMapping("/api/public/pedidos")
    @ResponseBody
    public ResponseEntity<?> createOrderApi(@Valid @RequestBody CreateOrderRequest request) {
        try {
            Order order = orderService.createOrder(request);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orderCode", order.getOrderCode());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/api/public/pedidos/track/{query}")
    @ResponseBody
    public ResponseEntity<?> trackOrderApi(@PathVariable String query) {
        String cleanQuery = query.trim();
        boolean isNumeric = cleanQuery.matches("\\d+");
        
        if (isNumeric) {
            java.util.List<Order> orders = orderService.getOrdersByPhone(cleanQuery);
            if (!orders.isEmpty()) {
                java.util.List<Map<String, Object>> responseList = orders.stream().map(order -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderCode", order.getOrderCode());
                    map.put("customerName", order.getCustomerName());
                    map.put("status", order.getStatus().name());
                    map.put("statusDisplay", order.getStatus().getDisplayName());
                    map.put("total", order.getTotal());
                    
                    java.util.List<Map<String, Object>> itemsList = order.getItems().stream().map(item -> {
                        Map<String, Object> itemMap = new HashMap<>();
                        itemMap.put("productName", item.getProductName());
                        itemMap.put("flavorName", item.getFlavorName());
                        itemMap.put("size", item.getSize().name());
                        itemMap.put("quantity", item.getQuantity());
                        itemMap.put("subtotal", item.getSubtotal());
                        return itemMap;
                    }).collect(java.util.stream.Collectors.toList());
                    
                    map.put("items", itemsList);
                    return map;
                }).collect(java.util.stream.Collectors.toList());
                
                return ResponseEntity.ok(Map.of("success", true, "multiple", true, "orders", responseList));
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "No se encontraron pedidos asociados a ese número.");
                return ResponseEntity.status(404).body(response);
            }
        } else {
            Optional<Order> orderOpt = orderService.getOrderByCode(cleanQuery.toUpperCase());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("multiple", false);
                response.put("orderCode", order.getOrderCode());
                response.put("customerName", order.getCustomerName());
                response.put("status", order.getStatus().name());
                response.put("statusDisplay", order.getStatus().getDisplayName());
                response.put("total", order.getTotal());
                
                java.util.List<Map<String, Object>> itemsList = order.getItems().stream().map(item -> {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("productName", item.getProductName());
                    itemMap.put("flavorName", item.getFlavorName());
                    itemMap.put("size", item.getSize().name());
                    itemMap.put("quantity", item.getQuantity());
                    itemMap.put("subtotal", item.getSubtotal());
                    return itemMap;
                }).collect(java.util.stream.Collectors.toList());
                
                response.put("items", itemsList);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "No se encontró ningún pedido con ese código.");
                return ResponseEntity.status(404).body(response);
            }
        }
    }

    @GetMapping("/pedido/exitoso/{code}")
    public String orderSuccess(@PathVariable String code, Model model) {
        Optional<Order> orderOpt = orderService.getOrderByCode(code);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            model.addAttribute("order", order);
            model.addAttribute("whatsappUrl", whatsAppService.generateWhatsAppUrl(order));
            return "public/pedido-exitoso";
        }
        return "redirect:/";
    }
}
