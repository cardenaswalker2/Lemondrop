package com.lemondrop.service;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderItem;
import com.lemondrop.model.OrderItemAddon;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final OrderRepository orderRepository;

    public StatsService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Map<String, Object> getTodayStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        List<Order> todayOrders = orderRepository.findByCreatedAtBetween(startOfDay, endOfDay);

        // Sales totals
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal estimatedSales = BigDecimal.ZERO;
        int activeOrdersCount = 0;
        int completedOrdersCount = 0;
        int cancelledOrdersCount = 0;

        // Preparation time calculations
        long totalPrepMinutes = 0;
        long timedOrdersCount = 0;

        // Top analytics maps
        Map<String, Integer> productCounts = new HashMap<>();
        Map<String, Integer> flavorCounts = new HashMap<>();
        Map<String, Integer> addonCounts = new HashMap<>();

        for (Order order : todayOrders) {
            estimatedSales = estimatedSales.add(order.getTotal());
            
            if (order.getStatus() != OrderStatus.CANCELLED) {
                totalSales = totalSales.add(order.getTotal());
                activeOrdersCount++;
            } else {
                cancelledOrdersCount++;
            }

            if (order.getStatus() == OrderStatus.DELIVERED) {
                completedOrdersCount++;
            }

            // Prep time logic
            if (order.getPreparingAt() != null && order.getReadyAt() != null) {
                Duration duration = Duration.between(order.getPreparingAt(), order.getReadyAt());
                totalPrepMinutes += duration.toMinutes();
                timedOrdersCount++;
            }

            // Products, flavors, addons metrics
            for (OrderItem item : order.getItems()) {
                int qty = item.getQuantity();
                productCounts.put(item.getProductName(), productCounts.getOrDefault(item.getProductName(), 0) + qty);
                flavorCounts.put(item.getFlavorName(), flavorCounts.getOrDefault(item.getFlavorName(), 0) + qty);
                for (OrderItemAddon addon : item.getAddons()) {
                    addonCounts.put(addon.getAddonName(), addonCounts.getOrDefault(addon.getAddonName(), 0) + (addon.getQuantity() * qty));
                }
            }
        }

        // Averages
        BigDecimal averageTicket = BigDecimal.ZERO;
        if (activeOrdersCount > 0) {
            averageTicket = totalSales.divide(new BigDecimal(activeOrdersCount), 2, BigDecimal.ROUND_HALF_UP);
        }

        double avgPrepTime = 0.0;
        if (timedOrdersCount > 0) {
            avgPrepTime = (double) totalPrepMinutes / timedOrdersCount;
        }

        // Sort maps to get top sellers
        List<Map.Entry<String, Integer>> topProducts = productCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map.Entry<String, Integer>> topFlavors = flavorCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        List<Map.Entry<String, Integer>> topAddons = addonCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSales", totalSales);
        stats.put("estimatedSales", estimatedSales);
        stats.put("totalOrders", todayOrders.size());
        stats.put("activeOrders", activeOrdersCount);
        stats.put("completedOrders", completedOrdersCount);
        stats.put("cancelledOrders", cancelledOrdersCount);
        stats.put("averageTicket", averageTicket);
        stats.put("avgPrepTime", avgPrepTime);
        stats.put("topProducts", topProducts);
        stats.put("topFlavors", topFlavors);
        stats.put("topAddons", topAddons);

        return stats;
    }
}
