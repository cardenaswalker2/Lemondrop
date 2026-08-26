package com.lemondrop.controller.admin;

import com.lemondrop.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminAuditController {

    private final AuditService auditService;

    public AdminAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/auditoria")
    public String auditCenterPage(Model model) {
        return "admin/auditoria";
    }

    @GetMapping("/api/auditoria/query")
    @ResponseBody
    public ResponseEntity<?> queryAuditLogs(
            @RequestParam(required = false, defaultValue = "today") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false, defaultValue = "ALL") String actionType,
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {

        Map<String, Object> result = auditService.queryUnifiedAudit(
                period, startDate, endDate, actor, actionType, orderCode, query, page, size
        );
        return ResponseEntity.ok(result);
    }
}
