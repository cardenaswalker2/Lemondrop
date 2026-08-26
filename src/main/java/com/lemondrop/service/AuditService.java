package com.lemondrop.service;

import com.lemondrop.model.OrderChangeHistory;
import com.lemondrop.model.OrderStatusHistory;
import com.lemondrop.repository.OrderChangeHistoryRepository;
import com.lemondrop.repository.OrderStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuditService {

    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final OrderChangeHistoryRepository changeHistoryRepository;

    public AuditService(OrderStatusHistoryRepository statusHistoryRepository,
                        OrderChangeHistoryRepository changeHistoryRepository) {
        this.statusHistoryRepository = statusHistoryRepository;
        this.changeHistoryRepository = changeHistoryRepository;
    }

    public Map<String, Object> queryUnifiedAudit(String period, String customStart, String customEnd,
                                                 String actor, String actionType, String orderCode,
                                                 String query, int page, int size) {
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
        } else if ("all".equalsIgnoreCase(period)) {
            start = LocalDateTime.of(2020, 1, 1, 0, 0);
        } else {
            // default today
            start = LocalDate.now().atStartOfDay();
        }

        List<Map<String, Object>> unifiedLogs = new ArrayList<>();

        // 1. Fetch & Transform Status Histories
        if (actionType == null || actionType.isEmpty() || "ALL".equalsIgnoreCase(actionType) || "STATUS".equalsIgnoreCase(actionType)) {
            List<OrderStatusHistory> statusList = statusHistoryRepository.findAll();
            for (OrderStatusHistory h : statusList) {
                if (h.getUpdatedAt() == null) continue;
                if (h.getUpdatedAt().isBefore(start) || h.getUpdatedAt().isAfter(end)) continue;

                Map<String, Object> log = new HashMap<>();
                log.put("id", h.getId());
                log.put("timestamp", h.getUpdatedAt().toString());
                log.put("actor", h.getUpdatedBy() != null ? h.getUpdatedBy() : "SISTEMA");
                log.put("orderCode", h.getOrderCode() != null ? h.getOrderCode() : "");
                log.put("orderId", h.getOrderId() != null ? h.getOrderId() : "");
                log.put("category", "ESTADO");
                log.put("action", "CAMBIO DE ESTADO");
                log.put("oldValue", h.getPreviousStatus() != null ? h.getPreviousStatus().getDisplayName() : "Inicio");
                log.put("newValue", h.getNewStatus() != null ? h.getNewStatus().getDisplayName() : "");
                log.put("reason", h.getNotes() != null ? h.getNotes() : "");
                log.put("source", "OrderStatusHistory");
                unifiedLogs.add(log);
            }
        }

        // 2. Fetch & Transform Change Histories (Edits, Reassignments, Priority, etc.)
        if (actionType == null || actionType.isEmpty() || "ALL".equalsIgnoreCase(actionType) || !"STATUS".equalsIgnoreCase(actionType)) {
            List<OrderChangeHistory> changeList = changeHistoryRepository.findAll();
            for (OrderChangeHistory c : changeList) {
                if (c.getUpdatedAt() == null) continue;
                if (c.getUpdatedAt().isBefore(start) || c.getUpdatedAt().isAfter(end)) continue;

                String cat = "MODIFICACION";
                String act = "CAMBIO GENERAL";
                String prop = c.getPropertyName() != null ? c.getPropertyName().toLowerCase() : "";

                if (prop.contains("priority")) {
                    cat = "PRIORIDAD";
                    act = "CAMBIO DE PRIORIDAD";
                } else if (prop.contains("advisor") || prop.contains("assignedadvisor")) {
                    cat = "ASIGNACION";
                    act = "ASIGNACIÓN / REASIGNACIÓN";
                } else if (prop.contains("reapertura") || prop.contains("reabrir")) {
                    cat = "REAPERTURA";
                    act = "REAPERTURA DE PEDIDO";
                } else if (prop.contains("eliminacion") || prop.contains("delete")) {
                    cat = "ELIMINACION";
                    act = "ELIMINACIÓN LÓGICA";
                } else if (prop.contains("restaurar")) {
                    cat = "RESTAURACION";
                    act = "RESTAURACIÓN DE PEDIDO";
                } else if (prop.contains("modificacion_admin") || prop.contains("items") || prop.contains("sabor")) {
                    cat = "EDICION";
                    act = "EDICIÓN DE PRODUCTOS";
                }

                if (actionType != null && !actionType.isEmpty() && !"ALL".equalsIgnoreCase(actionType)) {
                    if (!actionType.equalsIgnoreCase(cat)) continue;
                }

                Map<String, Object> log = new HashMap<>();
                log.put("id", c.getId());
                log.put("timestamp", c.getUpdatedAt().toString());
                log.put("actor", c.getUpdatedBy() != null ? c.getUpdatedBy() : "SISTEMA");
                log.put("orderCode", c.getOrderCode() != null ? c.getOrderCode() : "");
                log.put("orderId", c.getOrderId() != null ? c.getOrderId() : "");
                log.put("category", cat);
                log.put("action", act);
                log.put("oldValue", c.getOldValue() != null ? c.getOldValue() : "");
                log.put("newValue", c.getNewValue() != null ? c.getNewValue() : "");
                log.put("reason", c.getReason() != null ? c.getReason() : "");
                log.put("source", "OrderChangeHistory");
                unifiedLogs.add(log);
            }
        }

        // 3. Filter by Actor
        if (actor != null && !actor.trim().isEmpty() && !"ALL".equalsIgnoreCase(actor)) {
            final String fActor = actor.trim().toLowerCase();
            unifiedLogs = unifiedLogs.stream()
                    .filter(l -> ((String) l.get("actor")).toLowerCase().contains(fActor))
                    .collect(Collectors.toList());
        }

        // 4. Filter by Order Code
        if (orderCode != null && !orderCode.trim().isEmpty()) {
            final String fCode = orderCode.trim().toLowerCase();
            unifiedLogs = unifiedLogs.stream()
                    .filter(l -> ((String) l.get("orderCode")).toLowerCase().contains(fCode))
                    .collect(Collectors.toList());
        }

        // 5. Filter by General Query
        if (query != null && !query.trim().isEmpty()) {
            final String fQ = query.trim().toLowerCase();
            unifiedLogs = unifiedLogs.stream()
                    .filter(l -> ((String) l.get("orderCode")).toLowerCase().contains(fQ)
                            || ((String) l.get("actor")).toLowerCase().contains(fQ)
                            || ((String) l.get("action")).toLowerCase().contains(fQ)
                            || ((String) l.get("reason")).toLowerCase().contains(fQ)
                            || ((String) l.get("oldValue")).toLowerCase().contains(fQ)
                            || ((String) l.get("newValue")).toLowerCase().contains(fQ))
                    .collect(Collectors.toList());
        }

        // 6. Sort Chronologically Descending (Newest first)
        unifiedLogs.sort((a, b) -> ((String) b.get("timestamp")).compareTo((String) a.get("timestamp")));

        // 7. Server-Side Pagination
        int totalElements = unifiedLogs.size();
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int fromIndex = Math.min(safePage * safeSize, totalElements);
        int toIndex = Math.min(fromIndex + safeSize, totalElements);
        List<Map<String, Object>> pagedLogs = unifiedLogs.subList(fromIndex, toIndex);

        int totalPages = (int) Math.ceil((double) totalElements / safeSize);

        Map<String, Object> response = new HashMap<>();
        response.put("content", pagedLogs);
        response.put("currentPage", safePage);
        response.put("pageSize", safeSize);
        response.put("totalElements", totalElements);
        response.put("totalPages", totalPages);
        response.put("hasNext", safePage < totalPages - 1);
        response.put("hasPrevious", safePage > 0);

        return response;
    }
}
