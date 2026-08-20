package com.lemondrop.repository;

import com.lemondrop.model.OrderChangeHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface OrderChangeHistoryRepository extends MongoRepository<OrderChangeHistory, String> {
    List<OrderChangeHistory> findByOrderIdOrderByUpdatedAtAsc(String orderId);
    List<OrderChangeHistory> findByOrderCodeOrderByUpdatedAtAsc(String orderCode);
}
