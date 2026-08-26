package com.lemondrop.controller.api;

import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.*;
import com.lemondrop.repository.*;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.ProductService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.AddonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mobile")
public class MobileApiController {

    private final UserRepository userRepository;
    private final ApiTokenRepository apiTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderChangeHistoryRepository changeHistoryRepository;

    public MobileApiController(UserRepository userRepository,
                               ApiTokenRepository apiTokenRepository,
                               PasswordEncoder passwordEncoder,
                               OrderRepository orderRepository,
                               OrderService orderService,
                               ProductService productService,
                               FlavorService flavorService,
                               AddonService addonService,
                               OrderStatusHistoryRepository statusHistoryRepository,
                               OrderChangeHistoryRepository changeHistoryRepository) {
        this.userRepository = userRepository;
        this.apiTokenRepository = apiTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.statusHistoryRepository = statusHistoryRepository;
        this.changeHistoryRepository = changeHistoryRepository;
    }

    // 1. POST /api/mobile/auth/login
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Falta usuario o contraseña."));
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Usuario o contraseña incorrectos."));
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "El usuario está inactivo."));
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Usuario o contraseña incorrectos."));
        }

        // Clean up old tokens for this user
        apiTokenRepository.deleteByUsername(username);

        // Generate new token
        String tokenStr = UUID.randomUUID().toString();
        ApiToken token = ApiToken.builder()
                .token(tokenStr)
                .username(username)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        apiTokenRepository.save(token);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("username", user.getUsername());
        userData.put("role", user.getRole());
        userData.put("phone", user.getPhone() != null ? user.getPhone() : "");

        Map<String, Object> response = new HashMap<>();
        response.put("token", tokenStr);
        response.put("user", userData);

        return ResponseEntity.ok(response);
    }

    // 2. GET /api/mobile/me
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        String tokenStr = authHeader.replace("Bearer ", "").trim();
        Optional<ApiToken> tokenOpt = apiTokenRepository.findByToken(tokenStr);
        if (tokenOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Sesión inválida o expirada."));
        }

        Optional<User> userOpt = userRepository.findByUsername(tokenOpt.get().getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Usuario no encontrado."));
        }

        User user = userOpt.get();
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("name", user.getName());
        userData.put("username", user.getUsername());
        userData.put("role", user.getRole());
        userData.put("phone", user.getPhone() != null ? user.getPhone() : "");

        return ResponseEntity.ok(userData);
    }

    // 3. GET /api/mobile/orders (active orders with optional status filter)
    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> ordersPage;

        if (status != null && !status.isEmpty()) {
            try {
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                ordersPage = orderRepository.findByStatus(orderStatus, pageable);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Estado inválido."));
            }
        } else {
            // By default, fetch active orders (not DELIVERED or CANCELLED)
            List<OrderStatus> activeStatuses = List.of(
                    OrderStatus.RECEIVED,
                    OrderStatus.ACCEPTED,
                    OrderStatus.PREPARING,
                    OrderStatus.ALMOST_READY,
                    OrderStatus.READY
            );
            ordersPage = orderRepository.findByStatusIn(activeStatuses, pageable);
        }

        return ResponseEntity.ok(ordersPage);
    }

    // 4. GET /api/mobile/orders/history (history of completed/cancelled orders with server-side filters & search)
    @GetMapping("/orders/history")
    public ResponseEntity<?> getOrderHistory(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false, defaultValue = "") String query,
                                             @RequestParam(required = false, defaultValue = "TODOS") String filter,
                                             @RequestParam(required = false, defaultValue = "ALL") String status) {
        List<Order> allOrders = orderService.getAllOrders();

        List<Order> finishedOrders = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED || o.getStatus() == OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        // Filter by status if specified
        if ("DELIVERED".equalsIgnoreCase(status)) {
            finishedOrders = finishedOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).collect(Collectors.toList());
        } else if ("CANCELLED".equalsIgnoreCase(status)) {
            finishedOrders = finishedOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).collect(Collectors.toList());
        }

        // Filter by time range
        LocalDateTime now = LocalDateTime.now();
        if ("HOY".equalsIgnoreCase(filter)) {
            LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
            finishedOrders = finishedOrders.stream()
                    .filter(o -> (o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(startOfToday))
                            || (o.getCancelledAt() != null && o.getCancelledAt().isAfter(startOfToday))
                            || (o.getUpdatedAt() != null && o.getUpdatedAt().isAfter(startOfToday))
                            || o.getCreatedAt().isAfter(startOfToday))
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

        // Search query filter (orderCode, customerName, customerPhone)
        if (query != null && !query.trim().isEmpty()) {
            final String q = query.toLowerCase().trim();
            finishedOrders = finishedOrders.stream()
                    .filter(o -> (o.getOrderCode() != null && o.getOrderCode().toLowerCase().contains(q))
                            || (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(q))
                            || (o.getCustomerPhone() != null && o.getCustomerPhone().contains(q)))
                    .collect(Collectors.toList());
        }

        // Sort descending by updatedAt or createdAt
        finishedOrders.sort((a, b) -> {
            LocalDateTime timeA = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            LocalDateTime timeB = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            return timeB.compareTo(timeA);
        });

        int totalElements = finishedOrders.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        if (totalPages == 0) totalPages = 1;

        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Order> pageContent = finishedOrders.subList(fromIndex, toIndex);

        Map<String, Object> result = new HashMap<>();
        result.put("content", pageContent);
        result.put("totalPages", totalPages);
        result.put("totalElements", totalElements);
        result.put("page", page);
        result.put("size", size);
        result.put("isLast", page >= totalPages - 1);

        return ResponseEntity.ok(result);
    }

    // 5. GET /api/mobile/orders/updates (polling optimized endpoint)
    @GetMapping("/orders/updates")
    public ResponseEntity<?> getOrderUpdates(@RequestParam String since) {
        try {
            LocalDateTime sinceTime = LocalDateTime.parse(since);
            List<Order> updatedOrders = orderRepository.findByUpdatedAtAfter(sinceTime);
            return ResponseEntity.ok(updatedOrders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Formato de fecha inválido. Utilice ISO-8601 (yyyy-MM-ddTHH:mm:ss)."));
        }
    }

    // 6. POST /api/mobile/orders/{id}/status (status transitions)
    @PostMapping("/orders/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String id,
                                          @RequestBody Map<String, String> body,
                                          @RequestHeader("Authorization") String authHeader) {
        String tokenStr = authHeader.replace("Bearer ", "").trim();
        ApiToken token = apiTokenRepository.findByToken(tokenStr).orElseThrow();
        String actor = token.getUsername();

        String statusStr = body.get("status");
        String notes = body.getOrDefault("notes", "");

        if (statusStr == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Falta el estado de destino."));
        }

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Estado inválido."));
        }

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Pedido no encontrado."));
        }

        Order order = orderOpt.get();
        OrderStatus currentStatus = order.getStatus();

        // Strict transition validations
        boolean isValidTransition = false;
        switch (currentStatus) {
            case RECEIVED:
                if (newStatus == OrderStatus.ACCEPTED || newStatus == OrderStatus.CANCELLED) {
                    isValidTransition = true;
                }
                break;
            case ACCEPTED:
                if (newStatus == OrderStatus.PREPARING || newStatus == OrderStatus.CANCELLED) {
                    isValidTransition = true;
                }
                break;
            case PREPARING:
                if (newStatus == OrderStatus.ALMOST_READY || newStatus == OrderStatus.CANCELLED) {
                    isValidTransition = true;
                }
                break;
            case ALMOST_READY:
                if (newStatus == OrderStatus.READY || newStatus == OrderStatus.CANCELLED) {
                    isValidTransition = true;
                }
                break;
            case READY:
                if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED) {
                    isValidTransition = true;
                }
                break;
            default:
                break;
        }

        if (!isValidTransition) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Transición de estado inválida de " + currentStatus.name() + " a " + newStatus.name() + "."
            ));
        }

        try {
            Order updated = orderService.updateOrderStatus(id, newStatus, notes, actor);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al actualizar estado: " + e.getMessage()));
        }
    }

    // 7. POST /api/mobile/orders/{id}/claim (advisor claims an order)
    @PostMapping("/orders/{id}/claim")
    public ResponseEntity<?> claimOrder(@PathVariable String id,
                                        @RequestHeader("Authorization") String authHeader) {
        String tokenStr = authHeader.replace("Bearer ", "").trim();
        ApiToken token = apiTokenRepository.findByToken(tokenStr).orElseThrow();
        String actor = token.getUsername();

        try {
            Order updated = orderService.claimOrder(id, actor);
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Error al tomar el pedido: " + e.getMessage()));
        }
    }

    // 8. GET /api/mobile/orders/{id}/status-history
    @GetMapping("/orders/{id}/status-history")
    public ResponseEntity<?> getStatusHistory(@PathVariable String id) {
        List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(id);
        return ResponseEntity.ok(history);
    }

    // 9. GET /api/mobile/orders/{id}/change-history
    @GetMapping("/orders/{id}/change-history")
    public ResponseEntity<?> getChangeHistory(@PathVariable String id) {
        List<OrderChangeHistory> history = changeHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(id);
        return ResponseEntity.ok(history);
    }

    // 10. POST /api/mobile/orders/{id}/edit (edit order items and recalculate)
    @PostMapping("/orders/{id}/edit")
    public ResponseEntity<?> editOrderItems(@PathVariable String id,
                                            @RequestBody Map<String, Object> body,
                                            @RequestHeader("Authorization") String authHeader) {
        String tokenStr = authHeader.replace("Bearer ", "").trim();
        ApiToken token = apiTokenRepository.findByToken(tokenStr).orElseThrow();
        String actor = token.getUsername();

        String reason = (String) body.get("reason");
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) body.get("items");

        if (reason == null || reason.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El motivo del cambio es obligatorio."));
        }
        if (itemsRaw == null || itemsRaw.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La lista de ítems no puede estar vacía."));
        }

        List<OrderItemDto> itemDtos = new ArrayList<>();
        for (Map<String, Object> raw : itemsRaw) {
            try {
                String productId = (String) raw.get("productId");
                String flavorId = (String) raw.get("flavorId");
                String sizeStr = (String) raw.get("size");
                Integer quantity = (Integer) raw.get("quantity");
                List<String> addonIds = (List<String>) raw.getOrDefault("addonIds", new ArrayList<>());
                String observations = (String) raw.getOrDefault("observations", "");

                ProductSize size = ProductSize.valueOf(sizeStr.toUpperCase());

                OrderItemDto dto = OrderItemDto.builder()
                        .productId(productId)
                        .flavorId(flavorId)
                        .size(size)
                        .quantity(quantity)
                        .addonIds(addonIds)
                        .observations(observations)
                        .build();
                itemDtos.add(dto);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Datos de ítem inválidos: " + e.getMessage()));
            }
        }

        try {
            Order updatedOrder = orderService.modifyOrder(id, itemDtos, reason, actor);
            return ResponseEntity.ok(updatedOrder);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    // 11. GET /api/mobile/catalog
    @GetMapping("/catalog")
    public ResponseEntity<?> getCatalog() {
        Map<String, Object> response = new HashMap<>();
        response.put("products", productService.getAllActiveAndAvailable());
        response.put("flavors", flavorService.getAvailableFlavors());
        response.put("addons", addonService.getAvailableAddons());
        return ResponseEntity.ok(response);
    }

    // 12. GET /api/mobile/stats
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String actor = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String tokenStr = authHeader.replace("Bearer ", "").trim();
            Optional<ApiToken> tokenOpt = apiTokenRepository.findByToken(tokenStr);
            if (tokenOpt.isPresent()) {
                actor = tokenOpt.get().getUsername();
            }
        }

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        List<Order> ordersToday = orderRepository.findByCreatedAtBetween(startOfDay, endOfDay);

        // Advisor metrics
        long deliveredCount = ordersToday.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long readyCount = ordersToday.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
        long preparingCount = ordersToday.stream().filter(o -> o.getStatus() == OrderStatus.PREPARING).count();
        long cancelledCount = ordersToday.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        // Admin metrics
        double totalSales = ordersToday.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .mapToDouble(o -> o.getTotal().doubleValue())
                .sum();

        long pendingCount = ordersToday.stream()
                .filter(o -> o.getStatus() == OrderStatus.RECEIVED || o.getStatus() == OrderStatus.ACCEPTED)
                .count();

        // Operational metrics
        List<Order> allActiveOrders = orderService.getAllOrders().stream()
                .filter(o -> o.getStatus() != OrderStatus.DELIVERED && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        long urgentCount = allActiveOrders.stream()
                .filter(o -> "ALTA".equalsIgnoreCase(o.getPriority()))
                .count();

        long unassignedCount = allActiveOrders.stream()
                .filter(o -> o.getAssignedAdvisor() == null || o.getAssignedAdvisor().trim().isEmpty() || "Sin asignar".equalsIgnoreCase(o.getAssignedAdvisor()))
                .count();

        final String finalActor = actor;
        long myDeliveredCount = 0;
        long myActiveCount = 0;

        if (finalActor != null) {
            myDeliveredCount = ordersToday.stream()
                    .filter(o -> o.getStatus() == OrderStatus.DELIVERED &&
                            (finalActor.equalsIgnoreCase(o.getAssignedAdvisor()) || finalActor.equalsIgnoreCase(o.getLastModifiedBy())))
                    .count();

            myActiveCount = allActiveOrders.stream()
                    .filter(o -> finalActor.equalsIgnoreCase(o.getAssignedAdvisor()))
                    .count();
        }

        // Top products/flavors today
        Map<String, Long> productCounts = new HashMap<>();
        Map<String, Long> flavorCounts = new HashMap<>();

        for (Order o : ordersToday) {
            if (o.getStatus() != OrderStatus.CANCELLED) {
                for (OrderItem item : o.getItems()) {
                    productCounts.put(item.getProductName(), productCounts.getOrDefault(item.getProductName(), 0L) + item.getQuantity());
                    flavorCounts.put(item.getFlavorName(), flavorCounts.getOrDefault(item.getFlavorName(), 0L) + item.getQuantity());
                }
            }
        }

        String topProduct = productCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Ninguno");

        String topFlavor = flavorCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Ninguno");

        Map<String, Object> stats = new HashMap<>();
        stats.put("deliveredCountToday", deliveredCount);
        stats.put("readyCount", readyCount);
        stats.put("preparingCount", preparingCount);
        stats.put("pendingCount", pendingCount);
        stats.put("cancelledCountToday", cancelledCount);
        stats.put("urgentCount", urgentCount);
        stats.put("unassignedCount", unassignedCount);
        stats.put("myDeliveredCountToday", myDeliveredCount);
        stats.put("myActiveCount", myActiveCount);
        stats.put("totalSalesToday", totalSales);
        stats.put("ordersCreatedToday", ordersToday.size());
        stats.put("topProductToday", topProduct);
        stats.put("topFlavorToday", topFlavor);

        return ResponseEntity.ok(stats);
    }
}
