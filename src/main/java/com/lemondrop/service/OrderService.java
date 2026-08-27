package com.lemondrop.service;

import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.*;
import com.lemondrop.repository.*;
import com.lemondrop.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final FlavorRepository flavorRepository;
    private final AddonRepository addonRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderChangeHistoryRepository changeHistoryRepository;
    private final CounterService counterService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    private final MongoTemplate mongoTemplate;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        FlavorRepository flavorRepository,
                        AddonRepository addonRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        OrderChangeHistoryRepository changeHistoryRepository,
                        CounterService counterService,
                        InventoryService inventoryService,
                        NotificationService notificationService,
                        MongoTemplate mongoTemplate) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.flavorRepository = flavorRepository;
        this.addonRepository = addonRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.counterService = counterService;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
        this.mongoTemplate = mongoTemplate;
    }

    public synchronized Order createOrder(CreateOrderRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            Optional<Order> existing = orderRepository.findByRequestId(request.getRequestId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        int currentYear = LocalDate.now().getYear();
        String code = counterService.getNextOrderCode(currentYear);

        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemDto itemDto : request.getItems()) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
            if (!product.isActive() || !product.isAvailable()) {
                throw new IllegalStateException("El producto " + product.getName() + " no está disponible.");
            }

            BigDecimal unitPrice = product.getSizePrices().get(itemDto.getSize());
            if (unitPrice == null) {
                throw new IllegalArgumentException("Tamaño no válido para este producto.");
            }

            Flavor flavor = flavorRepository.findById(itemDto.getFlavorId())
                    .orElseThrow(() -> new IllegalArgumentException("Sabor no encontrado."));
            if (!flavor.isAvailable()) {
                throw new IllegalStateException("El sabor " + flavor.getName() + " no está disponible.");
            }
            BigDecimal flavorExtra = flavor.getAdditionalPrice();
            unitPrice = unitPrice.add(flavorExtra);

            List<OrderItemAddon> itemAddons = new ArrayList<>();
            BigDecimal addonsTotal = BigDecimal.ZERO;

            for (String addonId : itemDto.getAddonIds()) {
                Addon addon = addonRepository.findById(addonId)
                        .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado."));
                if (!addon.isAvailable()) {
                    throw new IllegalStateException("El complemento " + addon.getName() + " no está disponible.");
                }

                OrderItemAddon itemAddon = OrderItemAddon.builder()
                        .addonId(addon.getId())
                        .addonName(addon.getName())
                        .unitPrice(addon.getAdditionalPrice())
                        .quantity(1) // default 1 per unit
                        .build();

                itemAddons.add(itemAddon);
                addonsTotal = addonsTotal.add(addon.getAdditionalPrice());
            }

            BigDecimal itemSubtotal = unitPrice.add(addonsTotal).multiply(new BigDecimal(itemDto.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .flavorId(flavor.getId())
                    .flavorName(flavor.getName())
                    .size(itemDto.getSize())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(unitPrice)
                    .addons(itemAddons)
                    .addonTotal(addonsTotal)
                    .subtotal(itemSubtotal)
                    .observations(itemDto.getObservations())
                    .build();

            orderItems.add(orderItem);
        }

        Order order = Order.builder()
                .orderCode(code)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .items(orderItems)
                .subtotal(subtotal)
                .total(subtotal) // Taxes or discounts can be added here
                .status(OrderStatus.RECEIVED)
                .observations(request.getObservations())
                .requestId(request.getRequestId())
                .createdBy("GUEST")
                .lastModifiedBy("GUEST")
                .receivedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Order saved;
        try {
            saved = orderRepository.save(order);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
                Optional<Order> existing = orderRepository.findByRequestId(request.getRequestId());
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
            throw e;
        }

        // Track History
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(saved.getId())
                .orderCode(saved.getOrderCode())
                .previousStatus(null)
                .newStatus(OrderStatus.RECEIVED)
                .updatedBy("GUEST")
                .updatedAt(LocalDateTime.now())
                .notes("Pedido ingresado por la web pública.")
                .build();
        statusHistoryRepository.save(history);

        // Notify Operational Dashboard
        notificationService.createNotification(
                "NUEVO_PEDIDO",
                "¡Nuevo Pedido recibido!",
                "Pedido " + saved.getOrderCode() + " de " + saved.getCustomerName() + " por $" + saved.getTotal()
        );

        return saved;
    }

    public synchronized Order updateOrderStatus(String id, OrderStatus newStatus, String notes, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));

        OrderStatus oldStatus = order.getStatus();
        if (oldStatus == newStatus) {
            return order;
        }

        boolean isValidTransition = false;
        if (oldStatus == null) {
            isValidTransition = true;
        } else {
            switch (oldStatus) {
                case RECEIVED:
                    isValidTransition = (newStatus == OrderStatus.ACCEPTED || newStatus == OrderStatus.CANCELLED);
                    break;
                case ACCEPTED:
                    isValidTransition = (newStatus == OrderStatus.PREPARING || newStatus == OrderStatus.CANCELLED);
                    break;
                case PREPARING:
                    isValidTransition = (newStatus == OrderStatus.ALMOST_READY || newStatus == OrderStatus.CANCELLED);
                    break;
                case ALMOST_READY:
                    isValidTransition = (newStatus == OrderStatus.READY || newStatus == OrderStatus.CANCELLED);
                    break;
                case READY:
                    isValidTransition = (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED);
                    break;
                case DELIVERED:
                case CANCELLED:
                    isValidTransition = false;
                    break;
            }
        }
        if (!isValidTransition) {
            throw new IllegalStateException("Transición de estado inválida de " + oldStatus + " a " + newStatus);
        }

        order.setStatus(newStatus);
        order.setLastModifiedBy(actor);
        order.setUpdatedAt(LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case ACCEPTED:
                order.setAcceptedAt(now);
                break;
            case PREPARING:
                order.setPreparingAt(now);
                // Deduct inventory when transitioning from ACCEPTED to PREPARING
                inventoryService.deductStockForOrder(order);
                break;
            case ALMOST_READY:
                order.setAlmostReadyAt(now);
                break;
            case READY:
                order.setReadyAt(now);
                notificationService.createNotification(
                        "PEDIDO_LISTO",
                        "¡Pedido Listo para Recoger!",
                        "El pedido " + order.getOrderCode() + " de " + order.getCustomerName() + " está listo."
                );
                break;
            case DELIVERED:
                order.setDeliveredAt(now);
                break;
            case CANCELLED:
                order.setCancelledAt(now);
                order.setCancellationReason(notes);
                notificationService.createNotification(
                        "PEDIDO_CANCELADO",
                        "Pedido Cancelado",
                        "El pedido " + order.getOrderCode() + " fue cancelado por el asesor."
                );
                break;
        }

        Order saved = orderRepository.save(order);

        // Log Status History
        OrderStatusHistory history = OrderStatusHistory.builder()
                .orderId(saved.getId())
                .orderCode(saved.getOrderCode())
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .updatedBy(actor)
                .updatedAt(now)
                .notes(notes)
                .build();
        statusHistoryRepository.save(history);

        return saved;
    }

    public synchronized Order modifyOrder(String id, List<OrderItemDto> itemsDto, String reason, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));

        List<OrderItem> newItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        // Keep track of changes for history logs
        StringBuilder changeLog = new StringBuilder();

        for (OrderItemDto itemDto : itemsDto) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
            BigDecimal unitPrice = product.getSizePrices().get(itemDto.getSize());
            Flavor flavor = flavorRepository.findById(itemDto.getFlavorId())
                    .orElseThrow(() -> new IllegalArgumentException("Sabor no encontrado."));
            
            BigDecimal unitTotal = unitPrice.add(flavor.getAdditionalPrice());

            List<OrderItemAddon> itemAddons = new ArrayList<>();
            BigDecimal addonsTotal = BigDecimal.ZERO;
            for (String addonId : itemDto.getAddonIds()) {
                Addon addon = addonRepository.findById(addonId)
                        .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado."));
                itemAddons.add(OrderItemAddon.builder()
                        .addonId(addon.getId())
                        .addonName(addon.getName())
                        .unitPrice(addon.getAdditionalPrice())
                        .quantity(1)
                        .build());
                addonsTotal = addonsTotal.add(addon.getAdditionalPrice());
            }

            BigDecimal itemSubtotal = unitTotal.add(addonsTotal).multiply(new BigDecimal(itemDto.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            newItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .flavorId(flavor.getId())
                    .flavorName(flavor.getName())
                    .size(itemDto.getSize())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(unitTotal)
                    .addons(itemAddons)
                    .addonTotal(addonsTotal)
                    .subtotal(itemSubtotal)
                    .observations(itemDto.getObservations())
                    .build());
        }

        // Compare simple fields like flavor substitutions or additions
        if (order.getItems().size() == 1 && newItems.size() == 1) {
            OrderItem oldItem = order.getItems().get(0);
            OrderItem newItem = newItems.get(0);
            if (!oldItem.getFlavorId().equals(newItem.getFlavorId())) {
                OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                        .orderId(order.getId())
                        .orderCode(order.getOrderCode())
                        .propertyName("sabor")
                        .oldValue(oldItem.getFlavorName())
                        .newValue(newItem.getFlavorName())
                        .updatedBy(actor)
                        .updatedAt(LocalDateTime.now())
                        .reason(reason)
                        .build();
                changeHistoryRepository.save(changeHistory);
            }
        } else {
            // General structure update
            OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                    .orderId(order.getId())
                    .orderCode(order.getOrderCode())
                    .propertyName("items")
                    .oldValue("Estructura anterior (" + order.getItems().size() + " items)")
                    .newValue("Nueva estructura modificada (" + newItems.size() + " items)")
                    .updatedBy(actor)
                    .updatedAt(LocalDateTime.now())
                    .reason(reason)
                    .build();
            changeHistoryRepository.save(changeHistory);
        }

        order.setItems(newItems);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        order.setLastModifiedBy(actor);
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    public Page<Order> getOrdersPaginated(String query, OrderStatus status, String advisor, String priority,
                                         String dateFilter, String startDate, String endDate, String sort,
                                         int page, int size) {
        Query mongoQuery = new Query();

        // 1. Soft delete condition: deleted != true (matches false, null, and missing field)
        mongoQuery.addCriteria(Criteria.where("deleted").ne(true));

        // 2. Query search across code, customerName, customerPhone
        if (query != null && !query.trim().isEmpty()) {
            String q = Pattern.quote(query.trim());
            mongoQuery.addCriteria(new Criteria().orOperator(
                    Criteria.where("orderCode").regex(q, "i"),
                    Criteria.where("customerName").regex(q, "i"),
                    Criteria.where("customerPhone").regex(q, "i")
            ));
        }

        // 3. Status
        if (status != null) {
            mongoQuery.addCriteria(Criteria.where("status").is(status));
        }

        // 4. Advisor
        if (advisor != null && !advisor.trim().isEmpty() && !"all".equalsIgnoreCase(advisor)) {
            mongoQuery.addCriteria(Criteria.where("assignedAdvisor").is(advisor));
        }

        // 5. Priority
        if (priority != null && !priority.trim().isEmpty() && !"all".equalsIgnoreCase(priority)) {
            mongoQuery.addCriteria(Criteria.where("priority").is(priority.toUpperCase()));
        }

        // 6. Dates filter
        applyDateFilter(mongoQuery, dateFilter, startDate, endDate, "createdAt");

        // 7. Total count before pagination using clean copy
        long total = mongoTemplate.count(Query.of(mongoQuery), Order.class);

        // 8. Sorting & Pagination
        Sort sortObj = resolveSort(sort, "createdAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);
        mongoQuery.with(pageable);

        List<Order> orders = mongoTemplate.find(mongoQuery, Order.class);
        return new PageImpl<>(orders, pageable, total);
    }

    public Page<Order> getDeletedOrdersPaginated(String query, String dateFilter, String startDate, String endDate,
                                                String sort, int page, int size) {
        Query mongoQuery = new Query();

        // Soft delete condition: deleted == true
        mongoQuery.addCriteria(Criteria.where("deleted").is(true));

        // Query search across code, customerName, customerPhone
        if (query != null && !query.trim().isEmpty()) {
            String q = Pattern.quote(query.trim());
            mongoQuery.addCriteria(new Criteria().orOperator(
                    Criteria.where("orderCode").regex(q, "i"),
                    Criteria.where("customerName").regex(q, "i"),
                    Criteria.where("customerPhone").regex(q, "i")
            ));
        }

        // Date filter on deletedAt
        applyDateFilter(mongoQuery, dateFilter, startDate, endDate, "deletedAt");

        long total = mongoTemplate.count(Query.of(mongoQuery), Order.class);
        Sort sortObj = resolveSort(sort, "deletedAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);
        mongoQuery.with(pageable);

        List<Order> orders = mongoTemplate.find(mongoQuery, Order.class);
        return new PageImpl<>(orders, pageable, total);
    }

    private void applyDateFilter(Query mongoQuery, String dateFilter, String startDate, String endDate, String dateField) {
        if (dateFilter == null || dateFilter.trim().isEmpty()) return;

        LocalDateTime start = null;
        LocalDateTime end = null;

        if ("today".equalsIgnoreCase(dateFilter)) {
            start = LocalDate.now().atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);
        } else if ("yesterday".equalsIgnoreCase(dateFilter)) {
            start = LocalDate.now().minusDays(1).atStartOfDay();
            end = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);
        } else if ("last7days".equalsIgnoreCase(dateFilter)) {
            start = LocalDate.now().minusDays(7).atStartOfDay();
            end = LocalDate.now().atTime(LocalTime.MAX);
        } else if ("custom".equalsIgnoreCase(dateFilter) && startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
            try {
                start = LocalDate.parse(startDate).atStartOfDay();
                end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        if (start != null && end != null) {
            mongoQuery.addCriteria(Criteria.where(dateField).gte(start).lte(end));
        }
    }

    private Sort resolveSort(String sort, String defaultDateField) {
        if ("oldest".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, defaultDateField).and(Sort.by(Sort.Direction.ASC, "_id"));
        } else if ("highest".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "total").and(Sort.by(Sort.Direction.DESC, defaultDateField));
        } else if ("lowest".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, "total").and(Sort.by(Sort.Direction.DESC, defaultDateField));
        } else if ("priority".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.DESC, defaultDateField)).and(Sort.by(Sort.Direction.DESC, "_id"));
        } else {
            return Sort.by(Sort.Direction.DESC, defaultDateField).and(Sort.by(Sort.Direction.DESC, "_id"));
        }
    }

    public List<Order> getAllOrders() {
        Query query = new Query(Criteria.where("deleted").ne(true));
        query.with(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id")));
        return mongoTemplate.find(query, Order.class);
    }

    public List<Order> getAllDeletedOrders() {
        Query query = new Query(Criteria.where("deleted").is(true));
        query.with(Sort.by(Sort.Direction.DESC, "deletedAt").and(Sort.by(Sort.Direction.DESC, "_id")));
        return mongoTemplate.find(query, Order.class);
    }

    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status);
    }

    public Optional<Order> getOrderById(String id) {
        return orderRepository.findById(id);
    }

    public Optional<Order> getOrderByCode(String code) {
        return orderRepository.findByOrderCode(code);
    }

    public Optional<Order> getOrderByCodeAndPhone(String code, String phone) {
        return orderRepository.findByOrderCodeAndCustomerPhone(code, phone);
    }

    public List<Order> getOrdersByPhone(String phone) {
        return orderRepository.findByCustomerPhoneOrderByCreatedAtDesc(phone);
    }

    public Optional<Order> getOrderByRequestId(String requestId) {
        return orderRepository.findByRequestId(requestId);
    }

    public synchronized Order deleteOrderLogically(String id, String reason, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        order.setDeleted(true);
        order.setDeletedAt(LocalDateTime.now());
        order.setDeletedBy(actor);
        order.setDeletionReason(reason);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("MOVED_TO_TRASH")
                .oldValue("deleted: false")
                .newValue("deleted: true")
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason(reason != null && !reason.trim().isEmpty() ? reason : "Movido a papelera")
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized Order restoreOrderLogically(String id, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        order.setDeleted(false);
        order.setDeletedAt(null);
        order.setDeletedBy(null);
        order.setDeletionReason(null);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("RESTORED")
                .oldValue("deleted: true")
                .newValue("deleted: false")
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason("Restaurado desde papelera")
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized void deleteOrderPermanently(String id, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));

        // Save independent audit trail log before physical deletion
        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("PERMANENTLY_DELETED")
                .oldValue("deleted: true (total: $" + order.getTotal() + ", cliente: " + order.getCustomerName() + ")")
                .newValue("ELIMINADO_DEFINITIVAMENTE")
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason("Eliminación física definitiva de base de datos por administrador")
                .build();
        changeHistoryRepository.save(changeHistory);

        // Physical hard delete from MongoDB
        orderRepository.deleteById(id);
    }

    public synchronized Order togglePriority(String id, String priority, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        String oldPriority = order.getPriority();
        if (oldPriority == null) oldPriority = "NORMAL";
        
        order.setPriority(priority);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("priority")
                .oldValue(oldPriority)
                .newValue(priority)
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason("Cambio de prioridad por administrador")
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized Order reassignAdvisor(String id, String newAdvisor, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        String oldAdvisor = order.getAssignedAdvisor() != null ? order.getAssignedAdvisor() : "Ninguno";
        
        order.setAssignedAdvisor(newAdvisor);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("assignedAdvisor")
                .oldValue(oldAdvisor)
                .newValue(newAdvisor)
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason("Reasignación de asesor por administrador")
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized Order claimOrder(String id, String advisor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));

        String current = order.getAssignedAdvisor();
        if (current != null && !current.trim().isEmpty() && !current.equalsIgnoreCase("Sin asignar") && !current.equalsIgnoreCase(advisor)) {
            throw new IllegalStateException("Este pedido ya fue tomado por " + current);
        }

        String oldAdvisor = current != null ? current : "Sin asignar";
        order.setAssignedAdvisor(advisor);
        order.setLastModifiedBy(advisor);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("assignedAdvisor")
                .oldValue(oldAdvisor)
                .newValue(advisor)
                .updatedBy(advisor)
                .updatedAt(LocalDateTime.now())
                .reason("Asesor tomó la comanda para preparación")
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized Order closeOrder(String id, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        order.setClosed(true);
        order.setClosedAt(LocalDateTime.now());
        order.setClosedBy(actor);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("closed")
                .oldValue("false")
                .newValue("true")
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason("Cierre del pedido")
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized Order reopenOrder(String id, String reason, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        order.setClosed(false);
        order.setReopenReason(reason);
        order.setClosedAt(null);
        order.setClosedBy(null);
        order.setUpdatedAt(LocalDateTime.now());

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("closed")
                .oldValue("true")
                .newValue("false")
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason(reason)
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public synchronized Order modifyOrderAdmin(String id, String customerName, String customerPhone, 
                                               List<OrderItemDto> itemsDto, String priority, 
                                               String assignedAdvisor, String reason, String actor) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));

        if (order.isClosed()) {
            throw new IllegalStateException("El pedido está cerrado. Debe reabrirse antes de editar.");
        }

        // Generate ANTES string
        StringBuilder oldState = new StringBuilder();
        oldState.append("Cliente: ").append(order.getCustomerName()).append(" (Tel: ").append(order.getCustomerPhone()).append(")\n");
        oldState.append("Prioridad: ").append(order.getPriority() != null ? order.getPriority() : "NORMAL").append("\n");
        oldState.append("Asesor: ").append(order.getAssignedAdvisor() != null ? order.getAssignedAdvisor() : "Ninguno").append("\n");
        oldState.append("Total: $").append(order.getTotal()).append("\n");
        oldState.append("Items:\n");
        for (OrderItem item : order.getItems()) {
            String addonsStr = item.getAddons().stream().map(a -> a.getAddonName()).collect(Collectors.joining(", "));
            oldState.append("- ").append(item.getProductName()).append(" (").append(item.getFlavorName()).append(") [")
                    .append(item.getSize()).append("] x").append(item.getQuantity()).append(" ($").append(item.getSubtotal()).append(")");
            if (!addonsStr.isEmpty()) {
                oldState.append(" + Adicionales: ").append(addonsStr);
            }
            oldState.append("\n");
        }

        // Process new items and calculate prices securely on backend
        List<OrderItem> newItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItemDto itemDto : itemsDto) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
            BigDecimal unitPrice = product.getSizePrices().get(itemDto.getSize());
            if (unitPrice == null) {
                throw new IllegalArgumentException("Tamaño no disponible para el producto.");
            }
            
            Flavor flavor = flavorRepository.findById(itemDto.getFlavorId())
                    .orElseThrow(() -> new IllegalArgumentException("Sabor no encontrado."));
            
            BigDecimal unitTotal = unitPrice.add(flavor.getAdditionalPrice());

            List<OrderItemAddon> itemAddons = new ArrayList<>();
            BigDecimal addonsTotal = BigDecimal.ZERO;
            if (itemDto.getAddonIds() != null) {
                for (String addonId : itemDto.getAddonIds()) {
                    Addon addon = addonRepository.findById(addonId)
                            .orElseThrow(() -> new IllegalArgumentException("Complemento no encontrado."));
                    itemAddons.add(OrderItemAddon.builder()
                            .addonId(addon.getId())
                            .addonName(addon.getName())
                            .unitPrice(addon.getAdditionalPrice())
                            .quantity(1)
                            .build());
                    addonsTotal = addonsTotal.add(addon.getAdditionalPrice());
                }
            }

            BigDecimal itemSubtotal = unitTotal.add(addonsTotal).multiply(new BigDecimal(itemDto.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            newItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .flavorId(flavor.getId())
                    .flavorName(flavor.getName())
                    .size(itemDto.getSize())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(unitTotal)
                    .addons(itemAddons)
                    .addonTotal(addonsTotal)
                    .subtotal(itemSubtotal)
                    .observations(itemDto.getObservations())
                    .build());
        }

        order.setCustomerName(customerName);
        order.setCustomerPhone(customerPhone);
        order.setItems(newItems);
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        order.setPriority(priority);
        order.setAssignedAdvisor(assignedAdvisor);
        order.setLastModifiedBy(actor);
        order.setUpdatedAt(LocalDateTime.now());

        // Generate DESPUES string
        StringBuilder newState = new StringBuilder();
        newState.append("Cliente: ").append(order.getCustomerName()).append(" (Tel: ").append(order.getCustomerPhone()).append(")\n");
        newState.append("Prioridad: ").append(order.getPriority()).append("\n");
        newState.append("Asesor: ").append(order.getAssignedAdvisor() != null ? order.getAssignedAdvisor() : "Ninguno").append("\n");
        newState.append("Total: $").append(order.getTotal()).append("\n");
        newState.append("Items:\n");
        for (OrderItem item : order.getItems()) {
            String addonsStr = item.getAddons().stream().map(a -> a.getAddonName()).collect(Collectors.joining(", "));
            newState.append("- ").append(item.getProductName()).append(" (").append(item.getFlavorName()).append(") [")
                    .append(item.getSize()).append("] x").append(item.getQuantity()).append(" ($").append(item.getSubtotal()).append(")");
            if (!addonsStr.isEmpty()) {
                newState.append(" + Adicionales: ").append(addonsStr);
            }
            newState.append("\n");
        }

        OrderChangeHistory changeHistory = OrderChangeHistory.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .propertyName("modificacion_admin")
                .oldValue(oldState.toString())
                .newValue(newState.toString())
                .updatedBy(actor)
                .updatedAt(LocalDateTime.now())
                .reason(reason)
                .build();
        changeHistoryRepository.save(changeHistory);

        return orderRepository.save(order);
    }

    public Map<String, Object> mapOrderToTrackingDetails(Order order) {
        if (order == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId() != null ? order.getId() : "");
        map.put("orderCode", order.getOrderCode() != null ? order.getOrderCode() : "");
        map.put("customerName", order.getCustomerName() != null ? order.getCustomerName() : "");
        map.put("customerPhone", order.getCustomerPhone() != null ? order.getCustomerPhone() : "");
        map.put("status", order.getStatus() != null ? order.getStatus().name() : "RECEIVED");
        map.put("statusDisplay", order.getStatus() != null ? order.getStatus().getDisplayName() : "Pedido Recibido");
        map.put("subtotal", order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO);
        map.put("total", order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
        map.put("observations", order.getObservations() != null ? order.getObservations() : "");
        map.put("advisorNotes", order.getAdvisorNotes() != null ? order.getAdvisorNotes() : "");
        map.put("cancellationReason", order.getCancellationReason() != null ? order.getCancellationReason() : "");
        map.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
        map.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);

        List<Map<String, Object>> itemsList = new ArrayList<>();
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("productId", item.getProductId() != null ? item.getProductId() : "");
                itemMap.put("productName", item.getProductName() != null ? item.getProductName() : "");
                itemMap.put("flavorId", item.getFlavorId() != null ? item.getFlavorId() : "");
                itemMap.put("flavorName", item.getFlavorName() != null ? item.getFlavorName() : "");
                itemMap.put("size", item.getSize() != null ? item.getSize().name() : "MEDIUM");
                itemMap.put("quantity", item.getQuantity() != null ? item.getQuantity() : 1);
                itemMap.put("unitPrice", item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);
                itemMap.put("subtotal", item.getSubtotal() != null ? item.getSubtotal() : BigDecimal.ZERO);
                itemMap.put("observations", item.getObservations() != null ? item.getObservations() : "");

                List<Map<String, Object>> addonsList = new ArrayList<>();
                if (item.getAddons() != null) {
                    for (OrderItemAddon addon : item.getAddons()) {
                        Map<String, Object> addonMap = new HashMap<>();
                        addonMap.put("addonId", addon.getAddonId() != null ? addon.getAddonId() : "");
                        addonMap.put("addonName", addon.getAddonName() != null ? addon.getAddonName() : "");
                        addonMap.put("unitPrice", addon.getUnitPrice() != null ? addon.getUnitPrice() : BigDecimal.ZERO);
                        addonMap.put("quantity", addon.getQuantity() != null ? addon.getQuantity() : 1);
                        addonsList.add(addonMap);
                    }
                }
                itemMap.put("addons", addonsList);
                itemsList.add(itemMap);
            }
        }
        map.put("items", itemsList);
        return map;
    }
}
