package com.lemondrop;

import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.*;
import com.lemondrop.repository.*;
import com.lemondrop.service.CounterService;
import com.lemondrop.service.InventoryService;
import com.lemondrop.service.NotificationService;
import com.lemondrop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class LemondropApplicationTests {

    private OrderService orderService;
    private ProductRepository productRepository;
    private FlavorRepository flavorRepository;
    private AddonRepository addonRepository;
    private OrderRepository orderRepository;
    private OrderStatusHistoryRepository statusHistoryRepository;
    private OrderChangeHistoryRepository changeHistoryRepository;
    private CounterService counterService;
    private InventoryService inventoryService;
    private NotificationService notificationService;

    private Product testProduct;
    private Flavor testFlavor;
    private Addon testAddon;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        flavorRepository = Mockito.mock(FlavorRepository.class);
        addonRepository = Mockito.mock(AddonRepository.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        statusHistoryRepository = Mockito.mock(OrderStatusHistoryRepository.class);
        changeHistoryRepository = Mockito.mock(OrderChangeHistoryRepository.class);
        counterService = Mockito.mock(CounterService.class);
        inventoryService = Mockito.mock(InventoryService.class);
        notificationService = Mockito.mock(NotificationService.class);
        org.springframework.data.mongodb.core.MongoTemplate mongoTemplate = Mockito.mock(org.springframework.data.mongodb.core.MongoTemplate.class);

        when(counterService.getNextOrderCode(anyInt())).thenReturn("LD-2026-00001");

        orderService = new OrderService(
                orderRepository,
                productRepository,
                flavorRepository,
                addonRepository,
                statusHistoryRepository,
                changeHistoryRepository,
                counterService,
                inventoryService,
                notificationService,
                mongoTemplate
        );

        Map<ProductSize, BigDecimal> prices = new HashMap<>();
        prices.put(ProductSize.SMALL, new BigDecimal("4000"));
        prices.put(ProductSize.MEDIUM, new BigDecimal("6000"));
        prices.put(ProductSize.LARGE, new BigDecimal("8000"));

        testProduct = Product.builder()
                .id("prod-1")
                .name("Test Granizado")
                .description("Test Description")
                .category("Granizados")
                .sizePrices(prices)
                .active(true)
                .available(true)
                .build();

        testFlavor = Flavor.builder()
                .id("flav-1")
                .name("Limón")
                .description("Test")
                .additionalPrice(BigDecimal.ZERO)
                .available(true)
                .build();

        testAddon = Addon.builder()
                .id("add-1")
                .name("Leche Condensada")
                .description("Test")
                .additionalPrice(new BigDecimal("1000"))
                .available(true)
                .build();

        when(productRepository.findById("prod-1")).thenReturn(Optional.of(testProduct));
        when(productRepository.findAll()).thenReturn(List.of(testProduct));
        when(flavorRepository.findById("flav-1")).thenReturn(Optional.of(testFlavor));
        when(addonRepository.findById("add-1")).thenReturn(Optional.of(testAddon));
        
        java.util.concurrent.ConcurrentHashMap<String, Order> orderDb = new java.util.concurrent.ConcurrentHashMap<>();

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            if (o.getId() == null) {
                o.setId("order-" + UUID.randomUUID().toString().substring(0, 8));
            }
            orderDb.put(o.getId(), o);
            return o;
        });

        when(orderRepository.findById(any())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.ofNullable(orderDb.get(id));
        });

        when(orderRepository.findAll()).thenAnswer(invocation -> new ArrayList<>(orderDb.values()));

        when(orderRepository.findByRequestId(any())).thenAnswer(invocation -> {
            String reqId = invocation.getArgument(0);
            return orderDb.values().stream().filter(o -> reqId != null && reqId.equals(o.getRequestId())).findFirst();
        });
    }

    @Test
    void testContextLoads() {
        assertNotNull(orderService);
    }

    @Test
    void testCreateOrderAndCalculateTotals() {
        OrderItemDto item = OrderItemDto.builder()
                .productId(testProduct.getId())
                .flavorId(testFlavor.getId())
                .size(ProductSize.MEDIUM)
                .quantity(2)
                .addonIds(Collections.singletonList(testAddon.getId()))
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerName("Ana Gomez")
                .customerPhone("3005555555")
                .observations("Sin azucar")
                .items(Collections.singletonList(item))
                .build();

        Order order = orderService.createOrder(request);

        assertNotNull(order);
        assertNotNull(order.getOrderCode());
        assertEquals("Ana Gomez", order.getCustomerName());
        assertEquals(OrderStatus.RECEIVED, order.getStatus());

        // Expected calculation:
        // Product MEDIUM price: $7000 (standard seed) or $6000 (fall-back)
        // Addon price: $1000
        // Sabor price: $0 or $500 depending on matches
        BigDecimal base = testProduct.getSizePrices().get(ProductSize.MEDIUM);
        BigDecimal expectedUnit = base.add(testFlavor.getAdditionalPrice());
        BigDecimal expectedAddons = testAddon.getAdditionalPrice();
        BigDecimal expectedTotal = expectedUnit.add(expectedAddons).multiply(new BigDecimal(2));

        assertEquals(expectedTotal, order.getTotal());
    }

    @Test
    void testUpdateOrderStatusHistory() {
        OrderItemDto item = OrderItemDto.builder()
                .productId(testProduct.getId())
                .flavorId(testFlavor.getId())
                .size(ProductSize.SMALL)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerName("Carlos Perez")
                .customerPhone("3004444444")
                .items(Collections.singletonList(item))
                .build();

        Order order = orderService.createOrder(request);
        assertNotNull(order);

        Order accepted = orderService.updateOrderStatus(order.getId(), OrderStatus.ACCEPTED, "Aceptado ok", "asesor");
        assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
        assertEquals("asesor", accepted.getLastModifiedBy());
        assertNotNull(accepted.getAcceptedAt());

        Order preparing = orderService.updateOrderStatus(order.getId(), OrderStatus.PREPARING, "Iniciando cocción", "asesor");
        assertEquals(OrderStatus.PREPARING, preparing.getStatus());
        assertNotNull(preparing.getPreparingAt());
    }

    @Test
    void testModifyOrderAdmin() {
        OrderItemDto item = OrderItemDto.builder()
                .productId(testProduct.getId())
                .flavorId(testFlavor.getId())
                .size(ProductSize.MEDIUM)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerName("Carlos Perez")
                .customerPhone("3004444444")
                .items(Collections.singletonList(item))
                .build();

        Order order = orderService.createOrder(request);
        assertNotNull(order);

        // Modify order as admin: change size to LARGE, change name, add addon
        OrderItemDto modifiedItem = OrderItemDto.builder()
                .productId(testProduct.getId())
                .flavorId(testFlavor.getId())
                .size(ProductSize.LARGE)
                .quantity(2)
                .addonIds(Collections.singletonList(testAddon.getId()))
                .build();

        Order modified = orderService.modifyOrderAdmin(
                order.getId(),
                "Carlos Perez Modificado",
                "3009999999",
                Collections.singletonList(modifiedItem),
                "ALTA",
                "asesor",
                "El cliente solicitó cambiar a tamaño grande con adicionales",
                "admin"
        );

        assertNotNull(modified);
        assertEquals("Carlos Perez Modificado", modified.getCustomerName());
        assertEquals("3009999999", modified.getCustomerPhone());
        assertEquals("ALTA", modified.getPriority());
        assertEquals("asesor", modified.getAssignedAdvisor());

        // Expected calculation:
        // Product LARGE price + flavor price + addon price * quantity 2
        BigDecimal base = testProduct.getSizePrices().get(ProductSize.LARGE);
        BigDecimal expectedUnit = base.add(testFlavor.getAdditionalPrice());
        BigDecimal expectedAddons = testAddon.getAdditionalPrice();
        BigDecimal expectedTotal = expectedUnit.add(expectedAddons).multiply(new BigDecimal(2));

        assertEquals(expectedTotal, modified.getTotal());
    }

    @Test
    void testIdempotencyConcurrencyAndLegacy() throws InterruptedException, java.util.concurrent.ExecutionException {
        // 1. Test legacy/normal order without requestId (should succeed)
        OrderItemDto item = OrderItemDto.builder()
                .productId(testProduct.getId())
                .flavorId(testFlavor.getId())
                .size(ProductSize.SMALL)
                .quantity(1)
                .build();

        CreateOrderRequest legacyRequest = CreateOrderRequest.builder()
                .customerName("Legacy Customer")
                .customerPhone("3001234567")
                .items(Collections.singletonList(item))
                .build();

        Order legacyOrder = orderService.createOrder(legacyRequest);
        assertNotNull(legacyOrder);
        assertNull(legacyOrder.getRequestId());

        // 2. Test concurrent submissions (10 simultaneous threads with SAME requestId)
        final String duplicateRequestId = "CONCURRENT-TEST-REQ-123";
        final int threadCount = 10;
        
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        List<java.util.concurrent.Future<Order>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                latch.await();
                
                CreateOrderRequest req = CreateOrderRequest.builder()
                        .customerName("Concurrent Client " + index)
                        .customerPhone("3007654321")
                        .requestId(duplicateRequestId)
                        .items(Collections.singletonList(item))
                        .build();
                return orderService.createOrder(req);
            }));
        }
        
        latch.countDown();
        
        List<Order> createdOrders = new java.util.ArrayList<>();
        for (java.util.concurrent.Future<Order> future : futures) {
            createdOrders.add(future.get());
        }
        
        executor.shutdown();
        
        String firstOrderCode = createdOrders.get(0).getOrderCode();
        String firstOrderId = createdOrders.get(0).getId();
        assertNotNull(firstOrderCode);
        assertNotNull(firstOrderId);
        
        for (Order order : createdOrders) {
            assertEquals(firstOrderId, order.getId(), "All concurrent requests with same requestId must resolve to the same Order ID");
            assertEquals(firstOrderCode, order.getOrderCode(), "All concurrent requests with same requestId must resolve to the same Order Code");
        }
        
        long countInDb = orderRepository.findAll().stream()
                .filter(o -> duplicateRequestId.equals(o.getRequestId()))
                .count();
        assertEquals(1, countInDb, "Only one order must exist in the database with the duplicate requestId");
    }
}
