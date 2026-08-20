package com.lemondrop.controller.guest;

import com.lemondrop.model.Order;
import com.lemondrop.model.OrderStatusHistory;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import com.lemondrop.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
public class OrderTrackingController {

    private final OrderService orderService;
    private final OrderStatusHistoryRepository statusHistoryRepository;

    public OrderTrackingController(OrderService orderService, OrderStatusHistoryRepository statusHistoryRepository) {
        this.orderService = orderService;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @GetMapping("/pedido/seguimiento")
    public String trackingPage(@RequestParam(required = false) String code,
                               @RequestParam(required = false) String phone,
                               Model model) {
        boolean hasCode = code != null && !code.trim().isEmpty();
        boolean hasPhone = phone != null && !phone.trim().isEmpty();

        if (hasCode && hasPhone) {
            Optional<Order> orderOpt = orderService.getOrderByCodeAndPhone(code.trim().toUpperCase(), phone.trim());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(order.getId());
                model.addAttribute("order", order);
                model.addAttribute("history", history);
            } else {
                model.addAttribute("error", "No se encontró ningún pedido con el código y número de WhatsApp ingresados.");
            }
            model.addAttribute("searchedCode", code.trim());
            model.addAttribute("searchedPhone", phone.trim());
        } else if (hasCode) {
            Optional<Order> orderOpt = orderService.getOrderByCode(code.trim().toUpperCase());
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(order.getId());
                model.addAttribute("order", order);
                model.addAttribute("history", history);
                model.addAttribute("searchedPhone", order.getCustomerPhone());
            } else {
                model.addAttribute("error", "No se encontró ningún pedido con el código ingresado.");
            }
            model.addAttribute("searchedCode", code.trim());
        } else if (hasPhone) {
            List<Order> orders = orderService.getOrdersByPhone(phone.trim());
            if (!orders.isEmpty()) {
                if (orders.size() == 1) {
                    Order order = orders.get(0);
                    List<OrderStatusHistory> history = statusHistoryRepository.findByOrderIdOrderByUpdatedAtAsc(order.getId());
                    model.addAttribute("order", order);
                    model.addAttribute("history", history);
                    model.addAttribute("searchedCode", order.getOrderCode());
                } else {
                    model.addAttribute("orders", orders);
                }
            } else {
                model.addAttribute("error", "No se encontró ningún pedido asociado a ese número de WhatsApp.");
            }
            model.addAttribute("searchedPhone", phone.trim());
        }
        return "public/seguimiento";
    }

    @GetMapping("/pedido/seguimiento/{code}")
    public String trackingPageByPath(@PathVariable String code,
                                     @RequestParam(required = false) String phone,
                                     Model model) {
        return trackingPage(code, phone, model);
    }
}
