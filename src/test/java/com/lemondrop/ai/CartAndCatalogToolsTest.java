package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIToolResult;
import com.lemondrop.ai.model.*;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.ai.tools.impl.CartTools;
import com.lemondrop.ai.tools.impl.CatalogTools;
import com.lemondrop.model.*;
import com.lemondrop.service.AddonService;
import com.lemondrop.service.FlavorService;
import com.lemondrop.service.InventoryService;
import com.lemondrop.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CartAndCatalogToolsTest {

    private AIToolRegistry registry;
    private ProductService productService;
    private FlavorService flavorService;
    private AddonService addonService;
    private InventoryService inventoryService;
    private LemonAiProperties properties;

    private CatalogTools catalogTools;
    private CartTools cartTools;

    private Product testProduct;
    private Flavor testFlavor;
    private Addon testAddon;

    @BeforeEach
    void setUp() {
        registry = new AIToolRegistry(new ObjectMapper());
        productService = Mockito.mock(ProductService.class);
        flavorService = Mockito.mock(FlavorService.class);
        addonService = Mockito.mock(AddonService.class);
        inventoryService = Mockito.mock(InventoryService.class);
        properties = new LemonAiProperties();

        Map<ProductSize, BigDecimal> prices = new HashMap<>();
        prices.put(ProductSize.SMALL, new BigDecimal("4000"));
        prices.put(ProductSize.MEDIUM, new BigDecimal("6000"));
        prices.put(ProductSize.LARGE, new BigDecimal("8000"));

        testProduct = Product.builder()
                .id("prod-1")
                .name("Granizado Clásico")
                .description("Delicioso granizado artesanal")
                .sizePrices(prices)
                .active(true)
                .available(true)
                .build();

        testFlavor = Flavor.builder()
                .id("flav-1")
                .name("Mango")
                .additionalPrice(BigDecimal.ZERO)
                .available(true)
                .build();

        testAddon = Addon.builder()
                .id("addon-1")
                .name("Gomitas")
                .additionalPrice(new BigDecimal("1000"))
                .available(true)
                .build();

        when(productService.getAllActiveAndAvailable()).thenReturn(List.of(testProduct));
        when(productService.getById("prod-1")).thenReturn(Optional.of(testProduct));
        when(flavorService.getAvailableFlavors()).thenReturn(List.of(testFlavor));
        when(flavorService.getById("flav-1")).thenReturn(Optional.of(testFlavor));
        when(addonService.getAvailableAddons()).thenReturn(List.of(testAddon));
        when(addonService.getById("addon-1")).thenReturn(Optional.of(testAddon));

        catalogTools = new CatalogTools(registry, productService, flavorService, addonService, inventoryService);
        catalogTools.registerTools();

        cartTools = new CartTools(registry, productService, flavorService, addonService, properties);
        cartTools.registerTools();
    }

    @Test
    void testBuscarProductosTool() {
        AIConversation conv = AIConversation.builder().conversationId("c1").build();
        AIToolResult result = registry.execute("buscar_productos", "{\"query\": \"mango\"}", conv);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }

    @Test
    void testAgregarProductoAndBackendPriceAuthority() {
        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(AICart.builder().cartId("cart-1").items(new ArrayList<>()).build())
                .build();

        // Pass arbitrary external requested price, the backend MUST calculate (8000 + 0 + 1000) * 1 = 9000
        String args = "{\"productName\": \"Granizado Clásico\", \"flavorName\": \"Mango\", \"size\": \"LARGE\", \"addonIds\": [\"Gomitas\"], \"quantity\": 1}";
        AIToolResult result = registry.execute("agregar_producto", args, conv);

        assertTrue(result.isSuccess());
        assertTrue(result.isCartModified());
        assertEquals(1, conv.getCart().getItems().size());

        AICartItem item = conv.getCart().getItems().get(0);
        assertEquals(ProductSize.LARGE, item.getSize());
        assertEquals(new BigDecimal("8000"), item.getUnitPrice());
        assertEquals(new BigDecimal("1000"), item.getAddonTotal());
        assertEquals(new BigDecimal("9000"), item.getSubtotal());
        assertEquals(new BigDecimal("9000"), conv.getCart().getTotal());
    }

    @Test
    void testModificarProductoCarrito() {
        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(AICart.builder().cartId("cart-1").items(new ArrayList<>()).build())
                .build();

        // Add first
        registry.execute("agregar_producto", "{\"productName\": \"Granizado Clásico\", \"flavorName\": \"Mango\", \"size\": \"SMALL\", \"quantity\": 1}", conv);
        assertEquals(new BigDecimal("4000"), conv.getCart().getTotal());

        // Modify size to MEDIUM
        registry.execute("modificar_producto_carrito", "{\"size\": \"MEDIUM\"}", conv);
        assertEquals(new BigDecimal("6000"), conv.getCart().getTotal());
        assertEquals(ProductSize.MEDIUM, conv.getCart().getItems().get(0).getSize());
    }

    @Test
    void testEliminarYVaciarCarrito() {
        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(AICart.builder().cartId("cart-1").items(new ArrayList<>()).build())
                .build();

        registry.execute("agregar_producto", "{\"productName\": \"Granizado Clásico\", \"flavorName\": \"Mango\", \"size\": \"SMALL\"}", conv);
        assertEquals(1, conv.getCart().getItems().size());

        registry.execute("vaciar_carrito", "{}", conv);
        assertEquals(0, conv.getCart().getItems().size());
        assertEquals(BigDecimal.ZERO, conv.getCart().getTotal());
    }

    @Test
    void testMultiStepConversationalModification() {
        Addon oreo = Addon.builder().id("addon-2").name("Oreo").additionalPrice(new BigDecimal("1500")).available(true).build();
        when(addonService.getAvailableAddons()).thenReturn(List.of(testAddon, oreo));
        when(addonService.getById("addon-2")).thenReturn(Optional.of(oreo));

        AIConversation conv = AIConversation.builder()
                .conversationId("c1")
                .cart(AICart.builder().cartId("cart-1").items(new ArrayList<>()).build())
                .build();

        // 1. "Quiero mango" (defaults to MEDIUM = 6000)
        registry.execute("agregar_producto", "{\"productName\": \"Granizado Clásico\", \"flavorName\": \"Mango\"}", conv);
        assertEquals(1, conv.getCart().getItems().size());
        assertEquals(new BigDecimal("6000"), conv.getCart().getTotal());

        // 2. "Grande" -> change size to LARGE (8000)
        registry.execute("modificar_producto_carrito", "{\"size\": \"LARGE\"}", conv);
        assertEquals(1, conv.getCart().getItems().size()); // Ensure no duplicate item was added!
        assertEquals(new BigDecimal("8000"), conv.getCart().getTotal());

        // 3. "Con Oreo" -> add Oreo (+1500 = 9500)
        registry.execute("modificar_producto_carrito", "{\"addAddonNames\": [\"Oreo\"]}", conv);
        assertEquals(1, conv.getCart().getItems().size());
        assertEquals(new BigDecimal("9500"), conv.getCart().getTotal());

        // 4. "También agrégale gomitas" -> add Gomitas (+1000 = 10500)
        registry.execute("modificar_producto_carrito", "{\"addAddonNames\": [\"Gomitas\"]}", conv);
        assertEquals(1, conv.getCart().getItems().size());
        assertEquals(2, conv.getCart().getItems().get(0).getAddons().size());
        assertEquals(new BigDecimal("10500"), conv.getCart().getTotal());

        // 5. "Quita el Oreo" -> remove Oreo (-1500 = 9000)
        registry.execute("modificar_producto_carrito", "{\"removeAddonNames\": [\"Oreo\"]}", conv);
        assertEquals(1, conv.getCart().getItems().size());
        assertEquals(1, conv.getCart().getItems().get(0).getAddons().size());
        assertEquals("Gomitas", conv.getCart().getItems().get(0).getAddons().get(0).getAddonName());
        assertEquals(new BigDecimal("9000"), conv.getCart().getTotal());
    }
}

