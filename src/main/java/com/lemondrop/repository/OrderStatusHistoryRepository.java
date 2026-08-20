package com.lemondrop.repository;

import com.lemondrop.model.OrderStatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface OrderStatusHistoryRepository extends MongoRepository<OrderStatusHistory, String> {
    List<OrderStatusHistory> findByOrderIdOrderByUpdatedAtAsc(String orderId);
    List<OrderStatusHistory> findByOrderCodeOrderByUpdatedAtAsc(String orderCode);
}
