# 🍋 LEMON DROP AI — Guía de Integración en Flutter

Este documento describe la arquitectura, endpoints, modelos, estado global, soporte de voz y directivas técnicas de la integración móvil de **Lemon Drop AI** en Flutter.

---

## 1. Principio Arquitectónico

La aplicación móvil Flutter actúa como una **interfaz nativa cliente** para el motor de **Lemon Drop AI** alojado exclusivamente en el backend Spring Boot.

```text
┌────────────────────────────────────────────────────────┐
│                   FLUTTER MOBILE APP                   │
│                                                        │
│  [LemonAiFab] ➔ [LemonAiSheet Modal / Full-Screen]    │
│                                                        │
│  State: LemonAiNotifier (StateNotifierProvider)        │
│  Services: AudioRecorderService (record) & TtsService  │
│  Repository: LemonAiRepository                         │
│  HTTP Client: ApiClient (Dio with AppPreferences)      │
└──────────────────────────┬─────────────────────────────┘
                           │ POST /api/ai/chat
                           │ POST /api/ai/voice
                           │ GET  /api/ai/conversations/{id}
                           ▼
┌────────────────────────────────────────────────────────┐
│                  SPRING BOOT BACKEND                   │
│                                                        │
│  LemonDropAIService ➔ Groq / Tools ➔ MongoDB           │
│  (Catalog, Cart, Inventory, Order, WhatsApp, Security) │
└────────────────────────────────────────────────────────┘
```

> [!IMPORTANT]
> Flutter **NUNCA** contiene claves privadas como `GROQ_API_KEY`, ni realiza cálculos independientes de precios, stock o estados de pedidos. El backend es la única fuente de verdad.

---

## 2. Endpoints Consumidos

| Método | Endpoint | Descripción | Payload |
|---|---|---|---|
| `POST` | `/api/ai/chat` | Envía mensaje conversacional de texto o acciones explícitas | `AIChatRequest` (JSON) |
| `POST` | `/api/ai/voice` | Envía audio de voz para transcripción Whisper y procesamiento AI | `FormData` (Multipart con archivo `.m4a`/`.wav`) |
| `GET` | `/api/ai/conversations/{id}` | Consulta el estado e historial de una conversación activa | Path param `{id}` |
| `POST` | `/api/ai/chat/stream` | Preparado para streaming Server-Sent Events (SSE) | `AIChatRequest` (JSON) |

---

## 3. Modelos Tipados Dart

Ubicación: `mobile/lib/features/lemon_ai/data/models/ai_models.dart`

* `AIChatRequest`: Contiene `conversationId`, `clientToken`, `message`, `customerName`, `customerPhone`, `action`.
* `AIChatResponse`: Contiene `conversationId`, `clientToken`, `message`, `state`, `intent`, `cartUpdated`, `requiresConfirmation`, `orderReadyForConfirmation`, `orderConfirmed`, `orderCode`, `whatsAppUrl`, `cart`, `suggestions`, `success`, `error`.
* `AICartDto` & `AICartItemDto`: Estructura tipada con productos, sabores, tamaños, toppings, subtotales y total en pesos colombianos.
* `AIMessage`: Historial de mensajes en UI con roles `user`, `assistant`, marcas de voz y snapshots de carrito.

---

## 4. Estado Global con Riverpod

Ubicación: `mobile/lib/features/lemon_ai/presentation/providers/lemon_ai_provider.dart`

* `lemonAiProvider`: Mantiene el estado de la conversación entre todas las pantallas (`WelcomeScreen`, `AdvisorScreen`, `AdminScreen`), permitiendo que el usuario navegue sin perder el contexto conversacional.
* **Persistencia Segura**:
  * `clientToken` ➔ Almacenado en `FlutterSecureStorage`.
  * `conversationId` y `isTtsEnabled` ➔ Almacenados en `SharedPreferences`.
* **Ciclo de Vida**: Soporta `startNewConversation()`, `resetConversation()` y reconexión automática.

---

## 5. Entrada y Salida por Voz

### Grabación de Audio (`AudioRecorderService`)
* Utiliza el paquete `record`.
* Gestiona permisos en tiempo de ejecución (`RECORD_AUDIO`).
* Almacena temporalmente el audio en la carpeta cache y lo **elimina de inmediato** al terminar el envío o si el usuario cancela la grabación.

### Síntesis de Voz (`TextToSpeechProvider` / `FlutterTtsProvider`)
* Soporta reproducción en español (`es-CO` / `es-ES`).
* **Interrupción inteligente**: Si el usuario presiona el botón de micrófono para hablar o envía un nuevo mensaje, el TTS se detiene instantáneamente (`stop()`).

---

## 6. Tarjetas de Pedido y Estados Reales

* **Confirmación (`LemonAiOrderCard`)**: Cuando el backend devuelve `requiresConfirmation: true`, se renderiza una tarjeta estructurada con el desglose exacto de productos, tamaño, toppings y el total devuelto por Spring Boot. Al pulsar **Confirmar Pedido**, se envía la acción `CONFIRM_ORDER`.
* **Éxito (`LemonAiSuccessCard`)**: Cuando el backend devuelve `orderConfirmed: true`, muestra el estado **`🟢 Estado: Pedido recibido`** y el código `LD-YYYY-XXXXX`, con botones para abrir WhatsApp o ir a seguimiento. Nunca afirma que está listo para recoger hasta que el backend pase a `READY`.

---

## 7. Ejecución de Tests en Flutter

```bash
cd mobile
flutter pub get
flutter analyze
flutter test
```
