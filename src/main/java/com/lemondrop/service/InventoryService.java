package com.lemondrop.service;

import com.lemondrop.model.InventoryItem;
import com.lemondrop.model.Order;
import com.lemondrop.model.OrderItem;
import com.lemondrop.model.OrderItemAddon;
import com.lemondrop.repository.InventoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final NotificationService notificationService;

    public InventoryService(InventoryRepository inventoryRepository, NotificationService notificationService) {
        this.inventoryRepository = inventoryRepository;
        this.notificationService = notificationService;
    }

    public List<InventoryItem> getAll() {
        return inventoryRepository.findAll();
    }

    public Optional<InventoryItem> getById(String id) {
        return inventoryRepository.findById(id);
    }

    public InventoryItem save(InventoryItem item) {
        return inventoryRepository.save(item);
    }

    public synchronized void deductStockForOrder(Order order) {
        // Recipe execution logic per order item
        for (OrderItem item : order.getItems()) {
            int qty = item.getQuantity();
            
            // Deduct base ingredients: Hielo, Vasos, Cucharas, Servilletas, Agua, Azúcar
            deductIngredient("Hielo", new BigDecimal("0.3").multiply(new BigDecimal(qty)));
            deductIngredient("Vasos", new BigDecimal("1").multiply(new BigDecimal(qty)));
            deductIngredient("Cucharas", new BigDecimal("1").multiply(new BigDecimal(qty)));
            deductIngredient("Servilletas", new BigDecimal("2").multiply(new BigDecimal(qty)));
            deductIngredient("Agua", new BigDecimal("0.2").multiply(new BigDecimal(qty)));
            deductIngredient("Azúcar", new BigDecimal("0.05").multiply(new BigDecimal(qty)));

            // Deduct flavor pulp
            String flavor = item.getFlavorName();
            if (flavor != null) {
                if (flavor.equalsIgnoreCase("Limón")) {
                    deductIngredient("Limón", new BigDecimal("0.1").multiply(new BigDecimal(qty)));
                } else if (flavor.equalsIgnoreCase("Maracuyá")) {
                    deductIngredient("Maracuyá", new BigDecimal("0.1").multiply(new BigDecimal(qty)));
                } else if (flavor.equalsIgnoreCase("Cereza")) {
                    deductIngredient("Cereza", new BigDecimal("0.08").multiply(new BigDecimal(qty)));
                }
            }

            // Deduct addons
            for (OrderItemAddon addon : item.getAddons()) {
                int addonQty = addon.getQuantity() * qty;
                if (addon.getAddonName().equalsIgnoreCase("Leche condensada")) {
                    deductIngredient("Leche condensada", new BigDecimal(addonQty));
                } else if (addon.getAddonName().equalsIgnoreCase("Arequipe")) {
                    deductIngredient("Arequipe", new BigDecimal(addonQty));
                }
            }
        }
    }

    private void deductIngredient(String name, BigDecimal amount) {
        Optional<InventoryItem> optItem = inventoryRepository.findByNameIgnoreCase(name);
        if (optItem.isPresent()) {
            InventoryItem item = optItem.get();
            BigDecimal newQty = item.getQuantity().subtract(amount);
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                newQty = BigDecimal.ZERO;
            }
            item.setQuantity(newQty);
            inventoryRepository.save(item);

            // Trigger alerts if low or out of stock
            if (newQty.compareTo(BigDecimal.ZERO) == 0) {
                notificationService.createNotification(
                        "PRODUCTO_AGOTADO",
                        "¡Insumo Agotado!",
                        "El insumo " + item.getName() + " se ha agotado por completo."
                );
            } else if (newQty.compareTo(item.getMinStock()) <= 0) {
                notificationService.createNotification(
                        "STOCK_BAJO",
                        "¡Stock Bajo!",
                        "El insumo " + item.getName() + " está por debajo del stock mínimo (" + newQty + " " + item.getUnit() + " restantes)."
                );
            }
        }
    }
}
