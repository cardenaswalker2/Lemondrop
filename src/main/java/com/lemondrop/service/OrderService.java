package com.lemondrop.service;

import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.*;
import com.lemondrop.repository.*;
import com.lemondrop.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        FlavorRepository flavorRepository,
                        AddonRepository addonRepository,
                        OrderStatusHistoryRepository statusHistoryRepository,
                        OrderChangeHistoryRepository changeHistoryRepository,
                        CounterService counterService,
                        InventoryService inventoryService,
                        NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.flavorRepository = flavorRepository;
        this.addonRepository = addonRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.changeHistoryRepository = changeHistoryRepository;
        this.counterService = counterService;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }

    public Order createOrder(CreateOrderRequest request) {
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
                .createdBy("GUEST")
                .lastModifiedBy("GUEST")
                .receivedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Order saved = orderRepository.save(order);

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

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
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
}
