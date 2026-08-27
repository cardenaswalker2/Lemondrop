package com.lemondrop.controller.admin;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.model.User;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import com.lemondrop.repository.OrderRepository;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.StatsService;
import com.lemondrop.service.UserService;
import org.springframework.data.domain.Page;
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

        // Add real-time live operational overview & advisor load
        model.addAttribute("opsOverview", statsService.getLiveOperationsOverview());
        model.addAttribute("advisorsLoad", statsService.getLiveAdvisorsLoad());

        // Add recent logs for security/operation audits
        List<Order> allOrders = orderService.getAllOrders();
        model.addAttribute("recentOrders", allOrders.stream().limit(5).collect(Collectors.toList()));
        
        return "admin/dashboard";
    }

    @GetMapping("/pedidos")
    public String orderHistory(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(required = false) String query,
                               @RequestParam(required = false) OrderStatus status,
                               @RequestParam(required = false) String advisor,
                               @RequestParam(required = false) String priority,
                               @RequestParam(required = false) String dateFilter,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate,
                               @RequestParam(defaultValue = "newest") String sort,
                               Model model) {
        Page<Order> orderPage = orderService.getOrdersPaginated(
                query, status, advisor, priority, dateFilter, startDate, endDate, sort, page, size
        );

        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("currentPage", orderPage.getNumber());
        model.addAttribute("totalPages", Math.max(1, orderPage.getTotalPages()));
        model.addAttribute("totalItems", orderPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("hasPrevious", orderPage.hasPrevious());
        model.addAttribute("hasNext", orderPage.hasNext());
        model.addAttribute("isFirst", orderPage.isFirst());
        model.addAttribute("isLast", orderPage.isLast());

        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedAdvisor", advisor);
        model.addAttribute("selectedPriority", priority);
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
    public String deletedOrders(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                @RequestParam(required = false) String query,
                                @RequestParam(required = false) String dateFilter,
                                @RequestParam(required = false) String startDate,
                                @RequestParam(required = false) String endDate,
                                @RequestParam(defaultValue = "newest") String sort,
                                Model model) {
        Page<Order> deletedPage = orderService.getDeletedOrdersPaginated(
                query, dateFilter, startDate, endDate, sort, page, size
        );

        model.addAttribute("orders", deletedPage.getContent());
        model.addAttribute("orderPage", deletedPage);
        model.addAttribute("currentPage", deletedPage.getNumber());
        model.addAttribute("totalPages", Math.max(1, deletedPage.getTotalPages()));
        model.addAttribute("totalItems", deletedPage.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("hasPrevious", deletedPage.hasPrevious());
        model.addAttribute("hasNext", deletedPage.hasNext());
        model.addAttribute("isFirst", deletedPage.isFirst());
        model.addAttribute("isLast", deletedPage.isLast());
        model.addAttribute("searchQuery", query);
        model.addAttribute("dateFilter", dateFilter);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("selectedSort", sort);

        return "admin/pedidos-eliminados";
    }
}
