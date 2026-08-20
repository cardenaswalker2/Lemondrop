package com.lemondrop.controller.admin;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.OrderStatusHistory;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import com.lemondrop.repository.OrderRepository;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.StatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final StatsService statsService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;

    public AdminDashboardController(StatsService statsService,
                                    OrderService orderService,
                                    OrderRepository orderRepository,
                                    OrderStatusHistoryRepository statusHistoryRepository) {
        this.statsService = statsService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Map<String, Object> stats = statsService.getTodayStats();
        model.addAllAttributes(stats);

        // Add recent logs for security/operation audits
        List<Order> allOrders = orderService.getAllOrders();
        model.addAttribute("recentOrders", allOrders.stream().limit(5).collect(Collectors.toList()));
        
        return "admin/dashboard";
    }

    @GetMapping("/pedidos")
    public String orderHistory(@RequestParam(required = false) String query,
                               @RequestParam(required = false) OrderStatus status,
                               Model model) {
        List<Order> orders = orderService.getAllOrders();

        if (status != null) {
            orders = orders.stream()
                    .filter(o -> o.getStatus() == status)
                    .collect(Collectors.toList());
        }

        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().toLowerCase();
            orders = orders.stream()
                    .filter(o -> o.getOrderCode().toLowerCase().contains(q)
                            || o.getCustomerName().toLowerCase().contains(q)
                            || o.getCustomerPhone().contains(q))
                    .collect(Collectors.toList());
        }

        model.addAttribute("orders", orders);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("searchQuery", query);
        
        return "admin/pedidos";
    }
}
