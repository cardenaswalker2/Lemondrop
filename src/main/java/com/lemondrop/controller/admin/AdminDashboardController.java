package com.lemondrop.controller.admin;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.User;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import com.lemondrop.repository.OrderRepository;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.StatsService;
import com.lemondrop.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final StatsService statsService;
    private final OrderService orderService;
    private final UserService userService;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;

    public AdminDashboardController(StatsService statsService,
                                    OrderService orderService,
                                    UserService userService,
                                    OrderRepository orderRepository,
                                    OrderStatusHistoryRepository statusHistoryRepository) {
        this.statsService = statsService;
        this.orderService = orderService;
        this.userService = userService;
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
                               @RequestParam(required = false) String advisor,
                               @RequestParam(required = false) String dateFilter,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               @RequestParam(required = false) String sort,
                               Model model) {
        List<Order> orders = orderService.getAllOrders();

        // 1. Status Filter
        if (status != null) {
            orders = orders.stream()
                    .filter(o -> o.getStatus() == status)
                    .collect(Collectors.toList());
        }

        // 2. Advisor Filter
        if (advisor != null && !advisor.trim().isEmpty() && !"all".equals(advisor)) {
            orders = orders.stream()
                    .filter(o -> advisor.equals(o.getAssignedAdvisor()))
                    .collect(Collectors.toList());
        }

        // 3. Date Filter
        if (dateFilter != null && !dateFilter.trim().isEmpty()) {
            LocalDateTime start = null;
            LocalDateTime end = null;
            if ("today".equals(dateFilter)) {
                start = LocalDate.now().atStartOfDay();
                end = LocalDate.now().atTime(LocalTime.MAX);
            } else if ("yesterday".equals(dateFilter)) {
                start = LocalDate.now().minusDays(1).atStartOfDay();
                end = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);
            } else if ("last7days".equals(dateFilter)) {
                start = LocalDate.now().minusDays(7).atStartOfDay();
                end = LocalDate.now().atTime(LocalTime.MAX);
            } else if ("custom".equals(dateFilter) && startDate != null && endDate != null && !startDate.isEmpty() && !endDate.isEmpty()) {
                try {
                    start = LocalDate.parse(startDate).atStartOfDay();
                    end = LocalDate.parse(endDate).atTime(LocalTime.MAX);
                } catch (Exception e) {
                    // ignore
                }
            }

            if (start != null && end != null) {
                final LocalDateTime fStart = start;
                final LocalDateTime fEnd = end;
                orders = orders.stream()
                        .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(fStart) && !o.getCreatedAt().isAfter(fEnd))
                        .collect(Collectors.toList());
            }
        }

        // 4. Query Search
        if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().toLowerCase();
            orders = orders.stream()
                    .filter(o -> (o.getOrderCode() != null && o.getOrderCode().toLowerCase().contains(q))
                            || (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(q))
                            || (o.getCustomerPhone() != null && o.getCustomerPhone().contains(q))
                            || (o.getAssignedAdvisor() != null && o.getAssignedAdvisor().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        // 5. Sorting
        if ("oldest".equals(sort)) {
            orders = orders.stream().sorted(Comparator.comparing(Order::getCreatedAt)).collect(Collectors.toList());
        } else if ("highest".equals(sort)) {
            orders = orders.stream().sorted(Comparator.comparing(Order::getTotal).reversed()).collect(Collectors.toList());
        } else if ("lowest".equals(sort)) {
            orders = orders.stream().sorted(Comparator.comparing(Order::getTotal)).collect(Collectors.toList());
        } else { // default "newest"
            orders = orders.stream().sorted(Comparator.comparing(Order::getCreatedAt).reversed()).collect(Collectors.toList());
        }

        model.addAttribute("orders", orders);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedAdvisor", advisor);
        model.addAttribute("dateFilter", dateFilter);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("searchQuery", query);

        List<User> activeAdvisors = userService.getAllUsers().stream()
                .filter(u -> "ASESOR".equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());
        model.addAttribute("advisors", activeAdvisors);
        
        return "admin/pedidos";
    }

    @GetMapping("/pedidos/eliminados")
    public String deletedOrders(Model model) {
        List<Order> deletedOrders = orderService.getAllDeletedOrders();
        model.addAttribute("orders", deletedOrders);
        return "admin/pedidos-eliminados";
    }
}
