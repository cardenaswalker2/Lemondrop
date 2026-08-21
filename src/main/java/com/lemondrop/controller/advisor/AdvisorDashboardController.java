package com.lemondrop.controller.advisor;

import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.ProductSize;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.ProductService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.AddonService;
import com.lemondrop.security.SecurityUtils;
import com.lemondrop.service.WhatsAppService;
import org.springframework.http.ResponseEntity;
import java.time.LocalDateTime;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/asesor")
public class AdvisorDashboardController {

    private final OrderService orderService;
    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;
    private final WhatsAppService whatsAppService;

    public AdvisorDashboardController(OrderService orderService,
                                      ProductService productService,
                                      FlavorService flavorService,
                                      AddonService addonService,
                                      WhatsAppService whatsAppService) {
        this.orderService = orderService;
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.whatsAppService = whatsAppService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Order> allOrders = orderService.getAllOrders();
        
        // Group orders for the Kanban board
        model.addAttribute("receivedOrders", filterOrders(allOrders, OrderStatus.RECEIVED));
        model.addAttribute("acceptedOrders", filterOrders(allOrders, OrderStatus.ACCEPTED));
        model.addAttribute("preparingOrders", filterOrders(allOrders, OrderStatus.PREPARING));
        model.addAttribute("almostReadyOrders", filterOrders(allOrders, OrderStatus.ALMOST_READY));
        model.addAttribute("readyOrders", filterOrders(allOrders, OrderStatus.READY));
        model.addAttribute("deliveredOrders", filterOrders(allOrders, OrderStatus.DELIVERED).stream().limit(10).collect(Collectors.toList()));
        model.addAttribute("cancelledOrders", filterOrders(allOrders, OrderStatus.CANCELLED).stream().limit(10).collect(Collectors.toList()));
        
        // Form dependencies for edits
        model.addAttribute("products", productService.getAllActiveAndAvailable());
        model.addAttribute("flavors", flavorService.getAvailableFlavors());
        model.addAttribute("addons", addonService.getAvailableAddons());
        
        return "advisor/dashboard";
    }

    @PostMapping("/pedidos/{id}/estado")
    public String updateStatus(@PathVariable String id,
                               @RequestParam OrderStatus status,
                               @RequestParam(required = false, defaultValue = "") String notes) {
        String username = SecurityUtils.getCurrentUsername();
        orderService.updateOrderStatus(id, status, notes, username);
        return "redirect:/asesor/dashboard";
    }

    @PostMapping("/pedidos/{id}/editar")
    public String editOrder(@PathVariable String id,
                            @RequestParam String productId,
                            @RequestParam String flavorId,
                            @RequestParam ProductSize size,
                            @RequestParam Integer quantity,
                            @RequestParam(required = false) List<String> addonIds,
                            @RequestParam(required = false, defaultValue = "") String observations,
                            @RequestParam String reason) {
        String username = SecurityUtils.getCurrentUsername();
        
        OrderItemDto itemDto = OrderItemDto.builder()
                .productId(productId)
                .flavorId(flavorId)
                .size(size)
                .quantity(quantity)
                .addonIds(addonIds != null ? addonIds : new ArrayList<>())
                .observations(observations)
                .build();

        orderService.modifyOrder(id, Collections.singletonList(itemDto), reason, username);
        return "redirect:/asesor/dashboard";
    }

    @GetMapping("/api/pedidos/updates")
    @ResponseBody
    public ResponseEntity<?> getActiveOrdersApi() {
        List<Order> activeOrders = orderService.getAllOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.DELIVERED && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        List<Map<String, Object>> response = activeOrders.stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("code", o.getOrderCode());
            map.put("customerName", o.getCustomerName());
            map.put("status", o.getStatus().name());
            map.put("statusDisplay", o.getStatus().getDisplayName());
            map.put("total", o.getTotal());
            map.put("createdAt", o.getCreatedAt().toString());
            map.put("whatsappUrl", whatsAppService.generateWhatsAppUrl(o));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/pedidos/historial")
    @ResponseBody
    public ResponseEntity<?> getHistorialOrdersApi(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "TODOS") String filter) {
        
        List<Order> allOrders = orderService.getAllOrders();
        
        List<Order> finishedOrders = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED || o.getStatus() == OrderStatus.CANCELLED)
                .collect(Collectors.toList());
                
        LocalDateTime now = LocalDateTime.now();
        if ("HOY".equalsIgnoreCase(filter)) {
            LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
            finishedOrders = finishedOrders.stream()
                    .filter(o -> o.getCreatedAt().isAfter(startOfToday))
                    .collect(Collectors.toList());
        } else if ("AYER".equalsIgnoreCase(filter)) {
            LocalDateTime startOfYesterday = now.toLocalDate().minusDays(1).atStartOfDay();
            LocalDateTime endOfYesterday = now.toLocalDate().atStartOfDay().minusSeconds(1);
            finishedOrders = finishedOrders.stream()
                    .filter(o -> o.getCreatedAt().isAfter(startOfYesterday) && o.getCreatedAt().isBefore(endOfYesterday))
                    .collect(Collectors.toList());
        } else if ("7_DIAS".equalsIgnoreCase(filter)) {
            LocalDateTime startOf7DaysAgo = now.toLocalDate().minusDays(7).atStartOfDay();
            finishedOrders = finishedOrders.stream()
                    .filter(o -> o.getCreatedAt().isAfter(startOf7DaysAgo))
                    .collect(Collectors.toList());
        }
        
        if (query != null && !query.trim().isEmpty()) {
            final String q = query.toLowerCase().trim();
            finishedOrders = finishedOrders.stream()
                    .filter(o -> o.getOrderCode().toLowerCase().contains(q)
                            || o.getCustomerName().toLowerCase().contains(q)
                            || (o.getCustomerPhone() != null && o.getCustomerPhone().contains(q)))
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> response = finishedOrders.stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("code", o.getOrderCode());
            map.put("customerName", o.getCustomerName());
            map.put("total", o.getTotal());
            map.put("status", o.getStatus().name());
            map.put("statusDisplay", o.getStatus().getDisplayName());
            map.put("createdAt", o.getCreatedAt().toString());
            map.put("lastModifiedBy", o.getLastModifiedBy() != null ? o.getLastModifiedBy() : "GUEST");
            return map;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    private List<Order> filterOrders(List<Order> orders, OrderStatus status) {
        return orders.stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
    }
}
