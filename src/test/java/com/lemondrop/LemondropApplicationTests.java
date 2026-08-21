package com.lemondrop;

import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.*;
import com.lemondrop.repository.*;
import com.lemondrop.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LemondropApplicationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FlavorRepository flavorRepository;

    @Autowired
    private AddonRepository addonRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Product testProduct;
    private Flavor testFlavor;
    private Addon testAddon;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // Ensure we have seeded test data
        testProduct = productRepository.findByActiveTrue().stream().findFirst().orElseGet(() -> {
            Map<ProductSize, BigDecimal> prices = new HashMap<>();
            prices.put(ProductSize.SMALL, new BigDecimal("4000"));
            prices.put(ProductSize.MEDIUM, new BigDecimal("6000"));
            prices.put(ProductSize.LARGE, new BigDecimal("8000"));
            
            return productRepository.save(Product.builder()
                    .name("Test Granizado")
                    .description("Test Description")
                    .category("Granizados")
                    .sizePrices(prices)
                    .available(true)
                    .active(true)
                    .build());
        });

        testFlavor = flavorRepository.findByAvailableTrue().stream().findFirst().orElseGet(() -> 
            flavorRepository.save(Flavor.builder()
                    .name("Limón Test")
                    .description("Test")
                    .available(true)
                    .additionalPrice(BigDecimal.ZERO)
                    .build())
        );

        testAddon = addonRepository.findByAvailableTrue().stream().findFirst().orElseGet(() -> 
            addonRepository.save(Addon.builder()
                    .name("Leche Test")
                    .description("Test")
                    .available(true)
                    .additionalPrice(new BigDecimal("1000"))
                    .build())
        );
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
}
