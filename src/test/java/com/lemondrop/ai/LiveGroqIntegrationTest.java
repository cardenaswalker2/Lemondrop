package com.lemondrop.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemondrop.ai.client.GroqClient;
import com.lemondrop.ai.config.GroqConfig.GroqProperties;
import com.lemondrop.ai.config.GroqConfig.LemonAiProperties;
import com.lemondrop.ai.dto.AIChatRequest;
import com.lemondrop.ai.dto.AIChatResponse;
import com.lemondrop.ai.model.AIConversation;
import com.lemondrop.ai.repository.AIConversationRepository;
import com.lemondrop.ai.service.AIConversationService;
import com.lemondrop.ai.service.LemonDropAIService;
import com.lemondrop.ai.service.SecurityAuditService;
import com.lemondrop.ai.tools.AIToolRegistry;
import com.lemondrop.ai.tools.impl.*;
import com.lemondrop.model.*;
import com.lemondrop.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class LiveGroqIntegrationTest {

    @Test
    void testLiveGroqMultiTurnAndTools() {
        String apiKey = System.getenv("GROQ_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("groq.api.key", "");
        }
        if (apiKey.trim().isEmpty()) {
            System.out.println("GROQ_API_KEY no configurada. Omitiendo prueba en vivo.");
            return;
        }

        GroqProperties groqProperties = new GroqProperties();
        groqProperties.getApi().setKey(apiKey);
        groqProperties.getApi().setModel("openai/gpt-oss-120b");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        RestTemplate restTemplate = new RestTemplate(factory);

        ObjectMapper objectMapper = new ObjectMapper();
        GroqClient groqClient = new GroqClient(restTemplate, groqProperties, objectMapper);

        AIToolRegistry toolRegistry = new AIToolRegistry(objectMapper);
        ProductService productService = Mockito.mock(ProductService.class);
        FlavorService flavorService = Mockito.mock(FlavorService.class);
        AddonService addonService = Mockito.mock(AddonService.class);
        InventoryService inventoryService = Mockito.mock(InventoryService.class);
        OrderService orderService = Mockito.mock(OrderService.class);
        WhatsAppService whatsAppService = Mockito.mock(WhatsAppService.class);

        Product p = Product.builder()
                .id("p1")
                .name("Granizado Clásico")
                .sizePrices(Map.of(ProductSize.SMALL, new BigDecimal("4000"), ProductSize.MEDIUM, new BigDecimal("6000"), ProductSize.LARGE, new BigDecimal("8000")))
                .active(true)
                .available(true)
                .build();
        Flavor f = Flavor.builder().id("f1").name("Mango").additionalPrice(BigDecimal.ZERO).available(true).build();
        Addon a = Addon.builder().id("a1").name("Gomitas").additionalPrice(new BigDecimal("1000")).available(true).build();

        when(productService.getAllActiveAndAvailable()).thenReturn(List.of(p));
        when(productService.getById("p1")).thenReturn(Optional.of(p));
        when(flavorService.getAvailableFlavors()).thenReturn(List.of(f));
        when(flavorService.getById("f1")).thenReturn(Optional.of(f));
        when(addonService.getAvailableAddons()).thenReturn(List.of(a));
        when(addonService.getById("a1")).thenReturn(Optional.of(a));

        CatalogTools catalogTools = new CatalogTools(toolRegistry, productService, flavorService, addonService, inventoryService);
        catalogTools.registerTools();

        LemonAiProperties aiProps = new LemonAiProperties();
        CartTools cartTools = new CartTools(toolRegistry, productService, flavorService, addonService, aiProps);
        cartTools.registerTools();

        OrderTools orderTools = new OrderTools(toolRegistry, orderService, whatsAppService);
        orderTools.registerTools();

        RecommendationTools recTools = new RecommendationTools(toolRegistry, productService, flavorService, "Lemon Drop", "10am-9pm", "+573001234567", "Calle 10 # 40-20");
        recTools.registerTools();

        AIConversationRepository convRepo = Mockito.mock(AIConversationRepository.class);
        Map<String, AIConversation> storage = new HashMap<>();
        when(convRepo.findByConversationId(Mockito.anyString())).thenAnswer(inv -> Optional.ofNullable(storage.get(inv.getArgument(0))));
        when(convRepo.save(Mockito.any(AIConversation.class))).thenAnswer(inv -> {
            AIConversation conv = inv.getArgument(0);
            storage.put(conv.getConversationId(), conv);
            return conv;
        });

        AIConversationService convService = new AIConversationService(convRepo, aiProps);
        SecurityAuditService auditService = Mockito.mock(SecurityAuditService.class);
        when(auditService.sanitizeInput(Mockito.anyString())).thenAnswer(inv -> inv.getArgument(0));

        LemonDropAIService aiService = new LemonDropAIService(
                groqClient, groqProperties, aiProps, convService, toolRegistry, auditService, objectMapper
        );

        // Turn 1: "Recomiendame algo rico"
        AIChatRequest req1 = AIChatRequest.builder()
                .message("Recomiendame algo rico")
                .build();
        AIChatResponse res1 = aiService.processMessage(req1);
        System.out.println("TURN 1 RESPONSE: " + res1.getMessage());
        assertNotNull(res1.getMessage());
        assertTrue(!res1.getMessage().contains("problema de conexión"));

        // Turn 2: "sii, ese mismo porfa"
        AIChatRequest req2 = AIChatRequest.builder()
                .conversationId(res1.getConversationId())
                .clientToken(res1.getClientToken())
                .message("sii, ese mismo porfa")
                .build();
        AIChatResponse res2 = aiService.processMessage(req2);
        System.out.println("TURN 2 RESPONSE: " + res2.getMessage());
        assertNotNull(res2.getMessage());
        assertTrue(!res2.getMessage().contains("problema de conexión"));
    }
}
