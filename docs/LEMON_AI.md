# 🍋 LEMON DROP AI — DOCUMENTACIÓN TÉCNICA DEL AGENTE DE PEDIDOS

Sistema de Inteligencia Artificial para **Lemon Drop** impulsado por **Groq** (`Llama-3.3-70b-versatile` para procesamiento conversacional y Function Calling; `Whisper-large-v3-turbo` para Speech-to-Text de alta precisión).

---

## 1. Arquitectura del Sistema

El sistema implementa un **AI Order Agent** de bucle cerrado (*Agent Loop*) que traduce el lenguaje natural coloquial en transacciones reales contra la base de datos de MongoDB y la lógica de negocio de Spring Boot.

```
Usuario (Voz 🎙️ / Texto 💬)
         ↓
Frontend Lemon AI (Widget Flotante Web)
         ↓
POST /api/ai/chat  |  POST /api/ai/voice
         ↓
Spring Boot Backend (LemonDropAIService)
         ↓
Groq Client (Llama 3.3 70B / Whisper Turbo)
         ↓
Function Calling / Tool Dispatcher (AIToolRegistry)
         ↓
Servicios de Negocio (ProductService, FlavorService, AddonService, OrderService, InventoryService)
         ↓
MongoDB (Colecciones reales: products, flavors, addons, orders, ai_conversations, ai_audit_logs)
         ↓
Cálculo de Precios e Idempotencia (Autoridad Backend)
         ↓
Respuesta Estructurada + Confirmación Explícita + WhatsApp
```

---

## 2. Configuración y Variables de Entorno

### Única Variable de Entorno Requerida:
Para habilitar el agente inteligente con Groq en producción o desarrollo:

```bash
export GROQ_API_KEY=gsk_tu_api_key_real_de_groq
```

### Configuración en `application.yml`:

```yaml
groq:
  api:
    key: ${GROQ_API_KEY:}
    url: ${GROQ_API_URL:https://api.groq.com/openai/v1/chat/completions}
    model: ${GROQ_API_MODEL:llama-3.3-70b-versatile}
  stt:
    url: ${GROQ_STT_URL:https://api.groq.com/openai/v1/audio/transcriptions}
    model: ${GROQ_STT_MODEL:whisper-large-v3-turbo}
  timeout:
    connect: 10000
    read: 60000

lemon:
  ai:
    max-tool-iterations: 8
    max-message-length: 2000
    cart-expiration-minutes: 60
    rate-limit:
      messages-per-minute: 30
      audios-per-minute: 10
      max-audio-size-bytes: 5242880 # 5MB
```

> **Fallback Graceful**: Si `GROQ_API_KEY` no está configurada, el backend inicia normalmente y responde con un mensaje de bienvenida amigable invitando al usuario a usar el catálogo web, sin lanzar errores 500 ni interrumpir el resto de la aplicación.

---

## 3. Catálogo de Herramientas (Function Calling)

El agente tiene registradas 18 herramientas operativas que interactúan directamente con los servicios existentes de Lemon Drop:

| Herramienta | Descripción | Servicio Reutilizado |
| :--- | :--- | :--- |
| `buscar_productos` | Búsqueda difusa de productos por nombre, categoría o sabor | `ProductService`, `FlavorService` |
| `obtener_catalogo` | Catálogo completo con productos, precios por tamaño y toppings | `ProductService`, `AddonService` |
| `consultar_producto` | Detalle específico de un producto y su lista de precios oficial | `ProductService` |
| `consultar_stock` | Consulta existencias e insumos en inventario real | `InventoryService` |
| `crear_carrito` | Inicializa un carrito temporal para la sesión de conversación | `AIConversationService` |
| `agregar_producto` | Agrega un granizado personalizado con cálculo de precio 100% backend | `ProductService`, `FlavorService`, `AddonService` |
| `modificar_producto_carrito` | Cambia tamaño, sabor o toppings de un ítem existente en el carrito | `CartTools` |
| `eliminar_producto_carrito` | Remueve un ítem específico o el último ítem del carrito | `CartTools` |
| `consultar_carrito` | Devuelve el desglose, subtotales y total exacto del carrito | `CartTools` |
| `vaciar_carrito` | Elimina todos los ítems del carrito de la sesión | `CartTools` |
| `recomendar_producto` | Sugiere combinaciones según presupuesto, sabor o popularidad | `ProductService`, `FlavorService` |
| `crear_borrador_pedido` | Arma el borrador y pasa la conversación a estado `WAITING_CONFIRMATION` | `OrderTools` |
| `confirmar_pedido` | Crea formalmente el pedido en `OrderService` con código e idempotencia | `OrderService`, `WhatsAppService` |
| `consultar_pedido` | Consulta el avance de un pedido validando teléfono/código | `OrderService` |
| `cancelar_pedido` | Cancela pedidos únicamente en estado inicial `RECEIVED` | `OrderService` |
| `repetir_ultimo_pedido` | Carga los ítems del último pedido realizado por el cliente | `OrderService` |
| `consultar_horarios` | Horarios oficiales de apertura y atención | `RecommendationTools` |
| `consultar_promociones` | Ofertas y productos destacados del negocio | `ProductService` |
| `obtener_configuracion_negocio` | Información oficial de contacto, WhatsApp y ubicación | `RecommendationTools` |

