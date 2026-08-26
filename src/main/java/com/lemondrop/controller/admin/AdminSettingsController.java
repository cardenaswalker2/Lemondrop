package com.lemondrop.controller.admin;

import com.lemondrop.model.AppSetting;
import com.lemondrop.security.SecurityUtils;
import com.lemondrop.service.SettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminSettingsController {

    private final SettingService settingService;

    public AdminSettingsController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/configuracion")
    public String settingsPage(Model model) {
        List<AppSetting> settings = settingService.getAllSettings();
        model.addAttribute("settings", settings);
        return "admin/configuracion";
    }

    @PostMapping("/api/configuracion/guardar")
    @ResponseBody
    public ResponseEntity<?> saveSettingApi(@RequestBody Map<String, String> payload) {
        String key = payload.get("key");
        String value = payload.get("value");
        if (key == null || key.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La clave de configuración es obligatoria."));
        }

        String actor = SecurityUtils.getCurrentUsername();
        AppSetting updated = settingService.updateSetting(key, value != null ? value.trim() : "", actor);
        return ResponseEntity.ok(updated);
    }
}
