package com.lemondrop.repository;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends MongoRepository<Order, String> {
    Optional<Order> findByOrderCode(String orderCode);
    Optional<Order> findByOrderCodeAndCustomerPhone(String orderCode, String customerPhone);
    List<Order> findByCustomerPhone(String customerPhone);
    List<Order> findByCustomerPhoneOrderByCreatedAtDesc(String customerPhone);
    List<Order> findByStatus(OrderStatus status);
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
    Page<Order> findByStatusIn(List<OrderStatus> statuses, Pageable pageable);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Order> findAllByOrderByCreatedAtDesc();

    @Query(value = "{ 'deleted': { '$ne': true } }", sort = "{ 'createdAt': -1 }")
    List<Order> findActiveOrdersOrderByCreatedAtDesc();

    @Query(value = "{ 'deleted': { '$ne': true } }")
    Page<Order> findActiveOrders(Pageable pageable);

    @Query(value = "{ 'deleted': true }", sort = "{ 'deletedAt': -1 }")
    List<Order> findDeletedOrdersOrderByDeletedAtDesc();

    @Query(value = "{ 'deleted': true }")
    Page<Order> findDeletedOrders(Pageable pageable);

    Optional<Order> findByRequestId(String requestId);
    List<Order> findByUpdatedAtAfter(LocalDateTime since);
}