---

## 4. Endpoints REST de la IA

### 1. Chat Conversacional
```http
POST /api/ai/chat
Content-Type: application/json

{
  "conversationId": "conv-123",
  "clientToken": "token-abc-456",
  "message": "Quiero un granizado de mango grande con gomitas",
  "customerName": "Juan Pérez",
  "customerPhone": "3001234567"
}
```

### 2. Entrada por Voz (Audio a Texto + IA)
```http
POST /api/ai/voice
Content-Type: multipart/form-data

audio: [archivo binario webm / wav]
conversationId: "conv-123"
clientToken: "token-abc-456"
customerName: "Juan Pérez"
customerPhone: "3001234567"
```

### 3. Consulta Segura de Conversación
```http
GET /api/ai/conversations/{conversationId}?clientToken=token-abc-456
```

### 4. Streaming Server-Sent Events (SSE)
```http
POST /api/ai/chat/stream
Content-Type: application/json
```

---

## 5. Regla de Autoridad del Backend

El modelo de lenguaje Groq **únicamente interpreta lenguaje natural e invoca herramientas**.
Cualquier intento del modelo o del usuario de inyectar precios o totales arbitrarios es ignorado:
1. El backend busca el producto en MongoDB por ID o nombre.
2. Extrae el precio oficial de la tabla de precios por tamaño (`ProductSize`).
3. Agrega el sobreprecio oficial del sabor (`Flavor.additionalPrice`).
4. Agrega el sobreprecio oficial de cada topping (`Addon.additionalPrice`).
5. Multiplica por la cantidad y calcula el subtotal y total.
6. El código del pedido (`LD-YYYY-XXXXX`) es generado exclusivamente por `CounterService`.

---

## 6. Seguridad Implementada

1. **Tokens de Sesión de Cliente (`clientToken`)**: Cada conversación creada genera un token criptográfico único. No es posible consultar o manipular conversaciones ajenas sin el token correspondiente.
2. **Protección contra Abuso y Rate Limiting**: Limitador de tasa por minuto tanto para mensajes de texto (30/min) como para audios (10/min) y tamaño máximo de archivo (5MB).
3. **Defensa contra Prompt Injection**: Sanitización de caracteres de control nulos y directivas en el System Prompt que priorizan la neutralidad y el rechazo a divulgación de credenciales.
4. **Auditoría Transaccional**: Registro en `AIAuditLog` de cada herramienta invocada con duración en milisegundos y resumen de respuesta sin registrar secretos.
5. **No Exposición de Claves**: La API key de Groq reside estrictamente en el entorno del servidor y jamás viaja al cliente web ni a la aplicación móvil.

---

## 7. Experiencia de Voz y Text-to-Speech

- **Speech-to-Text**: Utiliza la Web Audio API del navegador para capturar el micrófono con `MediaRecorder` y lo envía a Groq Whisper (`whisper-large-v3-turbo`).
- **Text-to-Speech Desacoplado**: Se implementa a través de la interfaz `TextToSpeechProvider` con soporte nativo para `window.speechSynthesis` configurado en acento latinoamericano (`es-CO`/`es-ES`), con botón de activación/silencio en el encabezado del widget.
