package com.lemondrop.controller.advisor;

import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.OrderStatusHistory;
import com.lemondrop.model.OrderChangeHistory;
import com.lemondrop.model.ProductSize;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import com.lemondrop.repository.OrderChangeHistoryRepository;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.ProductService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.AddonService;
import com.lemondrop.security.SecurityUtils;
import com.lemondrop.service.WhatsAppService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderChangeHistoryRepository changeHistoryRepository;

    public AdvisorDashboardController(OrderService orderService,
                                      ProductService productService,
                                      FlavorService flavorService,
                                      AddonService addonService,
                                      WhatsAppService whatsAppService,
                                      OrderStatusHistoryRepository statusHistoryRepository,
                                      OrderChangeHistoryRepository changeHistoryRepository) {
        this.orderService = orderService;
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.whatsAppService = whatsAppService;
        this.statusHistoryRepository = statusHistoryRepository;
        this.changeHistoryRepository = changeHistoryRepository;
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
        model.addAttribute("deliveredOrders", filterOrders(allOrders, OrderStatus.DELIVERED).stream().limit(15).collect(Collectors.toList()));
        model.addAttribute("cancelledOrders", filterOrders(allOrders, OrderStatus.CANCELLED).stream().limit(15).collect(Collectors.toList()));
        
        // Form dependencies for edits & interactive creation
        model.addAttribute("products", productService.getAllActiveAndAvailable());
        model.addAttribute("flavors", flavorService.getAvailableFlavors());
        model.addAttribute("addons", addonService.getAvailableAddons());
        model.addAttribute("currentUsername", SecurityUtils.getCurrentUsername());
        
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

    @PostMapping("/api/pedidos/{id}/estado")
    @ResponseBody
    public ResponseEntity<?> updateStatusApi(@PathVariable String id,
                                             @RequestBody Map<String, String> payload) {
        try {
            String statusStr = payload.get("status");
            String notes = payload.getOrDefault("notes", "");
            OrderStatus status = OrderStatus.valueOf(statusStr);
            String username = SecurityUtils.getCurrentUsername();
            Order updated = orderService.updateOrderStatus(id, status, notes, username);
            return ResponseEntity.ok(mapOrderToResponse(updated));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al actualizar estado del pedido."));
        }
    }

    @PostMapping("/pedidos/{id}/claim")
    public String claimOrderForm(@PathVariable String id) {
        String username = SecurityUtils.getCurrentUsername();
        orderService.claimOrder(id, username);
        return "redirect:/asesor/dashboard";
    }

    @PostMapping("/api/pedidos/{id}/claim")
    @ResponseBody
    public ResponseEntity<?> claimOrderApi(@PathVariable String id) {
        try {
            String username = SecurityUtils.getCurrentUsername();
            Order updated = orderService.claimOrder(id, username);
            return ResponseEntity.ok(mapOrderToResponse(updated));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al tomar el pedido."));
        }
    }

    @PostMapping("/api/pedidos/{id}/editar-multi")
    @ResponseBody
    public ResponseEntity<?> editOrderMultiApi(@PathVariable String id,
                                               @RequestBody Map<String, Object> payload) {
        try {
            String username = SecurityUtils.getCurrentUsername();
            String reason = (String) payload.getOrDefault("reason", "Modificación de productos por asesor");
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El motivo de la modificación es obligatorio."));
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) payload.get("items");
            if (itemsRaw == null || itemsRaw.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El pedido debe contener al menos un producto."));
            }

            List<OrderItemDto> itemsDto = new ArrayList<>();
            for (Map<String, Object> iMap : itemsRaw) {
                String productId = (String) iMap.get("productId");
                String flavorId = (String) iMap.get("flavorId");
                String sizeStr = (String) iMap.get("size");
                int quantity = ((Number) iMap.getOrDefault("quantity", 1)).intValue();
                String obs = (String) iMap.getOrDefault("observations", "");
                
                @SuppressWarnings("unchecked")
                List<String> addonIds = (List<String>) iMap.getOrDefault("addonIds", new ArrayList<>());

                itemsDto.add(OrderItemDto.builder()
                        .productId(productId)
                        .flavorId(flavorId)
                        .size(ProductSize.valueOf(sizeStr))
                        .quantity(quantity)
                        .addonIds(addonIds)
                        .observations(obs)
                        .build());
            }

            Order updated = orderService.modifyOrder(id, itemsDto, reason, username);
            return ResponseEntity.ok(mapOrderToResponse(updated));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar la modificación: " + e.getMessage()));
        }
    }

    @GetMapping("/api/pedidos/{id}/detalle")
    @ResponseBody
    public ResponseEntity<?> getOrderDetailApi(@PathVariable String id) {
        Optional<Order> orderOpt = orderService.getOrderById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pedido no encontrado."));
        }
        Order order = orderOpt.get();
        Map<String, Object> response = mapOrderToResponse(order);

        List<OrderStatusHistory> statusHist = statusHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(order.getId());
        List<Map<String, Object>> statusList = statusHist.stream().map(h -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", h.getId());
            m.put("status", h.getStatus().name());
            m.put("statusDisplay", h.getStatus().getDisplayName());
            m.put("notes", h.getNotes() != null ? h.getNotes() : "");
            m.put("updatedBy", h.getUpdatedBy() != null ? h.getUpdatedBy() : "SISTEMA");
            m.put("updatedAt", h.getUpdatedAt() != null ? h.getUpdatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());
        response.put("statusHistory", statusList);

        List<OrderChangeHistory> changeHist = changeHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(order.getId());
        List<Map<String, Object>> changeList = changeHist.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("propertyName", c.getPropertyName());
            m.put("oldValue", c.getOldValue() != null ? c.getOldValue() : "");
            m.put("newValue", c.getNewValue() != null ? c.getNewValue() : "");
            m.put("reason", c.getReason() != null ? c.getReason() : "");
            m.put("updatedBy", c.getUpdatedBy() != null ? c.getUpdatedBy() : "SISTEMA");
            m.put("updatedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());
        response.put("changeHistory", changeList);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/pedidos/updates")
    @ResponseBody
    public ResponseEntity<?> getActiveOrdersApi() {
        List<Order> activeOrders = orderService.getAllOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.DELIVERED && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        List<Map<String, Object>> response = activeOrders.stream()
                .map(this::mapOrderToResponse)
                .collect(Collectors.toList());

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
                            || (o.getCustomerPhone() != null && o.getCustomerPhone().contains(q))
                            || (o.getAssignedAdvisor() != null && o.getAssignedAdvisor().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }
        
        List<Map<String, Object>> response = finishedOrders.stream()
                .map(this::mapOrderToResponse)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> mapOrderToResponse(Order o) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", o.getId());
        map.put("code", o.getOrderCode());
        map.put("customerName", o.getCustomerName());
        map.put("customerPhone", o.getCustomerPhone() != null ? o.getCustomerPhone() : "");
        map.put("status", o.getStatus().name());
        map.put("statusDisplay", o.getStatus().getDisplayName());
        map.put("total", o.getTotal());
        map.put("subtotal", o.getSubtotal());
        map.put("observations", o.getObservations() != null ? o.getObservations() : "");
        map.put("advisorNotes", o.getAdvisorNotes() != null ? o.getAdvisorNotes() : "");
        map.put("cancellationReason", o.getCancellationReason() != null ? o.getCancellationReason() : "");
        map.put("items", o.getItems());
        map.put("priority", o.getPriority() != null ? o.getPriority() : "NORMAL");
        map.put("assignedAdvisor", o.getAssignedAdvisor() != null ? o.getAssignedAdvisor() : "Sin asignar");
        map.put("lastModifiedBy", o.getLastModifiedBy() != null ? o.getLastModifiedBy() : "GUEST");
        map.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
        map.put("updatedAt", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null);
        map.put("receivedAt", o.getReceivedAt() != null ? o.getReceivedAt().toString() : null);
        map.put("acceptedAt", o.getAcceptedAt() != null ? o.getAcceptedAt().toString() : null);
        map.put("preparingAt", o.getPreparingAt() != null ? o.getPreparingAt().toString() : null);
        map.put("almostReadyAt", o.getAlmostReadyAt() != null ? o.getAlmostReadyAt().toString() : null);
        map.put("readyAt", o.getReadyAt() != null ? o.getReadyAt().toString() : null);
        map.put("deliveredAt", o.getDeliveredAt() != null ? o.getDeliveredAt().toString() : null);
        map.put("cancelledAt", o.getCancelledAt() != null ? o.getCancelledAt().toString() : null);
        map.put("whatsappUrl", whatsAppService.generateWhatsAppUrl(o));
        return map;
    }

    private List<Order> filterOrders(List<Order> orders, OrderStatus status) {
        return orders.stream()
                .filter(o -> o.getStatus() == status)
                .collect(Collectors.toList());
    }
}

