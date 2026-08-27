package com.lemondrop.controller.admin;

import com.lemondrop.dto.order.AdminOrderEditRequest;
import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.User;
import com.lemondrop.repository.OrderChangeHistoryRepository;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import com.lemondrop.service.*;
import com.lemondrop.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/pedidos")
public class AdminOrderApiController {

    private final OrderService orderService;
    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;
    private final UserService userService;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderChangeHistoryRepository changeHistoryRepository;

    public AdminOrderApiController(OrderService orderService,
                                  ProductService productService,
                                  FlavorService flavorService,
                                  AddonService addonService,
                                  UserService userService,
                                  OrderStatusHistoryRepository statusHistoryRepository,
                                  OrderChangeHistoryRepository changeHistoryRepository) {
        this.orderService = orderService;
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.userService = userService;
        this.statusHistoryRepository = statusHistoryRepository;
        this.changeHistoryRepository = changeHistoryRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderDetails(@PathVariable String id) {
        Order order = orderService.getOrderById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));

        Map<String, Object> response = new HashMap<>();
        response.put("order", order);
        response.put("products", productService.getAllActiveAndAvailable());
        response.put("flavors", flavorService.getAvailableFlavors());
        response.put("addons", addonService.getAvailableAddons());
        
        List<User> advisors = userService.getAllUsers().stream()
                .filter(u -> "ASESOR".equalsIgnoreCase(u.getRole()) && u.isActive())
                .collect(Collectors.toList());
        response.put("advisors", advisors);

        response.put("statusHistory", statusHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(id));
        response.put("changeHistory", changeHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(id));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/editar")
    public ResponseEntity<?> editOrderAdmin(@PathVariable String id, @RequestBody AdminOrderEditRequest request) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.modifyOrderAdmin(
                    id,
                    request.getCustomerName(),
                    request.getCustomerPhone(),
                    request.getItems(),
                    request.getPriority(),
                    request.getAssignedAdvisor(),
                    request.getReason(),
                    actor
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelOrderAdmin(@PathVariable String id, @RequestParam String reason) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.updateOrderStatus(id, OrderStatus.CANCELLED, reason, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/eliminar")
    public ResponseEntity<?> deleteOrderAdmin(@PathVariable String id, @RequestParam(required = false, defaultValue = "Eliminado lógicamente") String reason) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.deleteOrderLogically(id, reason, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<?> restoreOrderAdmin(@PathVariable String id) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.restoreOrderLogically(id, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @RequestMapping(value = {"/{id}/eliminar-definitivo", "/{id}/definitivo"}, method = {RequestMethod.POST, RequestMethod.DELETE})
    public ResponseEntity<?> deleteOrderPermanentlyAdmin(@PathVariable String id) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            orderService.deleteOrderPermanently(id, actor);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Pedido eliminado definitivamente de la base de datos.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/prioridad")
    public ResponseEntity<?> changePriorityAdmin(@PathVariable String id, @RequestParam String priority) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.togglePriority(id, priority, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/reasignar")
    public ResponseEntity<?> reassignAdvisorAdmin(@PathVariable String id, @RequestParam String advisor) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.reassignAdvisor(id, advisor, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/cerrar")
    public ResponseEntity<?> closeOrderAdmin(@PathVariable String id) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.closeOrder(id, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/reabrir")
    public ResponseEntity<?> reopenOrderAdmin(@PathVariable String id, @RequestParam String reason) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.reopenOrder(id, reason, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/estado")
    public ResponseEntity<?> changeStatusAdmin(@PathVariable String id, @RequestParam OrderStatus status, @RequestParam(required = false, defaultValue = "") String notes) {
        String actor = SecurityUtils.getCurrentUsername();
        try {
            Order updated = orderService.updateOrderStatus(id, status, notes, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
