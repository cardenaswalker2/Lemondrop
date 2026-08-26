package com.lemondrop.service;

import com.lemondrop.model.AppSetting;
import com.lemondrop.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SettingService {

    private final AppSettingRepository settingRepository;

    public SettingService(AppSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @PostConstruct
    public void initDefaultSettings() {
        createIfMissing("business_name", "Lemon Drop", "Nombre del Negocio", "Nombre público visible de la marca", "GENERAL");
        createIfMissing("business_phone", "3000000000", "Teléfono Principal", "Línea de atención de pedidos", "GENERAL");
        createIfMissing("whatsapp_country_code", "57", "Código de País", "Prefijo internacional para WhatsApp (ej: 57)", "NOTIFICATIONS");
        createIfMissing("whatsapp_auto_notifications", "true", "Notificaciones de WhatsApp", "Habilitar generación de enlaces y avisos directos", "NOTIFICATIONS");
        createIfMissing("target_prep_time_minutes", "12", "Tiempo Objetivo de Cocina (min)", "Minutos estándar antes de considerar una orden demorada", "OPERATIONS");
        createIfMissing("critical_delay_minutes", "15", "Umbral Alerta Crítica (min)", "Minutos de espera para disparar alertas de cuello de botella", "OPERATIONS");
        createIfMissing("max_orders_per_advisor", "5", "Límite Recomendado por Asesor", "Capacidad máxima antes de marcar al asesor como saturado", "OPERATIONS");
    }

    private void createIfMissing(String key, String value, String label, String description, String category) {
        if (settingRepository.findByKey(key).isEmpty()) {
            AppSetting setting = AppSetting.builder()
                    .key(key)
                    .value(value)
                    .label(label)
                    .description(description)
                    .category(category)
                    .updatedBy("SYSTEM")
                    .updatedAt(LocalDateTime.now())
                    .build();
            settingRepository.save(setting);
        }
    }

    public List<AppSetting> getAllSettings() {
        return settingRepository.findAll();
    }

    public String getSettingValue(String key, String defaultValue) {
        return settingRepository.findByKey(key)
                .map(AppSetting::getValue)
                .filter(v -> v != null && !v.trim().isEmpty())
                .orElse(defaultValue);
    }

    public int getSettingInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getSettingValue(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getSettingBool(String key, boolean defaultValue) {
        String val = getSettingValue(key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    public AppSetting updateSetting(String key, String value, String updatedBy) {
        AppSetting setting = settingRepository.findByKey(key)
                .orElse(AppSetting.builder().key(key).category("CUSTOM").build());
        
        setting.setValue(value);
        setting.setUpdatedBy(updatedBy != null ? updatedBy : "ADMIN");
        setting.setUpdatedAt(LocalDateTime.now());
        return settingRepository.save(setting);
    }

    public Map<String, String> getSettingsAsMap() {
        Map<String, String> map = new HashMap<>();
        for (AppSetting s : settingRepository.findAll()) {
            map.put(s.getKey(), s.getValue());
        }
        return map;
    }
}
