package com.lemondrop.service;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderItem;
import com.lemondrop.model.OrderItemAddon;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.User;
import com.lemondrop.repository.OrderRepository;
import com.lemondrop.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public StatsService(OrderRepository orderRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> getTodayStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return calculateStatsForRange(startOfDay, endOfDay);
    }

    public Map<String, Object> getPeriodStats(String period, String customStart, String customEnd) {
        LocalDateTime start;
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);

        if ("yesterday".equalsIgnoreCase(period)) {
            start = LocalDate.now().minusDays(1).atStartOfDay();
            end = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);
        } else if ("last7days".equalsIgnoreCase(period)) {
            start = LocalDate.now().minusDays(7).atStartOfDay();
        } else if ("last30days".equalsIgnoreCase(period)) {
            start = LocalDate.now().minusDays(30).atStartOfDay();
        } else if ("custom".equalsIgnoreCase(period) && customStart != null && customEnd != null && !customStart.isEmpty() && !customEnd.isEmpty()) {
            try {
                start = LocalDate.parse(customStart).atStartOfDay();
                end = LocalDate.parse(customEnd).atTime(LocalTime.MAX);
            } catch (Exception e) {
                start = LocalDate.now().atStartOfDay();
            }
        } else {
            // default today
            start = LocalDate.now().atStartOfDay();
        }

        return calculateStatsForRange(start, end);
    }

    private Map<String, Object> calculateStatsForRange(LocalDateTime start, LocalDateTime end) {
        List<Order> orders = orderRepository.findByCreatedAtBetween(start, end);

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal estimatedSales = BigDecimal.ZERO;
        int activeOrdersCount = 0;
        int completedOrdersCount = 0;
        int cancelledOrdersCount = 0;

        long totalPrepMinutes = 0;
        long timedOrdersCount = 0;

        Map<String, Integer> productCounts = new HashMap<>();
        Map<String, Integer> flavorCounts = new HashMap<>();
        Map<String, Integer> addonCounts = new HashMap<>();

        for (Order order : orders) {
            estimatedSales = estimatedSales.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
            
            if (order.getStatus() != OrderStatus.CANCELLED) {
                totalSales = totalSales.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
                activeOrdersCount++;
            } else {
                cancelledOrdersCount++;
            }

            if (order.getStatus() == OrderStatus.DELIVERED) {
                completedOrdersCount++;
            }

            if (order.getPreparingAt() != null && order.getReadyAt() != null) {
                Duration duration = Duration.between(order.getPreparingAt(), order.getReadyAt());
                totalPrepMinutes += duration.toMinutes();
                timedOrdersCount++;
            }

            if (order.getItems() != null) {
                for (OrderItem item : order.getItems()) {
                    int qty = item.getQuantity();
                    productCounts.put(item.getProductName(), productCounts.getOrDefault(item.getProductName(), 0) + qty);
                    flavorCounts.put(item.getFlavorName(), flavorCounts.getOrDefault(item.getFlavorName(), 0) + qty);
                    if (item.getAddons() != null) {
                        for (OrderItemAddon addon : item.getAddons()) {
                            addonCounts.put(addon.getAddonName(), addonCounts.getOrDefault(addon.getAddonName(), 0) + (addon.getQuantity() * qty));
                        }
                    }
                }
            }
        }

        BigDecimal averageTicket = BigDecimal.ZERO;
        if (activeOrdersCount > 0) {
            averageTicket = totalSales.divide(new BigDecimal(activeOrdersCount), 2, RoundingMode.HALF_UP);
        }

        double avgPrepTime = 0.0;
        if (timedOrdersCount > 0) {
            avgPrepTime = (double) totalPrepMinutes / timedOrdersCount;
        }

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
        stats.put("totalOrders", orders.size());
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

    public List<Map<String, Object>> getLiveAdvisorsLoad() {
        List<User> advisors = userRepository.findAll().stream()
                .filter(u -> "ASESOR".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());

        List<Order> activeOrders = orderRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() != OrderStatus.DELIVERED && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<Order> todayDelivered = orderRepository.findByCreatedAtBetween(startOfToday, LocalDateTime.now()).stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();

        for (User advisor : advisors) {
            String username = advisor.getUsername();
            List<Order> myActive = activeOrders.stream()
                    .filter(o -> username.equalsIgnoreCase(o.getAssignedAdvisor()))
                    .collect(Collectors.toList());

            long urgentCount = myActive.stream().filter(o -> "ALTA".equalsIgnoreCase(o.getPriority())).count();
            long preparingCount = myActive.stream().filter(o -> o.getStatus() == OrderStatus.PREPARING).count();
            long readyCount = myActive.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            
            long completedTodayCount = todayDelivered.stream()
                    .filter(o -> username.equalsIgnoreCase(o.getAssignedAdvisor()) || username.equalsIgnoreCase(o.getLastModifiedBy()))
                    .count();

            Map<String, Object> map = new HashMap<>();
            map.put("id", advisor.getId());
            map.put("name", advisor.getName());
            map.put("username", advisor.getUsername());
            map.put("active", advisor.isActive());
            map.put("currentOrdersCount", myActive.size());
            map.put("urgentCount", urgentCount);
            map.put("preparingCount", preparingCount);
            map.put("readyCount", readyCount);
            map.put("completedTodayCount", completedTodayCount);
            map.put("isOverloaded", myActive.size() >= 5);

            result.add(map);
        }

        // Sort by current load descending
        result.sort((a, b) -> ((Integer) b.get("currentOrdersCount")).compareTo((Integer) a.get("currentOrdersCount")));
        return result;
    }

    public Map<String, Object> getLiveOperationsOverview() {
        List<Order> activeOrders = orderRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(o -> o.getStatus() != OrderStatus.DELIVERED && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();

        long receivedCount = activeOrders.stream().filter(o -> o.getStatus() == OrderStatus.RECEIVED).count();
        long acceptedCount = activeOrders.stream().filter(o -> o.getStatus() == OrderStatus.ACCEPTED).count();
        long preparingCount = activeOrders.stream().filter(o -> o.getStatus() == OrderStatus.PREPARING).count();
        long almostReadyCount = activeOrders.stream().filter(o -> o.getStatus() == OrderStatus.ALMOST_READY).count();
        long readyCount = activeOrders.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
        long unassignedCount = activeOrders.stream().filter(o -> o.getAssignedAdvisor() == null || o.getAssignedAdvisor().trim().isEmpty() || "Sin asignar".equalsIgnoreCase(o.getAssignedAdvisor())).count();
        long urgentCount = activeOrders.stream().filter(o -> "ALTA".equalsIgnoreCase(o.getPriority())).count();
        
        long delayedOrdersCount = activeOrders.stream().filter(o -> {
            if (o.getCreatedAt() == null) return false;
            return Duration.between(o.getCreatedAt(), now).toMinutes() >= 15;
        }).count();

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalActive", activeOrders.size());
        overview.put("received", receivedCount);
        overview.put("accepted", acceptedCount);
        overview.put("preparing", preparingCount);
        overview.put("almostReady", almostReadyCount);
        overview.put("ready", readyCount);
        overview.put("unassigned", unassignedCount);
        overview.put("urgent", urgentCount);
        overview.put("delayed", delayedOrdersCount);

        return overview;
    }
}

