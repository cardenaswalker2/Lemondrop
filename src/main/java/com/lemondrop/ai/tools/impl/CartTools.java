package com.lemondrop.ai.tools.impl;

import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.*;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.model.Addon;
import com.lemondrop.model.Flavor;
import com.lemondrop.model.Product;
import com.lemondrop.model.ProductSize;
import com.lemondrop.service.AddonService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.ProductService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CartTools {

    private final AIToolRegistry registry;
    private final ProductService productService;
    private final FlavorService flavorService;
    private final AddonService addonService;
    private final LemonAiProperties lemonAiProperties;

    public CartTools(AIToolRegistry registry,
                     ProductService productService,
                     FlavorService flavorService,
                     AddonService addonService,
                     LemonAiProperties lemonAiProperties) {
        this.registry = registry;
        this.productService = productService;
        this.flavorService = flavorService;
        this.addonService = addonService;
        this.lemonAiProperties = lemonAiProperties;
    }

    @PostConstruct
    public void registerTools() {
        registerCrearCarrito();
        registerAgregarProducto();
        registerModificarProductoCarrito();
        registerEliminarProductoCarrito();
        registerConsultarCarrito();
        registerVaciarCarrito();
    }

    private void registerCrearCarrito() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("crear_carrito")
                .description("Inicializa o reinicia el carrito de compras en la sesión actual.")
                .parametersSchema(schema)
                .executor(this::crearCarrito)
                .build());
    }

    private AIToolResult crearCarrito(Map<String, Object> args, AIConversation conv) {
        AICart cart = AICart.builder()
                .cartId("cart-" + UUID.randomUUID().toString().substring(0, 8))
                .items(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .status(AICart.CartStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(lemonAiProperties.getCartExpirationMinutes()))
                .build();

        conv.setCart(cart);
        conv.setState(ConversationState.BUILDING_ORDER);

        return AIToolResult.builder()
                .toolName("crear_carrito")
                .success(true)
                .cartModified(true)
                .data(formatCartData(cart))
                .message("Carrito creado exitosamente.")
                .build();
    }

    private void registerAgregarProducto() {
        Map<String, Object> props = new HashMap<>();
        props.put("productId", Map.of("type", "string", "description", "ID del producto o nombre (ej. 'Granizado Clásico')"));
        props.put("productName", Map.of("type", "string", "description", "Nombre aproximado del producto si no se tiene el ID"));
        props.put("flavorId", Map.of("type", "string", "description", "ID del sabor o nombre del sabor (ej. 'Mango', 'Fresa', 'Limón')"));
        props.put("flavorName", Map.of("type", "string", "description", "Nombre del sabor si no se tiene el ID"));
        props.put("size", Map.of("type", "string", "enum", List.of("SMALL", "MEDIUM", "LARGE"), "description", "Tamaño: SMALL (Pequeño), MEDIUM (Mediano), LARGE (Grande)"));
        props.put("quantity", Map.of("type", "integer", "description", "Cantidad de unidades (por defecto 1)", "default", 1));
        props.put("addonIds", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Lista de IDs o nombres de toppings/complementos (ej. ['Gomitas', 'Leche condensada'])"));
        props.put("observations", Map.of("type", "string", "description", "Notas especiales opcionales"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("size"));

        registry.register(AIToolDefinition.builder()
                .name("agregar_producto")
                .description("Agrega un granizado personalizado al carrito. Valida existencias y calcula precios oficiales.")
                .parametersSchema(schema)
                .executor(this::agregarProducto)
                .build());
    }

    private AIToolResult agregarProducto(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() == null || conv.getCart().isExpired()) {
            crearCarrito(Collections.emptyMap(), conv);
        }

        // 1. Resolve Product
        String productId = (String) args.get("productId");
        String productName = (String) args.get("productName");
        Product product = resolveProduct(productId, productName);
        if (product == null) {
            return AIToolResult.builder()
                    .toolName("agregar_producto")
                    .success(false)
                    .message("No se pudo identificar el producto en el catálogo oficial.")
                    .build();
        }
        if (!product.isActive() || !product.isAvailable()) {
            return AIToolResult.builder()
                    .toolName("agregar_producto")
                    .success(false)
                    .message("El producto " + product.getName() + " no está disponible actualmente.")
                    .build();
        }

        // 2. Resolve Size
        String sizeStr = (String) args.get("size");
        ProductSize size;
        try {
            size = ProductSize.valueOf(sizeStr != null ? sizeStr.toUpperCase() : "MEDIUM");
        } catch (Exception e) {
            size = ProductSize.MEDIUM;
        }

        BigDecimal basePrice = product.getSizePrices() != null ? product.getSizePrices().get(size) : null;
        if (basePrice == null) {
            return AIToolResult.builder()
                    .toolName("agregar_producto")
                    .success(false)
                    .message("El tamaño " + size + " no está disponible para " + product.getName())
                    .build();
        }

        // 3. Resolve Flavor
        String flavorId = (String) args.get("flavorId");
        String flavorName = (String) args.get("flavorName");
        Flavor flavor = resolveFlavor(flavorId, flavorName);
        if (flavor == null) {
            // Default to first available flavor or ask
            List<Flavor> availableFlavors = flavorService.getAvailableFlavors();
            if (!availableFlavors.isEmpty()) {
                flavor = availableFlavors.get(0);
            } else {
                return AIToolResult.builder()
                        .toolName("agregar_producto")
                        .success(false)
                        .message("No hay sabores disponibles en este momento.")
                        .build();
            }
        }
        if (!flavor.isAvailable()) {
            return AIToolResult.builder()
                    .toolName("agregar_producto")
                    .success(false)
                    .message("El sabor " + flavor.getName() + " no está disponible.")
                    .build();
        }

        BigDecimal unitPrice = basePrice.add(flavor.getAdditionalPrice() != null ? flavor.getAdditionalPrice() : BigDecimal.ZERO);

        // 4. Resolve Quantity
        int quantity = 1;
        if (args.get("quantity") instanceof Number num) {
            quantity = Math.max(1, num.intValue());
        }

        // 5. Resolve Addons/Toppings
        List<AICartItemAddon> itemAddons = new ArrayList<>();
        BigDecimal addonsTotal = BigDecimal.ZERO;

        Object addonObjs = args.get("addonIds");
        if (addonObjs instanceof List<?> list) {
            for (Object obj : list) {
                if (obj != null) {
                    Addon addon = resolveAddon(obj.toString());
                    if (addon != null && addon.isAvailable()) {
                        BigDecimal addonPrice = addon.getAdditionalPrice() != null ? addon.getAdditionalPrice() : BigDecimal.ZERO;
                        itemAddons.add(AICartItemAddon.builder()
                                .addonId(addon.getId())
                                .addonName(addon.getName())
                                .unitPrice(addonPrice)
                                .quantity(1)
                                .build());
                        addonsTotal = addonsTotal.add(addonPrice);
                    }
                }
            }
        }

        // 6. Calculate Subtotals in Backend Authority
        BigDecimal itemSubtotal = unitPrice.add(addonsTotal).multiply(BigDecimal.valueOf(quantity));

        String observations = (String) args.get("observations");

        AICartItem item = AICartItem.builder()
                .id("item-" + UUID.randomUUID().toString().substring(0, 8))
                .productId(product.getId())
                .productName(product.getName())
                .flavorId(flavor.getId())
                .flavorName(flavor.getName())
                .size(size)
                .quantity(quantity)
                .addons(itemAddons)
                .unitPrice(unitPrice)
                .addonTotal(addonsTotal)
                .subtotal(itemSubtotal)
                .observations(observations)
                .build();

        conv.getCart().getItems().add(item);
        conv.getCart().recalculateTotals();
        conv.setState(ConversationState.BUILDING_ORDER);

        return AIToolResult.builder()
                .toolName("agregar_producto")
                .success(true)
                .cartModified(true)
                .data(formatCartData(conv.getCart()))
                .message("Agregado al carrito: " + product.getName() + " (" + flavor.getName() + ", tamaño " + size.name() + "). Subtotal: $" + itemSubtotal)
                .build();
    }

    private void registerModificarProductoCarrito() {
        Map<String, Object> props = new HashMap<>();
        props.put("cartItemId", Map.of("type", "string", "description", "ID del ítem en el carrito"));
        props.put("size", Map.of("type", "string", "enum", List.of("SMALL", "MEDIUM", "LARGE"), "description", "Nuevo tamaño"));
        props.put("flavorName", Map.of("type", "string", "description", "Nuevo sabor"));
        props.put("quantity", Map.of("type", "integer", "description", "Nueva cantidad"));
        props.put("addonIds", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Lista completa de reemplazo de toppings/complementos"));
        props.put("addAddonNames", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Nombres o IDs de toppings adicionales a agregar (ej. ['Gomitas'])"));
        props.put("removeAddonNames", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Nombres o IDs de toppings a remover (ej. ['Oreo'])"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("modificar_producto_carrito")
                .description("Modifica un producto existente en el carrito: cambiar tamaño, sabor, agregar o quitar toppings específicos o cambiar cantidad.")
                .parametersSchema(schema)
                .executor(this::modificarProductoCarrito)
                .build());
    }

    private AIToolResult modificarProductoCarrito(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() == null || conv.getCart().getItems().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("modificar_producto_carrito")
                    .success(false)
                    .message("El carrito está vacío.")
                    .build();
        }

        String cartItemId = (String) args.get("cartItemId");
        AICartItem targetItem = null;

        if (cartItemId != null && !cartItemId.trim().isEmpty()) {
            targetItem = conv.getCart().getItems().stream()
                    .filter(i -> i.getId().equalsIgnoreCase(cartItemId))
                    .findFirst()
                    .orElse(null);
        }

        // If not specified or not found by ID, modify the last added item
        if (targetItem == null && !conv.getCart().getItems().isEmpty()) {
            targetItem = conv.getCart().getItems().get(conv.getCart().getItems().size() - 1);
        }

        if (targetItem == null) {
            return AIToolResult.builder()
                    .toolName("modificar_producto_carrito")
                    .success(false)
                    .message("No se encontró el ítem a modificar.")
                    .build();
        }

        Product product = productService.getById(targetItem.getProductId()).orElse(null);
        if (product == null) {
            return AIToolResult.builder()
                    .toolName("modificar_producto_carrito")
                    .success(false)
                    .message("Producto base ya no disponible.")
                    .build();
        }

        if (args.containsKey("size")) {
            String sizeStr = (String) args.get("size");
            try {
                ProductSize newSize = ProductSize.valueOf(sizeStr.toUpperCase());
                targetItem.setSize(newSize);
            } catch (Exception ignored) {}
        }

        if (args.containsKey("flavorName")) {
            Flavor newFlavor = resolveFlavor(null, (String) args.get("flavorName"));
            if (newFlavor != null && newFlavor.isAvailable()) {
                targetItem.setFlavorId(newFlavor.getId());
                targetItem.setFlavorName(newFlavor.getName());
            }
        }

        if (args.containsKey("quantity") && args.get("quantity") instanceof Number num) {
            targetItem.setQuantity(Math.max(1, num.intValue()));
        }

        // Full replacement list
        if (args.containsKey("addonIds") && args.get("addonIds") instanceof List<?> list) {
            List<AICartItemAddon> newItemAddons = new ArrayList<>();
            BigDecimal newAddonsTotal = BigDecimal.ZERO;
            for (Object obj : list) {
                if (obj != null) {
                    Addon addon = resolveAddon(obj.toString());
                    if (addon != null && addon.isAvailable()) {
                        BigDecimal addonPrice = addon.getAdditionalPrice() != null ? addon.getAdditionalPrice() : BigDecimal.ZERO;
                        newItemAddons.add(AICartItemAddon.builder()
                                .addonId(addon.getId())
                                .addonName(addon.getName())
                                .unitPrice(addonPrice)
                                .quantity(1)
                                .build());
                        newAddonsTotal = newAddonsTotal.add(addonPrice);
                    }
                }
            }
            targetItem.setAddons(newItemAddons);
            targetItem.setAddonTotal(newAddonsTotal);
        }

        // Append specific addons
        if (args.containsKey("addAddonNames") && args.get("addAddonNames") instanceof List<?> list) {
            if (targetItem.getAddons() == null) targetItem.setAddons(new ArrayList<>());
            for (Object obj : list) {
                if (obj != null) {
                    Addon addon = resolveAddon(obj.toString());
                    if (addon != null && addon.isAvailable()) {
                        boolean alreadyHas = targetItem.getAddons().stream()
                                .anyMatch(a -> a.getAddonId() != null && a.getAddonId().equals(addon.getId()));
                        if (!alreadyHas) {
                            BigDecimal addonPrice = addon.getAdditionalPrice() != null ? addon.getAdditionalPrice() : BigDecimal.ZERO;
                            targetItem.getAddons().add(AICartItemAddon.builder()
                                    .addonId(addon.getId())
                                    .addonName(addon.getName())
                                    .unitPrice(addonPrice)
                                    .quantity(1)
                                    .build());
                        }
                    }
                }
            }
        }

        // Remove specific addons
        if (args.containsKey("removeAddonNames") && args.get("removeAddonNames") instanceof List<?> list) {
            if (targetItem.getAddons() != null) {
                for (Object obj : list) {
                    if (obj != null) {
                        String target = obj.toString().toLowerCase().trim();
                        targetItem.getAddons().removeIf(a -> 
                            (a.getAddonName() != null && a.getAddonName().toLowerCase().contains(target)) ||
                            (a.getAddonId() != null && a.getAddonId().equalsIgnoreCase(target))
                        );
                    }
                }
            }
        }

        // Recalculate addon total from current list
        BigDecimal calculatedAddonsTotal = BigDecimal.ZERO;
        if (targetItem.getAddons() != null) {
            for (AICartItemAddon a : targetItem.getAddons()) {
                if (a.getUnitPrice() != null) {
                    calculatedAddonsTotal = calculatedAddonsTotal.add(a.getUnitPrice().multiply(BigDecimal.valueOf(a.getQuantity())));
                }
            }
        }
        targetItem.setAddonTotal(calculatedAddonsTotal);

        // Recalculate item unit price & subtotal
        BigDecimal basePrice = product.getSizePrices().get(targetItem.getSize());
        Flavor flavor = flavorService.getById(targetItem.getFlavorId()).orElse(null);
        BigDecimal flavorExtra = (flavor != null && flavor.getAdditionalPrice() != null) ? flavor.getAdditionalPrice() : BigDecimal.ZERO;
        BigDecimal unitPrice = basePrice.add(flavorExtra);

        targetItem.setUnitPrice(unitPrice);
        BigDecimal itemSubtotal = unitPrice.add(targetItem.getAddonTotal() != null ? targetItem.getAddonTotal() : BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(targetItem.getQuantity()));
        targetItem.setSubtotal(itemSubtotal);

        conv.getCart().recalculateTotals();

        return AIToolResult.builder()
                .toolName("modificar_producto_carrito")
                .success(true)
                .cartModified(true)
                .data(formatCartData(conv.getCart()))
                .message("Ítem modificado correctamente. Nuevo total: $" + conv.getCart().getTotal())
                .build();
    }

    private void registerEliminarProductoCarrito() {
        Map<String, Object> props = new HashMap<>();
        props.put("cartItemId", Map.of("type", "string", "description", "ID del ítem a eliminar del carrito"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("eliminar_producto_carrito")
                .description("Elimina un producto del carrito.")
                .parametersSchema(schema)
                .executor(this::eliminarProductoCarrito)
                .build());
    }

    private AIToolResult eliminarProductoCarrito(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() == null || conv.getCart().getItems().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("eliminar_producto_carrito")
                    .success(false)
                    .message("El carrito ya está vacío.")
                    .build();
        }

        String cartItemId = (String) args.get("cartItemId");
        if (cartItemId != null && !cartItemId.trim().isEmpty()) {
            conv.getCart().getItems().removeIf(i -> i.getId().equalsIgnoreCase(cartItemId));
        } else {
            // Remove the last added item
            conv.getCart().getItems().remove(conv.getCart().getItems().size() - 1);
        }

        conv.getCart().recalculateTotals();

        return AIToolResult.builder()
                .toolName("eliminar_producto_carrito")
                .success(true)
                .cartModified(true)
                .data(formatCartData(conv.getCart()))
                .message("Producto eliminado del carrito. Total restante: $" + conv.getCart().getTotal())
                .build();
    }

    private void registerConsultarCarrito() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("consultar_carrito")
                .description("Devuelve el estado y detalle actual del carrito con subtotales y total calculados por el backend.")
                .parametersSchema(schema)
                .executor(this::consultarCarrito)
                .build());
    }

    private AIToolResult consultarCarrito(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() == null || conv.getCart().isExpired()) {
            return AIToolResult.builder()
                    .toolName("consultar_carrito")
                    .success(true)
                    .data(Map.of("items", List.of(), "total", 0, "status", "EMPTY"))
                    .message("El carrito está vacío.")
                    .build();
        }

        return AIToolResult.builder()
                .toolName("consultar_carrito")
                .success(true)
                .data(formatCartData(conv.getCart()))
                .message("Carrito consultado con éxito.")
                .build();
    }

    private void registerVaciarCarrito() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());

        registry.register(AIToolDefinition.builder()
                .name("vaciar_carrito")
                .description("Vacía completamente todos los productos del carrito actual.")
                .parametersSchema(schema)
                .executor(this::vaciarCarrito)
                .build());
    }

    private AIToolResult vaciarCarrito(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() != null) {
            conv.getCart().getItems().clear();
            conv.getCart().recalculateTotals();
            conv.getCart().setStatus(AICart.CartStatus.DRAFT);
        }
        conv.setState(ConversationState.IDLE);

        return AIToolResult.builder()
                .toolName("vaciar_carrito")
                .success(true)
                .cartModified(true)
                .data(formatCartData(conv.getCart()))
                .message("Carrito vaciado correctamente.")
                .build();
    }

    private Product resolveProduct(String id, String name) {
        if (id != null && !id.trim().isEmpty()) {
            Optional<Product> p = productService.getById(id);
            if (p.isPresent()) return p.get();
        }
        List<Product> all = productService.getAllActiveAndAvailable();
        if (name != null && !name.trim().isEmpty()) {
            String lower = name.toLowerCase().trim();
            for (Product p : all) {
                if (p.getName().toLowerCase().contains(lower)) return p;
            }
        }
        return all.isEmpty() ? null : all.get(0);
    }

    private Flavor resolveFlavor(String id, String name) {
        if (id != null && !id.trim().isEmpty()) {
            Optional<Flavor> f = flavorService.getById(id);
            if (f.isPresent()) return f.get();
        }
        List<Flavor> all = flavorService.getAvailableFlavors();
        if (name != null && !name.trim().isEmpty()) {
            String lower = name.toLowerCase().trim();
            for (Flavor f : all) {
                if (f.getName().toLowerCase().contains(lower)) return f;
            }
        }
        return null;
    }

    private Addon resolveAddon(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) return null;
        Optional<Addon> opt = addonService.getById(identifier);
        if (opt.isPresent()) return opt.get();

        String lower = identifier.toLowerCase().trim();
        for (Addon a : addonService.getAvailableAddons()) {
            if (a.getName().toLowerCase().contains(lower)) return a;
        }
        return null;
    }

    private Map<String, Object> formatCartData(AICart cart) {
        if (cart == null) return Map.of("items", List.of(), "total", 0);

        List<Map<String, Object>> itemsList = cart.getItems().stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("productId", item.getProductId());
            map.put("productName", item.getProductName());
            map.put("flavorName", item.getFlavorName());
            map.put("size", item.getSize() != null ? item.getSize().name() : "MEDIUM");
            map.put("quantity", item.getQuantity());
            map.put("unitPrice", item.getUnitPrice());
            map.put("addonTotal", item.getAddonTotal());
            map.put("subtotal", item.getSubtotal());
            map.put("addons", item.getAddons().stream().map(AICartItemAddon::getAddonName).collect(Collectors.toList()));
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("cartId", cart.getCartId());
        data.put("items", itemsList);
        data.put("subtotal", cart.getSubtotal());
        data.put("total", cart.getTotal());
        data.put("totalItems", cart.getItems().size());
        data.put("status", cart.getStatus().name());
        return data;
    }
}
