package com.lemondrop.service;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderChangeHistory;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderHistoryAndTrashTest {

    private OrderService orderService;
    private OrderRepository orderRepository;
    private OrderChangeHistoryRepository changeHistoryRepository;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        changeHistoryRepository = mock(OrderChangeHistoryRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        FlavorRepository flavorRepository = mock(FlavorRepository.class);
        AddonRepository addonRepository = mock(AddonRepository.class);
        OrderStatusHistoryRepository statusHistoryRepository = mock(OrderStatusHistoryRepository.class);
        CounterService counterService = mock(CounterService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        NotificationService notificationService = mock(NotificationService.class);

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
    }

    @Test
    @DisplayName("Legacy order without 'deleted' flag (null / false) is queried and returned in active history")
    void testLegacyOrderWithoutDeletedFlag() {
        Order legacyOrder = Order.builder()
                .id("legacy-1")
                .orderCode("LD-25-00001")
                .customerName("Legacy Customer")
                .customerPhone("3000000000")
                .total(new BigDecimal("10000"))
                .status(OrderStatus.RECEIVED)
                .deleted(false) // mimics default or absent in MongoDB
                .createdAt(LocalDateTime.now().minusMonths(6))
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Order.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(List.of(legacyOrder));

        Page<Order> result = orderService.getOrdersPaginated(null, null, null, null, null, null, null, "newest", 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("LD-25-00001", result.getContent().get(0).getOrderCode());
        assertFalse(result.getContent().get(0).isDeleted());

        // Verify criteria contains { deleted: { $ne: true } }
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Order.class));
        Query executedQuery = queryCaptor.getValue();
        assertTrue(executedQuery.getQueryObject().toJson().contains("\"deleted\""));
    }

    @Test
    @DisplayName("Move order to trash sets deleted=true and persists independent audit log")
    void testMoveOrderToTrash() {
        Order order = Order.builder()
                .id("order-123")
                .orderCode("LD-26-00099")
                .customerName("Carlos")
                .deleted(false)
                .build();

        when(orderRepository.findById("order-123")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order deleted = orderService.deleteOrderLogically("order-123", "Cancelado a petición del cliente", "admin_user");

        assertTrue(deleted.isDeleted());
        assertNotNull(deleted.getDeletedAt());
        assertEquals("admin_user", deleted.getDeletedBy());
        assertEquals("Cancelado a petición del cliente", deleted.getDeletionReason());

        // Verify audit log
        ArgumentCaptor<OrderChangeHistory> historyCaptor = ArgumentCaptor.forClass(OrderChangeHistory.class);
        verify(changeHistoryRepository).save(historyCaptor.capture());
        OrderChangeHistory savedHistory = historyCaptor.getValue();
        assertEquals("MOVED_TO_TRASH", savedHistory.getPropertyName());
        assertEquals("admin_user", savedHistory.getUpdatedBy());
        assertEquals("LD-26-00099", savedHistory.getOrderCode());
    }

    @Test
    @DisplayName("Restore order sets deleted=false and persists independent audit log")
    void testRestoreOrder() {
        Order deletedOrder = Order.builder()
                .id("order-456")
                .orderCode("LD-26-00100")
                .customerName("Ana")
                .deleted(true)
                .deletedAt(LocalDateTime.now())
                .deletedBy("admin_user")
                .deletionReason("Error")
                .build();

        when(orderRepository.findById("order-456")).thenReturn(Optional.of(deletedOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order restored = orderService.restoreOrderLogically("order-456", "admin_user");

        assertFalse(restored.isDeleted());
        assertNull(restored.getDeletedAt());
        assertNull(restored.getDeletedBy());
        assertNull(restored.getDeletionReason());

        // Verify audit log
        ArgumentCaptor<OrderChangeHistory> historyCaptor = ArgumentCaptor.forClass(OrderChangeHistory.class);
        verify(changeHistoryRepository).save(historyCaptor.capture());
        OrderChangeHistory savedHistory = historyCaptor.getValue();
        assertEquals("RESTORED", savedHistory.getPropertyName());
        assertEquals("admin_user", savedHistory.getUpdatedBy());
    }

    @Test
    @DisplayName("Permanent hard delete removes Order from MongoDB while preserving OrderChangeHistory audit trail")
    void testPermanentHardDelete() {
        Order orderToDelete = Order.builder()
                .id("order-789")
                .orderCode("LD-26-00101")
                .customerName("Pedro")
                .total(new BigDecimal("15000"))
                .deleted(true)
                .build();

        when(orderRepository.findById("order-789")).thenReturn(Optional.of(orderToDelete));

        orderService.deleteOrderPermanently("order-789", "super_admin");

        // Verify audit log was recorded BEFORE hard delete
        ArgumentCaptor<OrderChangeHistory> historyCaptor = ArgumentCaptor.forClass(OrderChangeHistory.class);
        verify(changeHistoryRepository).save(historyCaptor.capture());
        OrderChangeHistory savedHistory = historyCaptor.getValue();
        assertEquals("PERMANENTLY_DELETED", savedHistory.getPropertyName());
        assertEquals("super_admin", savedHistory.getUpdatedBy());
        assertEquals("LD-26-00101", savedHistory.getOrderCode());

        // Verify hard delete
        verify(orderRepository, times(1)).deleteById("order-789");
    }
}
