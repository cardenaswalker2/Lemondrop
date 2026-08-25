package com.lemondrop.ai.tools.impl;

import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.AICart;
import com.lemondrop.ai.model.AICartItem;
import com.lemondrop.ai.model.AICartItemAddon;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.model.ConversationState;
import com.lemondrop.ai.tools.AIToolDefinition;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.dto.order.CreateOrderRequest;
import com.lemondrop.dto.order.OrderItemDto;
import com.lemondrop.model.Order;
import com.lemondrop.model.OrderItem;
import com.lemondrop.model.OrderItemAddon;
import com.lemondrop.model.OrderStatus;
import com.lemondrop.service.OrderService;
import com.lemondrop.service.WhatsAppService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class OrderTools {

    private final AIToolRegistry registry;
    private final OrderService orderService;
    private final WhatsAppService whatsAppService;

    public OrderTools(AIToolRegistry registry,
                      OrderService orderService,
                      WhatsAppService whatsAppService) {
        this.registry = registry;
        this.orderService = orderService;
        this.whatsAppService = whatsAppService;
    }

    @PostConstruct
    public void registerTools() {
        registerCrearBorradorPedido();
        registerConfirmarPedido();
        registerConsultarPedido();
        registerCancelarPedido();
        registerRepetirUltimoPedido();
    }

    private void registerCrearBorradorPedido() {
        Map<String, Object> props = new HashMap<>();
        props.put("customerName", Map.of("type", "string", "description", "Nombre del cliente"));
        props.put("customerPhone", Map.of("type", "string", "description", "Teléfono de contacto del cliente (7 a 15 dígitos)"));
        props.put("observations", Map.of("type", "string", "description", "Observaciones o instrucciones de entrega/recogida"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("crear_borrador_pedido")
                .description("Prepara un borrador de pedido con el contenido del carrito y solicita confirmación explícita al usuario.")
                .parametersSchema(schema)
                .executor(this::crearBorradorPedido)
                .build());
    }

    private AIToolResult crearBorradorPedido(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() == null || conv.getCart().getItems().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("crear_borrador_pedido")
                    .success(false)
                    .message("No se puede crear un borrador de pedido con el carrito vacío.")
                    .build();
        }

        String customerName = (String) args.get("customerName");
        String customerPhone = (String) args.get("customerPhone");
        String observations = (String) args.get("observations");

        if (customerName != null && !customerName.trim().isEmpty()) {
            conv.setCustomerName(customerName.trim());
        }
        if (customerPhone != null && !customerPhone.trim().isEmpty()) {
            conv.setCustomerPhone(customerPhone.trim());
        }

        conv.setState(ConversationState.WAITING_CONFIRMATION);

        Map<String, Object> draftData = new HashMap<>();
        draftData.put("items", conv.getCart().getItems());
        draftData.put("total", conv.getCart().getTotal());
        draftData.put("customerName", conv.getCustomerName());
        draftData.put("customerPhone", conv.getCustomerPhone());
        draftData.put("observations", observations);

        return AIToolResult.builder()
                .toolName("crear_borrador_pedido")
                .success(true)
                .requiresConfirmation(true)
                .data(draftData)
                .message("Borrador armado. Total: $" + conv.getCart().getTotal() + ". Requiere confirmación explícita del cliente.")
                .build();
    }

    private void registerConfirmarPedido() {
        Map<String, Object> props = new HashMap<>();
        props.put("customerName", Map.of("type", "string", "description", "Nombre del cliente"));
        props.put("customerPhone", Map.of("type", "string", "description", "Teléfono del cliente (7 a 15 dígitos numéricos)"));
        props.put("observations", Map.of("type", "string", "description", "Observaciones del pedido"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("confirmar_pedido")
                .description("Confirma y crea el pedido formal definitivo en el sistema Lemon Drop cuando el cliente lo ha autorizado.")
                .parametersSchema(schema)
                .executor(this::confirmarPedido)
                .build());
    }

    private AIToolResult confirmarPedido(Map<String, Object> args, AIConversation conv) {
        if (conv.getCart() == null || conv.getCart().getItems().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("confirmar_pedido")
                    .success(false)
                    .message("El carrito está vacío. Agrega productos antes de confirmar.")
                    .build();
        }

        String name = (String) args.get("customerName");
        if (name == null || name.trim().isEmpty()) {
            name = conv.getCustomerName();
        }
        if (name == null || name.trim().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("confirmar_pedido")
                    .success(false)
                    .message("Necesito tu nombre para completar el pedido 😊")
                    .build();
        }
        name = name.trim();

        String phone = (String) args.get("customerPhone");
        if (phone == null || phone.trim().isEmpty()) {
            phone = conv.getCustomerPhone();
        }
        if (phone == null || phone.trim().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("confirmar_pedido")
                    .success(false)
                    .message("Solo me falta tu número de teléfono para terminar el pedido 📱")
                    .build();
        }
        
        String cleanPhone = phone.replaceAll("[^0-9]", "");
        if (cleanPhone.startsWith("57") && cleanPhone.length() == 12) {
            cleanPhone = cleanPhone.substring(2);
        }
        if (cleanPhone.length() < 7 || cleanPhone.length() > 15) {
            return AIToolResult.builder()
                    .toolName("confirmar_pedido")
                    .success(false)
                    .message("Por favor ingresa un número de teléfono válido (entre 7 y 15 dígitos) 📱")
                    .build();
        }

        conv.setCustomerName(name);
        conv.setCustomerPhone(cleanPhone);

        String obs = (String) args.get("observations");

        // Convert AICart to CreateOrderRequest
        List<OrderItemDto> itemDtos = conv.getCart().getItems().stream().map(item -> {
            List<String> addonIds = item.getAddons() != null ?
                    item.getAddons().stream().map(a -> a.getAddonId()).filter(Objects::nonNull).collect(Collectors.toList())
                    : new ArrayList<>();

            return OrderItemDto.builder()
                    .productId(item.getProductId())
                    .flavorId(item.getFlavorId())
                    .size(item.getSize())
                    .quantity(item.getQuantity())
                    .addonIds(addonIds)
                    .observations(item.getObservations())
                    .build();
        }).collect(Collectors.toList());

        // Idempotency: Use conversationId + cartId
        String requestId = "AI-" + conv.getConversationId() + "-" + conv.getCart().getCartId();

        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .customerName(name)
                .customerPhone(cleanPhone)
                .observations(obs)
                .requestId(requestId)
                .items(itemDtos)
                .build();

        try {
            // Execute real Order creation via existing OrderService
            Order order = orderService.createOrder(orderRequest);

            conv.getCart().setStatus(AICart.CartStatus.CONFIRMED);
            conv.setState(ConversationState.ORDER_CONFIRMED);
            conv.setConfirmedOrderCode(order.getOrderCode());

            String whatsAppUrl = whatsAppService.generateWhatsAppUrl(order);

            Map<String, Object> data = new HashMap<>();
            data.put("orderCode", order.getOrderCode());
            data.put("customerName", order.getCustomerName());
            data.put("customerPhone", order.getCustomerPhone());
            data.put("total", order.getTotal());
            data.put("status", order.getStatus().name());
            data.put("whatsAppUrl", whatsAppUrl);

            String successMsg = String.format(
                    "🎉 ¡Listo, %s!\n\n" +
                    "Tu pedido %s quedó registrado correctamente.\n\n" +
                    "📦 Estado: Pedido recibido\n\n" +
                    "Te enviamos la confirmación por WhatsApp y te avisaremos por allí cuando tu pedido esté listo para recoger.\n\n" +
                    "Guarda tu código:\n%s 🍋",
                    order.getCustomerName(), order.getOrderCode(), order.getOrderCode()
            );

            return AIToolResult.builder()
                    .toolName("confirmar_pedido")
                    .success(true)
                    .orderCreated(true)
                    .cartModified(true)
                    .data(data)
                    .message(successMsg)
                    .build();

        } catch (Exception ex) {
            return AIToolResult.builder()
                    .toolName("confirmar_pedido")
                    .success(false)
                    .message("No se pudo crear el pedido: " + ex.getMessage())
                    .build();
        }
    }

    private void registerConsultarPedido() {
        Map<String, Object> props = new HashMap<>();
        props.put("orderCode", Map.of("type", "string", "description", "Código del pedido (ej. 'LD-2026-00001')"));
        props.put("customerPhone", Map.of("type", "string", "description", "Teléfono asociado al pedido para validación de seguridad"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("consultar_pedido")
                .description("Consulta el estado y avance de un pedido en tiempo real.")
                .parametersSchema(schema)
                .executor(this::consultarPedido)
                .build());
    }

    private AIToolResult consultarPedido(Map<String, Object> args, AIConversation conv) {
        String orderCode = (String) args.get("orderCode");
        String phone = (String) args.get("customerPhone");

        if (phone == null || phone.trim().isEmpty()) {
            phone = conv.getCustomerPhone();
        }

        if (orderCode != null && !orderCode.trim().isEmpty()) {
            Optional<Order> optOrder = orderService.getOrderByCode(orderCode.trim());
            if (optOrder.isPresent()) {
                Order order = optOrder.get();
                // Security check: if phone is known, verify phone match
                if (phone != null && !phone.trim().isEmpty() && order.getCustomerPhone() != null) {
                    String cleanPhone = phone.replaceAll("[^0-9]", "");
                    String cleanOrderPhone = order.getCustomerPhone().replaceAll("[^0-9]", "");
                    if (!cleanOrderPhone.contains(cleanPhone) && !cleanPhone.contains(cleanOrderPhone)) {
                        return AIToolResult.builder()
                                .toolName("consultar_pedido")
                                .success(false)
                                .message("El teléfono no coincide con el registro del pedido.")
                                .build();
                    }
                }

                return AIToolResult.builder()
                        .toolName("consultar_pedido")
                        .success(true)
                        .data(formatOrderSummary(order))
                        .message("Pedido " + order.getOrderCode() + " está en estado: " + order.getStatus().getDisplayName())
                        .build();
            }
        }

        if (phone != null && !phone.trim().isEmpty()) {
            List<Order> orders = orderService.getOrdersByPhone(phone.trim());
            if (!orders.isEmpty()) {
                List<Map<String, Object>> summaries = orders.stream()
                        .map(this::formatOrderSummary)
                        .collect(Collectors.toList());

                return AIToolResult.builder()
                        .toolName("consultar_pedido")
                        .success(true)
                        .data(summaries)
                        .message("Se encontraron " + orders.size() + " pedidos para el número proporcionado.")
                        .build();
            }
        }

        return AIToolResult.builder()
                .toolName("consultar_pedido")
                .success(false)
                .message("No se encontró ningún pedido con los datos suministrados.")
                .build();
    }

    private void registerCancelarPedido() {
        Map<String, Object> props = new HashMap<>();
        props.put("orderCode", Map.of("type", "string", "description", "Código del pedido a cancelar"));
        props.put("customerPhone", Map.of("type", "string", "description", "Teléfono de verificación"));
        props.put("reason", Map.of("type", "string", "description", "Motivo de la cancelación"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("orderCode"));

        registry.register(AIToolDefinition.builder()
                .name("cancelar_pedido")
                .description("Cancela un pedido si se encuentra en estado inicial RECEIVED y el usuario está autorizado.")
                .parametersSchema(schema)
                .executor(this::cancelarPedido)
                .build());
    }

    private AIToolResult cancelarPedido(Map<String, Object> args, AIConversation conv) {
        String orderCode = (String) args.get("orderCode");
        String phone = (String) args.get("customerPhone");
        String reason = (String) args.getOrDefault("reason", "Cancelado por el cliente a través de Lemon AI");

        if (phone == null || phone.trim().isEmpty()) {
            phone = conv.getCustomerPhone();
        }

        Optional<Order> optOrder = orderService.getOrderByCode(orderCode.trim());
        if (optOrder.isEmpty()) {
            return AIToolResult.builder()
                    .toolName("cancelar_pedido")
                    .success(false)
                    .message("Pedido no encontrado.")
                    .build();
        }

        Order order = optOrder.get();
        if (order.getStatus() != OrderStatus.RECEIVED) {
            return AIToolResult.builder()
                    .toolName("cancelar_pedido")
                    .success(false)
                    .message("No se puede cancelar el pedido " + orderCode + " porque ya está en estado " + order.getStatus().getDisplayName() + ". Por favor contacta directamente al punto de venta.")
                    .build();
        }

        try {
            orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED, reason, "LEMON_AI_CUSTOMER");
            return AIToolResult.builder()
                    .toolName("cancelar_pedido")
                    .success(true)
                    .message("El pedido " + orderCode + " ha sido cancelado con éxito.")
                    .build();
        } catch (Exception ex) {
            return AIToolResult.builder()
                    .toolName("cancelar_pedido")
                    .success(false)
                    .message("No fue posible cancelar el pedido: " + ex.getMessage())
                    .build();
        }
    }

    private void registerRepetirUltimoPedido() {
        Map<String, Object> props = new HashMap<>();
        props.put("customerPhone", Map.of("type", "string", "description", "Teléfono del cliente para consultar su historial"));

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);

        registry.register(AIToolDefinition.builder()
                .name("repetir_ultimo_pedido")
                .description("Carga los productos del último pedido realizado por el cliente en el carrito actual para repetirlo.")
                .parametersSchema(schema)
                .executor(this::repetirUltimoPedido)
                .build());
    }

    private AIToolResult repetirUltimoPedido(Map<String, Object> args, AIConversation conv) {
        String phone = (String) args.get("customerPhone");
        if (phone == null || phone.trim().isEmpty()) {
            phone = conv.getCustomerPhone();
        }

        if (phone == null || phone.trim().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("repetir_ultimo_pedido")
                    .success(false)
                    .message("Por favor indícame tu número de teléfono para buscar tu último pedido.")
                    .build();
        }

        List<Order> orders = orderService.getOrdersByPhone(phone.trim());
        if (orders.isEmpty()) {
            return AIToolResult.builder()
                    .toolName("repetir_ultimo_pedido")
                    .success(false)
                    .message("No encontramos pedidos anteriores asociados al teléfono " + phone)
                    .build();
        }

        Order lastOrder = orders.get(0); // Most recent
        if (lastOrder.getItems() == null || lastOrder.getItems().isEmpty()) {
            return AIToolResult.builder()
                    .toolName("repetir_ultimo_pedido")
                    .success(false)
                    .message("El último pedido registrado no tiene ítems válidos.")
                    .build();
        }

        // Initialize cart and clone items
        AICart newCart = AICart.builder()
                .cartId("cart-" + UUID.randomUUID().toString().substring(0, 8))
                .items(new ArrayList<>())
                .status(AICart.CartStatus.DRAFT)
                .build();

        for (OrderItem item : lastOrder.getItems()) {
            newCart.getItems().add(AICartItem.builder()
                    .id("item-" + UUID.randomUUID().toString().substring(0, 8))
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .flavorId(item.getFlavorId())
                    .flavorName(item.getFlavorName())
                    .size(item.getSize())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .addonTotal(item.getAddonTotal())
                    .subtotal(item.getSubtotal())
                    .addons(item.getAddons() != null ? item.getAddons().stream().map(a ->
                            com.lemondrop.ai.model.AICartItemAddon.builder()
                                    .addonId(a.getAddonId())
                                    .addonName(a.getAddonName())
                                    .unitPrice(a.getUnitPrice())
                                    .quantity(a.getQuantity())
                                    .build()
                    ).collect(Collectors.toList()) : new ArrayList<>())
                    .build());
        }

        newCart.recalculateTotals();
        conv.setCart(newCart);
        conv.setState(ConversationState.WAITING_CONFIRMATION);

        return AIToolResult.builder()
                .toolName("repetir_ultimo_pedido")
                .success(true)
                .cartModified(true)
                .requiresConfirmation(true)
                .data(Map.of(
                        "orderCode", lastOrder.getOrderCode(),
                        "items", newCart.getItems(),
                        "total", newCart.getTotal()
                ))
                .message("Se ha cargado tu último pedido (" + lastOrder.getOrderCode() + ") al carrito. Total: $" + newCart.getTotal() + ". ¿Deseas confirmarlo?")
                .build();
    }

    private Map<String, Object> formatOrderSummary(Order order) {
        Map<String, Object> map = new HashMap<>();
        map.put("orderCode", order.getOrderCode());
        map.put("customerName", order.getCustomerName());
        map.put("customerPhone", order.getCustomerPhone());
        map.put("status", order.getStatus().name());
        map.put("statusDisplay", order.getStatus().getDisplayName());
        map.put("total", order.getTotal());
        map.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : "");

        List<Map<String, Object>> itemsList = order.getItems().stream().map(item -> {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("productName", item.getProductName());
            itemMap.put("flavorName", item.getFlavorName());
            itemMap.put("size", item.getSize().name());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("subtotal", item.getSubtotal());
            return itemMap;
        }).collect(Collectors.toList());

        map.put("items", itemsList);
        return map;
    }
}
