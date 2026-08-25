package com.lemondrop.ai.tools.impl;

import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.model.Addon;
import com.lemondrop.model.Flavor;
import com.lemondrop.model.InventoryItem;
import com.lemondrop.model.Product;
import com.lemondrop.model.ProductSize;
import com.lemondrop.service.AddonService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.InventoryService;
import com.lemondrop.service.ProductService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CatalogTools {

    private final AIToolRegistry registry;
    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;
    private final InventoryService inventoryService;

    public CatalogTools(AIToolRegistry registry,
                        ProductService productService,
                        FlavorService flavorService,
                        AddonService addonService,
                        InventoryService inventoryService) {
        this.registry = registry;
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.inventoryService = inventoryService;
    }

    @PostConstruct
    public void registerTools() {
        registerBuscarProductos();
        registerObtenerCatalogo();
        registerConsultarProducto();
        registerConsultarStock();
    }

    private void registerBuscarProductos() {
        Map<String, Object> props = new HashMap<>();
        props.put("query", Map.of("description", "Término de búsqueda (ej. 'mango', 'fresa', 'limón', 'granizado')"));
        props.put("size", Map.of("description", "Tamaño deseado opcional (SMALL, MEDIUM, LARGE)"));
        props.put("category", Map.of("description", "Categoría del producto opcional"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("buscar_productos")
                .description("Busca productos, sabores y complementos en el catálogo oficial de Lemon Drop.")
                .parametersSchema(schema)
                .executor(this::buscarProductos)
                .build());
    }

    private AIToolResult buscarProductos(Map<String, Object> args, AIConversation conv) {
        String query = (String) args.getOrDefault("query", "");
        String cleanQuery = query != null ? query.toLowerCase().trim() : "";

        List<Product> allProducts = productService.getAllActiveAndAvailable();
        List<Flavor> allFlavors = flavorService.getAvailableFlavors();
        List<Addon> allAddons = addonService.getAvailableAddons();

        List<Map<String, Object>> matchedProducts = allProducts.stream()
                .filter(p -> cleanQuery.isEmpty() || p.getName().toLowerCase().contains(cleanQuery) ||
                        (p.getDescription() != null && p.getDescription().toLowerCase().contains(cleanQuery)) ||
                        (p.getCategory() != null && p.getCategory().toLowerCase().contains(cleanQuery)))
                .map(this::formatProductSummary)
                .collect(Collectors.toList());

        List<Map<String, Object>> matchedFlavors = allFlavors.stream()
                .filter(f -> cleanQuery.isEmpty() || f.getName().toLowerCase().contains(cleanQuery))
                .map(f -> Map.<String, Object>of(
                        "id", f.getId(),
                        "name", f.getName(),
                        "description", f.getDescription() != null ? f.getDescription() : "",
                        "additionalPrice", f.getAdditionalPrice()
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> matchedAddons = allAddons.stream()
                .filter(a -> cleanQuery.isEmpty() || a.getName().toLowerCase().contains(cleanQuery))
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "name", a.getName(),
                        "description", a.getDescription() != null ? a.getDescription() : "",
                        "additionalPrice", a.getAdditionalPrice()
                ))
                .collect(Collectors.toList());

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("products", matchedProducts);
        resultData.put("flavors", matchedFlavors);
        resultData.put("addons", matchedAddons);

        return AIToolResult.builder()
                .toolName("buscar_productos")
                .success(true)
                .data(resultData)
                .message("Búsqueda completada exitosamente.")
                .build();
    }

    private void registerObtenerCatalogo() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("obtener_catalogo")
                .description("Obtiene el catálogo completo de Lemon Drop: productos activos, tamaños, precios oficiales, sabores y complementos/toppings.")
                .parametersSchema(schema)
                .executor(this::obtenerCatalogo)
                .build());
    }

    private AIToolResult obtenerCatalogo(Map<String, Object> args, AIConversation conv) {
        List<Product> products = productService.getAllActiveAndAvailable();
        List<Flavor> flavors = flavorService.getAvailableFlavors();
        List<Addon> addons = addonService.getAvailableAddons();

        List<Map<String, Object>> productList = products.stream()
                .map(this::formatProductSummary)
                .collect(Collectors.toList());

        List<Map<String, Object>> flavorList = flavors.stream()
                .map(f -> Map.<String, Object>of(
                        "id", f.getId(),
                        "name", f.getName(),
                        "additionalPrice", f.getAdditionalPrice()
                ))
                .collect(Collectors.toList());

        List<Map<String, Object>> addonList = addons.stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "name", a.getName(),
                        "additionalPrice", a.getAdditionalPrice()
                ))
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("products", productList);
        data.put("flavors", flavorList);
        data.put("addons", addonList);

        return AIToolResult.builder()
                .toolName("obtener_catalogo")
                .success(true)
                .data(data)
                .message("Catálogo obtenido correctamente.")
                .build();
    }

    private void registerConsultarProducto() {
        Map<String, Object> props = new HashMap<>();
        props.put("productId", Map.of("description", "ID único del producto"));
        props.put("productName", Map.of("description", "Nombre aproximado o exacto del producto"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("consultar_producto")
                .description("Consulta el detalle de un producto específico, incluyendo sus precios oficiales por tamaño y descripción.")
                .parametersSchema(schema)
                .executor(this::consultarProducto)
                .build());
    }

    private AIToolResult consultarProducto(Map<String, Object> args, AIConversation conv) {
        String productId = (String) args.get("productId");
        String productName = (String) args.get("productName");

        Optional<Product> optProduct = Optional.empty();
        if (productId != null && !productId.trim().isEmpty()) {
            optProduct = productService.getById(productId);
        } else if (productName != null && !productName.trim().isEmpty()) {
            String target = productName.toLowerCase().trim();
            optProduct = productService.getAllActiveAndAvailable().stream()
                    .filter(p -> p.getName().toLowerCase().contains(target))
                    .findFirst();
        }

        if (optProduct.isEmpty()) {
            return AIToolResult.builder()
                    .toolName("consultar_producto")
                    .success(false)
                    .message("No se encontró el producto en el catálogo.")
                    .build();
        }

        Product product = optProduct.get();
        return AIToolResult.builder()
                .toolName("consultar_producto")
                .success(true)
                .data(formatProductSummary(product))
                .message("Producto encontrado: " + product.getName())
                .build();
    }

    private void registerConsultarStock() {
        Map<String, Object> props = new HashMap<>();
        props.put("itemName", Map.of("description", "Nombre del ingrediente, fruta o insumo a verificar"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("consultar_stock")
                .description("Verifica la disponibilidad real de insumos y stock en el inventario.")
                .parametersSchema(schema)
                .executor(this::consultarStock)
                .build());
    }

    private AIToolResult consultarStock(Map<String, Object> args, AIConversation conv) {
        String itemName = (String) args.get("itemName");
        List<InventoryItem> items = inventoryService.getAll();

        if (itemName != null && !itemName.trim().isEmpty()) {
            String target = itemName.toLowerCase().trim();
            List<Map<String, Object>> matches = items.stream()
                    .filter(i -> i.getName().toLowerCase().contains(target))
                    .map(i -> Map.<String, Object>of(
                            "name", i.getName(),
                            "quantity", i.getQuantity(),
                            "unit", i.getUnit(),
                            "available", i.getQuantity().compareTo(BigDecimal.ZERO) > 0
                    ))
                    .collect(Collectors.toList());

            return AIToolResult.builder()
                    .toolName("consultar_stock")
                    .success(true)
                    .data(matches)
                    .message("Consulta de stock realizada.")
                    .build();
        }

        // Return summary of low or out of stock items
        List<Map<String, Object>> stockSummary = items.stream()
                .map(i -> Map.<String, Object>of(
                        "name", i.getName(),
                        "quantity", i.getQuantity(),
                        "unit", i.getUnit(),
                        "available", i.getQuantity().compareTo(BigDecimal.ZERO) > 0
                ))
                .collect(Collectors.toList());

        return AIToolResult.builder()
                .toolName("consultar_stock")
                .success(true)
                .data(stockSummary)
                .message("Resumen de stock obtenido.")
                .build();
    }

    private Map<String, Object> formatProductSummary(Product p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("name", p.getName());
        map.put("description", p.getDescription() != null ? p.getDescription() : "");
        map.put("category", p.getCategory() != null ? p.getCategory() : "Granizados");
        map.put("badge", p.getBadge() != null ? p.getBadge() : "");
        map.put("featured", p.isFeatured());
        map.put("available", p.isAvailable());

        Map<String, Object> prices = new HashMap<>();
        if (p.getSizePrices() != null) {
            p.getSizePrices().forEach((size, price) -> prices.put(size.name(), price));
        }
        map.put("prices", prices);
        return map;
    }
}
