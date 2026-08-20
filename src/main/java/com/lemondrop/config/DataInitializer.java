package com.lemondrop.config;

import com.lemondrop.model.*;
import com.lemondrop.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final FlavorRepository flavorRepository;
    private final AddonRepository addonRepository;
    private final InventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           ProductRepository productRepository,
                           FlavorRepository flavorRepository,
                           AddonRepository addonRepository,
                           InventoryRepository inventoryRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.flavorRepository = flavorRepository;
        this.addonRepository = addonRepository;
        this.inventoryRepository = inventoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        seedUsers();
        seedFlavors();
        seedAddons();
        seedProducts();
        seedInventory();
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            // Create Admin
            User admin = User.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin"))
                    .name("Administrador Lemon Drop")
                    .phone("3001234567")
                    .role("ADMIN")
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(admin);

            // Create Advisor
            User advisor = User.builder()
                    .username("asesor")
                    .passwordHash(passwordEncoder.encode("asesor"))
                    .name("Asesor de Preparación")
                    .phone("3007654321")
                    .role("ASESOR")
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            userRepository.save(advisor);
            
            System.out.println("DataInitializer: Default users seeded successfully (admin/admin, asesor/asesor).");
        }
    }

    private void seedFlavors() {
        if (flavorRepository.count() == 0) {
            flavorRepository.save(Flavor.builder().name("Limón").description("Ácido y clásico").available(true).additionalPrice(BigDecimal.ZERO).build());
            flavorRepository.save(Flavor.builder().name("Maracuyá").description("Tropical e intenso").available(true).additionalPrice(new BigDecimal("500")).build());
            flavorRepository.save(Flavor.builder().name("Cereza").description("Dulce y refrescante").available(true).additionalPrice(new BigDecimal("500")).build());
            System.out.println("DataInitializer: Default flavors seeded successfully.");
        }
    }

    private void seedAddons() {
        if (addonRepository.count() == 0) {
            addonRepository.save(Addon.builder().name("Leche condensada").description("Toque cremoso").available(true).additionalPrice(new BigDecimal("1000")).build());
            addonRepository.save(Addon.builder().name("Arequipe").description("Toque de dulce tradicional").available(true).additionalPrice(new BigDecimal("1000")).build());
            System.out.println("DataInitializer: Default addons seeded successfully.");
        }
    }

    private void seedProducts() {
        if (productRepository.count() == 0) {
            // 1. Limon
            Map<ProductSize, BigDecimal> pricesLimon = new HashMap<>();
            pricesLimon.put(ProductSize.SMALL, new BigDecimal("5000"));
            pricesLimon.put(ProductSize.MEDIUM, new BigDecimal("7000"));
            pricesLimon.put(ProductSize.LARGE, new BigDecimal("9000"));
            
            productRepository.save(Product.builder()
                    .name("Granizado de Limón")
                    .description("Granizado tradicional elaborado con pulpa natural de limón, ideal para la sed veraniega.")
                    .image("https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&q=80&w=400")
                    .category("Granizados")
                    .sizePrices(pricesLimon)
                    .available(true)
                    .featured(true)
                    .badge("Favorito")
                    .active(true)
                    .build());

            // 2. Maracuya
            Map<ProductSize, BigDecimal> pricesMaracuya = new HashMap<>();
            pricesMaracuya.put(ProductSize.SMALL, new BigDecimal("6000"));
            pricesMaracuya.put(ProductSize.MEDIUM, new BigDecimal("8000"));
            pricesMaracuya.put(ProductSize.LARGE, new BigDecimal("10000"));
            
            productRepository.save(Product.builder()
                    .name("Granizado de Maracuyá")
                    .description("Delicioso y refrescante granizado de fruta de la pasión, con un balance perfecto entre dulce y ácido.")
                    .image("https://images.unsplash.com/photo-1546173159-315724a31696?auto=format&fit=crop&q=80&w=400")
                    .category("Granizados")
                    .sizePrices(pricesMaracuya)
                    .available(true)
                    .featured(true)
                    .badge("Más vendido")
                    .active(true)
                    .build());

            // 3. Cereza
            Map<ProductSize, BigDecimal> pricesCereza = new HashMap<>();
            pricesCereza.put(ProductSize.SMALL, new BigDecimal("6000"));
            pricesCereza.put(ProductSize.MEDIUM, new BigDecimal("8000"));
            pricesCereza.put(ProductSize.LARGE, new BigDecimal("10000"));
            
            productRepository.save(Product.builder()
                    .name("Granizado de Cereza")
                    .description("Refrescante granizado frutal sabor a cerezas del monte, dulce y vibrante.")
                    .image("https://images.unsplash.com/photo-1497534446932-c925b458314e?auto=format&fit=crop&q=80&w=400")
                    .category("Granizados")
                    .sizePrices(pricesCereza)
                    .available(true)
                    .featured(false)
                    .badge("Nuevo")
                    .active(true)
                    .build());
            
            System.out.println("DataInitializer: Default products seeded successfully.");
        }
    }

    private void seedInventory() {
        if (inventoryRepository.count() == 0) {
            inventoryRepository.save(InventoryItem.builder().name("Hielo").category("Ingredientes").quantity(new BigDecimal("50")).unit("Kg").minStock(new BigDecimal("10")).build());
            inventoryRepository.save(InventoryItem.builder().name("Limón").category("Ingredientes").quantity(new BigDecimal("15")).unit("Kg").minStock(new BigDecimal("3")).build());
            inventoryRepository.save(InventoryItem.builder().name("Maracuyá").category("Ingredientes").quantity(new BigDecimal("15")).unit("Kg").minStock(new BigDecimal("3")).build());
            inventoryRepository.save(InventoryItem.builder().name("Cereza").category("Ingredientes").quantity(new BigDecimal("10")).unit("Kg").minStock(new BigDecimal("2")).build());
            inventoryRepository.save(InventoryItem.builder().name("Azúcar").category("Ingredientes").quantity(new BigDecimal("25")).unit("Kg").minStock(new BigDecimal("5")).build());
            inventoryRepository.save(InventoryItem.builder().name("Agua").category("Ingredientes").quantity(new BigDecimal("100")).unit("L").minStock(new BigDecimal("20")).build());
            inventoryRepository.save(InventoryItem.builder().name("Leche condensada").category("Ingredientes").quantity(new BigDecimal("30")).unit("Unidades").minStock(new BigDecimal("5")).build());
            inventoryRepository.save(InventoryItem.builder().name("Arequipe").category("Ingredientes").quantity(new BigDecimal("20")).unit("Unidades").minStock(new BigDecimal("5")).build());
            inventoryRepository.save(InventoryItem.builder().name("Vasos").category("Insumos").quantity(new BigDecimal("400")).unit("Unidades").minStock(new BigDecimal("50")).build());
            inventoryRepository.save(InventoryItem.builder().name("Cucharas").category("Insumos").quantity(new BigDecimal("400")).unit("Unidades").minStock(new BigDecimal("50")).build());
            inventoryRepository.save(InventoryItem.builder().name("Servilletas").category("Insumos").quantity(new BigDecimal("800")).unit("Unidades").minStock(new BigDecimal("100")).build());
            System.out.println("DataInitializer: Initial inventory seeded successfully.");
        }
    }
}
