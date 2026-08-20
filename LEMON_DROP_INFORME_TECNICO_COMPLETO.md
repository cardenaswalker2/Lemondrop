# INFORME TÉCNICO Y DE AUDITORÍA COMPLETO
## SISTEMA LEMON DROP

**Proyecto:** LEMON DROP - Plataforma de Gestión de Granizados  
**Fecha:** 20 de Agosto de 2026  
**Auditor/Consultor:** Antigravity (Advanced Agentic Coding AI, DeepMind Team)  
**Versión del Sistema:** 1.0.0  
**Despliegue de Producción:** https://lemondrop-b7su.onrender.com/  
**Repositorio GitHub:** https://github.com/cardenaswalker2/Lemondrop.git  

---

## 1. Introducción
El presente informe constituye una auditoría técnica y funcional exhaustiva del ecosistema de software del emprendimiento comercial **LEMON DROP**, especializado en la producción y distribución de granizados artesanales preparados con fruta natural en ferias escolares. El objetivo primordial de este sistema es digitalizar y automatizar los procesos de toma de pedidos de clientes invitados (Guest), la gestión de preparación en tiempo real en cocina (Advisor/Asesor), el control del inventario de insumos e ingredientes, y el análisis analítico-operativo del negocio para la toma de decisiones gerenciales (Admin).

## 2. Objetivos
El objetivo principal de esta auditoría es evaluar la calidad del software, la seguridad de las APIs, la estructura de la base de datos, y la consistencia funcional de las aplicaciones cliente (web y móvil) y el servidor (backend).
Específicamente, este reporte busca:
1. Analizar el modelo de persistencia y estructura de colecciones de la base de datos en MongoDB.
2. Describir detalladamente la arquitectura general del backend (Spring Boot) y de la aplicación móvil (Flutter/Riverpod).
3. Proveer un inventario de todos los endpoints de las APIs.
4. Identificar debilidades de seguridad, riesgos técnicos, fugas de rendimiento y concurrencia.
5. Suministrar una hoja de ruta con recomendaciones de mantenimiento a corto, mediano y largo plazo.

## 3. Descripción General
El ecosistema de **LEMON DROP** consta de:
* **Backend**: Desarrollado en Java con Spring Boot, expone servicios MVC para las interfaces administrativas/operativas del navegador y APIs REST para la aplicación móvil.
* **Base de Datos**: MongoDB ejecutado de manera persistente localmente y en la nube (MongoDB Atlas).
* **Frontend Web**: Plantillas de servidor HTML renderizadas con Thymeleaf, maquetadas con CSS modular nativo y dinamizadas con JavaScript vanilla (AJAX, polling en tiempo real).
* **Aplicación Móvil**: Aplicación desarrollada en Flutter con gestión de estado Riverpod, que implementa alertas nativas (sonidos de notificación y vibración física de hardware) para la gestión operativa en cocina por parte de los asesores.

---

## 4. Arquitectura
El sistema implementa una arquitectura cliente-servidor de múltiples capas, con separación estricta de responsabilidades (Presentación, Negocio, Persistencia).

### Diagrama General de Arquitectura
```
[ Aplicación Web Pública ]  <-- (HTTP/HTML) --+
[ Admin / Asesor Web ]     <-- (HTTP/HTML) --+---> [ Spring Boot MVC & REST Backend ] ---> [ MongoDB ]
[ App Móvil (Flutter) ]    <-- (JSON REST) --+
```

### Flujo de Datos del Cliente Web (Guest)
```
Navegador Web ➜ GET/POST ➜ CatalogController ➜ OrderService ➜ OrderRepository ➜ MongoDB Atlas
```

### Flujo de Datos de la App Móvil (Asesor/Admin)
```
Flutter App ➜ Riverpod Provider ➜ Dio API Client ➜ MobileApiController ➜ OrderService ➜ Repositories ➜ MongoDB
```

---

## 5. Tecnologías Utilizadas

| Área | Tecnología | Versión | Uso |
|------|------------|---------|-----|
| Backend | Java JDK | 17 | Lenguaje principal del servidor |
| Backend | Spring Boot | 3.3.2 | Framework del backend (MVC, Security, Data) |
| Base de Datos | MongoDB | 8.0.0 (Local / Atlas) | Motor de base de datos NoSQL |
| Frontend Web | Thymeleaf | 3.x (Spring Boot default) | Motor de plantillas de servidor HTML5 |
| Frontend Web | CSS3 / Vanilla JS | N/A | Estructura visual y peticiones AJAX |
| Móvil | Flutter | ^3.x.x / Dart | Framework de desarrollo móvil multiplataforma |
| Móvil | Riverpod | ^2.5.1 | Gestor de estados en Flutter |
| Móvil | Dio | ^5.5.0 | Cliente HTTP para consumo de APIs en Flutter |
| Móvil | Audioplayers | ^6.0.0 | Reproductor para beeps de sonido locales |

---

## 6. Backend
El backend de Spring Boot se divide en las siguientes capas de abstracción:
* **Modelos (`com.lemondrop.model`)**: Representan los esquemas de documentos NoSQL anotados con `@Document` para Spring Data MongoDB.
* **Repositorios (`com.lemondrop.repository`)**: Interfaces que extienden `MongoRepository` para interactuar con la base de datos sin SQL manual.
* **Servicios (`com.lemondrop.service`)**: Capa de negocio que controla la lógica de validaciones, descuentos de stock, creación de códigos de pedido secuenciales y auditoría.
* **Controladores (`com.lemondrop.controller`)**: Divididos en subcarpetas según roles (`admin`, `advisor`, `guest`, `api`) que manejan el mapeo de URL y la seguridad.
* **Seguridad (`com.lemondrop.security`)**: Gestiona la autenticación web mediante cookies de sesión y la autenticación móvil/REST mediante el filtro de tokens API (`ApiTokenFilter`).

---

## 7. API

El backend expone un conjunto de endpoints REST y rutas MVC. A continuación, el inventario completo de endpoints:

| Método | Endpoint | Función | Autenticación | Rol Requerido | Respuesta típica |
|--------|----------|---------|---------------|---------------|------------------|
| `GET` | `/` | Página de inicio del catálogo web | Pública | Cualquiera | HTML (Home) |
| `GET` | `/login` | Página web de login de usuarios | Pública | Cualquiera | HTML (Login) |
| `GET` | `/catalogo` | Catálogo de productos interactivo | Pública | Cualquiera | HTML (Catálogo) |
| `POST` | `/api/public/pedidos` | Crear un pedido desde la web | Pública | Cualquiera | JSON `{success, orderCode}` |
| `GET` | `/api/public/pedidos/track/{query}` | Seguimiento de pedidos por código o teléfono | Pública | Cualquiera | JSON `{success, orders/order}` |
| `GET` | `/pedido/exitoso/{code}` | Confirmación de pedido web exitoso | Pública | Cualquiera | HTML |
| `GET` | `/pedido/seguimiento` | Consulta interactiva de seguimiento | Pública | Cualquiera | HTML |
| `GET` | `/pedido/seguimiento/{code}` | Detalle visual en tiempo real de un pedido | Pública | Cualquiera | HTML |
| `GET` | `/api/health` | Health Check (para mantener despierto en Render) | Pública | Cualquiera | JSON `{"status": "UP"}` |
| `POST` | `/api/mobile/auth/login` | Login del API móvil | Pública | Cualquiera | JSON Token + Datos de usuario |
| `GET` | `/api/mobile/me` | Retornar información del usuario móvil actual | Token Requerido | `ADMIN`, `ASESOR` | JSON User |
| `GET` | `/api/mobile/orders` | Listar pedidos activos en la cocina | Token Requerido | `ADMIN`, `ASESOR` | JSON Page |
| `GET` | `/api/mobile/orders/history` | Historial de pedidos entregados/cancelados | Token Requerido | `ADMIN`, `ASESOR` | JSON Page |
| `GET` | `/api/mobile/orders/updates` | Polling de actualizaciones de pedidos (hora local) | Token Requerido | `ADMIN`, `ASESOR` | JSON List |
| `POST` | `/api/mobile/orders/{id}/status` | Cambiar el estado de un pedido (ej. de PREPARING a READY) | Token Requerido | `ADMIN`, `ASESOR` | JSON Order |
| `POST` | `/api/mobile/orders/{id}/edit` | Modificar la composición de un pedido en cocina | Token Requerido | `ADMIN`, `ASESOR` | JSON Order |
| `GET` | `/api/mobile/catalog` | Obtener productos, sabores y complementos activos | Token Requerido | `ADMIN`, `ASESOR` | JSON Map |
| `GET` | `/api/mobile/stats` | Estadísticas del día para el dashboard móvil | Token Requerido | `ADMIN`, `ASESOR` | JSON Stats |
| `GET` | `/advisor/dashboard` | Dashboard web del Asesor | Sesión Web | `ASESOR`, `ADMIN` | HTML |
| `POST` | `/advisor/pedidos/{id}/estado` | Cambiar estado de un pedido en panel web | Sesión Web | `ASESOR`, `ADMIN` | Redirección HTTP |
| `POST` | `/advisor/pedidos/{id}/editar` | Editar ítems de un pedido en panel web | Sesión Web | `ASESOR`, `ADMIN` | Redirección HTTP |
| `GET` | `/advisor/api/pedidos/updates` | Polling web de nuevos pedidos | Sesión Web | `ASESOR`, `ADMIN` | JSON List |
| `GET` | `/admin/dashboard` | Dashboard web del Administrador | Sesión Web | `ADMIN` | HTML |
| `GET` | `/admin/pedidos` | Ver listado completo de pedidos históricos web | Sesión Web | `ADMIN` | HTML |
| `GET` | `/admin/productos` | Administrar productos | Sesión Web | `ADMIN` | HTML |
| `GET` | `/admin/productos/nuevo` | Formulario de producto nuevo | Sesión Web | `ADMIN` | HTML |
| `GET` | `/admin/productos/editar/{id}` | Formulario de edición de producto | Sesión Web | `ADMIN` | HTML |
| `POST` | `/admin/productos/guardar` | Guardar o actualizar producto | Sesión Web | `ADMIN` | Redirección HTTP |
| `POST` | `/admin/productos/eliminar/{id}` | Eliminar lógicamente un producto | Sesión Web | `ADMIN` | Redirección HTTP |
| `GET` | `/admin/sabores` | Administrar sabores | Sesión Web | `ADMIN` | HTML |
| `POST` | `/admin/sabores/guardar` | Guardar o actualizar sabor | Sesión Web | `ADMIN` | Redirección HTTP |
| `POST` | `/admin/sabores/eliminar/{id}` | Eliminar lógicamente un sabor | Sesión Web | `ADMIN` | Redirección HTTP |
| `GET` | `/admin/complementos` | Administrar complementos | Sesión Web | `ADMIN` | HTML |
| `POST` | `/admin/complementos/guardar` | Guardar o actualizar complemento | Sesión Web | `ADMIN` | Redirección HTTP |
| `POST` | `/admin/complementos/eliminar/{id}` | Eliminar lógicamente un complemento | Sesión Web | `ADMIN` | Redirección HTTP |
| `GET` | `/admin/inventario` | Administrar ítems de inventario | Sesión Web | `ADMIN` | HTML |
| `POST` | `/admin/inventario/guardar` | Guardar o actualizar insumo de inventario | Sesión Web | `ADMIN` | Redirección HTTP |
| `GET` | `/admin/usuarios` | Administrar personal (Admin/Asesores) | Sesión Web | `ADMIN` | HTML |
| `POST` | `/admin/usuarios/guardar` | Crear o actualizar usuario | Sesión Web | `ADMIN` | Redirección HTTP |
| `POST` | `/admin/usuarios/toggle/{id}` | Habilitar/Deshabilitar usuario | Sesión Web | `ADMIN` | Redirección HTTP |

---

## 8. Base de Datos
La base de datos utiliza MongoDB. Las principales colecciones identificadas son:

### Colección: `user`
* **Propósito**: Guarda las credenciales del personal administrativo y operativo.
* **Campos**:
  * `id` (`String`, ID autogenerado por Mongo) - Obligatorio
  * `username` (`String`) - Obligatorio, único
  * `passwordHash` (`String`) - Obligatorio
  * `name` (`String`) - Obligatorio
  * `phone` (`String`) - Opcional
  * `role` (`String`, valores: `"ADMIN"`, `"ASESOR"`) - Obligatorio
  * `active` (`boolean`) - Obligatorio
  * `createdAt` (`LocalDateTime`) - Obligatorio

### Colección: `product`
* **Propósito**: Catálogo de granizados comerciales.
* **Campos**:
  * `id` (`String`) - Obligatorio
  * `name` (`String`) - Obligatorio
  * `description` (`String`) - Opcional
  * `image` (`String`) - Opcional
  * `category` (`String`) - Obligatorio
  * `sizePrices` (`Map<ProductSize, BigDecimal>`) - Obligatorio (ej. `SMALL`, `MEDIUM`, `LARGE`)
  * `available` (`boolean`) - Obligatorio
  * `featured` (`boolean`) - Obligatorio
  * `badge` (`String`) - Opcional (ej: "Nuevo", "Más vendido")
  * `active` (`boolean`) - Obligatorio

### Colección: `order`
* **Propósito**: Registra la información de los pedidos de granizados.
* **Campos**:
  * `id` (`String`) - Obligatorio
  * `orderCode` (`String`, formato: `LD-YYYY-XXXXX`) - Obligatorio, único
  * `customerName` (`String`) - Obligatorio
  * `customerPhone` (`String`) - Obligatorio
  * `items` (`List<OrderItem>`) - Obligatorio
  * `subtotal` (`BigDecimal`) - Obligatorio
  * `total` (`BigDecimal`) - Obligatorio
  * `status` (`OrderStatus`, valores: `RECEIVED`, `ACCEPTED`, `PREPARING`, `ALMOST_READY`, `READY`, `DELIVERED`, `CANCELLED`) - Obligatorio
  * `observations` (`String`) - Opcional
  * `cancellationReason` (`String`) - Opcional
  * `createdBy` (`String`) - Obligatorio
  * `lastModifiedBy` (`String`) - Obligatorio
  * `receivedAt` (`LocalDateTime`) - Obligatorio
  * `acceptedAt`, `preparingAt`, `almostReadyAt`, `readyAt`, `deliveredAt`, `cancelledAt` (`LocalDateTime`) - Opcionales (para auditoría de tiempos)

### Generación de Código de Pedido Secuencial
Para evitar colisiones y números duplicados en el código de pedido (ej. `LD-2026-00001`), se utiliza la colección **`counter`** y el servicio `CounterService`.
* `Counter` contiene `year` y `sequence`.
* Se ejecuta una operación atómica `findAndModify` de MongoDB para incrementar la secuencia en 1 y devolver el nuevo valor bloqueando escrituras paralelas concurrentes.

---

## 9. Seguridad
La auditoría de seguridad arrojó el siguiente comportamiento técnico:

1. **Mecanismo Web**: Usa `Cookie-based Session Authentication` administrado por Spring Security. Las contraseñas se encriptan con `BCryptPasswordEncoder` en la inicialización y el login.
2. **Mecanismo Móvil**: Implementa un token estático almacenado en la colección `apiToken`. La app móvil pasa el token en la cabecera `Authorization: Bearer <TOKEN>`.
3. **CORS / CSRF**: El CSRF está explícitamente ignorado para la API móvil (`/api/mobile/**`) para permitir la comunicación REST externa, pero está activado para el resto del sitio web MVC.
4. **Validaciones**: Se implementa validación a nivel de controlador con `@Valid` y especificaciones de restricciones a nivel DTO en la creación de pedidos.
5. **Endpoints Públicos**:
   * Rutas estáticas: `/css/**`, `/js/**`, `/images/**`, `/favicon.ico`.
   * Landing page: `/`, `/catalogo`.
   * Creación y tracking de pedidos web: `/api/public/**`, `/pedido/seguimiento/**`, `/pedido/exitoso/**`.
   * Rutas de monitoreo: `/api/health`.

---

## 10. Aplicación Web
El frontend está implementado con **HTML5 + Thymeleaf + Custom CSS nativo**. No utiliza frameworks pesados como React ni librerías de estilos masivas como Tailwindcss, lo cual permite un renderizado instantáneo.

### Pantallas Principales:
* **Home (`public/home.html`)**: Landing page del negocio. Describe la marca y tiene un botón para ordenar.
* **Catálogo (`public/catalogo.html`)**: Muestra los granizados activos, sus precios por tamaño y un formulario/modal de personalización interactivo para agregar sabores, complementos y observaciones.
* **Seguimiento (`public/seguimiento.html`)**: Barra de búsqueda interactiva donde el usuario ingresa su código de pedido o teléfono y ve una línea de tiempo dinámica con el estado de su orden en tiempo real.
* **Dashboard del Asesor (`advisor/dashboard.html`)**: Tablero tipo Kanban que muestra los pedidos activos organizados por su fase operativa, y emite un pitido cuando ingresa un pedido nuevo.
* **Dashboard del Admin (`admin/dashboard.html`)**: Muestra gráficos de ventas diarias, CRUDs para productos, sabores, complementos, usuarios y stock mínimo de almacén.

---

## 11. Experiencia de Usuario Web
* **Fortalezas**:
  * **Branding Coherente**: Paleta de colores atractiva (crema base, verde menta y acentos limón) consistente con el negocio de granizados.
  * **Transiciones de Estado de Pedidos**: Excelente visualización de la línea de tiempo en el seguimiento del cliente.
  * **Checkout Rápido**: No exige registro de cuenta al usuario final; solo requiere nombre y número de teléfono.
* **Áreas de mejora**:
  * **Estados de Carga**: La personalización y envío del formulario carece de *spinners* o indicadores visuales claros de carga ("procesando...") durante la latencia de red.

---

## 12. Aplicación Móvil
La aplicación móvil está construida sobre **Flutter + Riverpod** y se comunica directamente con la API REST del backend.

* **Estructura Arquitectónica**:
  * `lib/core/models/models.dart`: Define las entidades de negocio replicadas del backend (`Order`, `User`, `Stats`, etc.) y sus mapeos JSON.
  * `lib/core/storage/preferences.dart`: Encapsula `SharedPreferences` para almacenar configuraciones persistentes (URL de backend, volumen, vibración, wake lock).
  * `lib/features/auth/providers/auth_provider.dart`: Gestiona el inicio de sesión y la persistencia de tokens de API.
  * `lib/features/advisor/providers/orders_provider.dart`: Maneja la lista de pedidos activos y realiza el polling periódico al backend.

### Alertas de Sonido y Vibración en la App Móvil:
* Al detectar un pedido entrante con estado `RECEIVED`, la app llama a un canal nativo en Kotlin (`MethodChannel`). 
* El código nativo en `MainActivity.kt` interactúa con el `RingtoneManager` y el servicio `Vibrator` del sistema Android para reproducir el **tono de llamada oficial de notificaciones** de su celular y hacer vibrar el dispositivo de forma física real, adaptándose a las preferencias del usuario.

---

## 13. Funcionalidades
A continuación, se detalla el nivel de implementación de cada componente funcional:

### Tabla de Funcionalidades
| Funcionalidad | Web | Móvil | Backend | Base de datos | Estado |
|---------------|-----|-------|---------|---------------|--------|
| Catálogo interactivo | ✅ | ❌ | ✅ | ✅ | ✅ Implementado (Solo Web) |
| Checkout sin registro | ✅ | ❌ | ✅ | ✅ | ✅ Implementado |
| Tracking de Pedido | ✅ | ❌ | ✅ | ✅ | ✅ Implementado |
| Dashboard Kanban | ✅ | ❌ | ✅ | ✅ | ✅ Implementado |
| Dashboard Admin KPIs | ✅ | ❌ | ✅ | ✅ | ✅ Implementado |
| Alertas por Nuevos Pedidos | ✅ | ✅ | ✅ | ✅ | ✅ Implementado (Web y Móvil) |
| Notificación de WhatsApp | ✅ | ❌ | ✅ | ❌ | ✅ Implementado (Genera link dinámico) |
| CRUD Insumos Inventario | ✅ | ❌ | ✅ | ✅ | ✅ Implementado |
| Deducción automática Stock | ❌ | ❌ | ✅ | ✅ | ✅ Implementado (En backend al preparar) |
| Edición de Pedido en Cocina | ✅ | ✅ | ✅ | ✅ | ✅ Implementado |
| Historial de Cambios (Logs) | ❌ | ❌ | ✅ | ✅ | ✅ Implementado (Auditoría en DB) |
| Mantener pantalla encendida | ❌ | 🟡 | ❌ | ❌ | 🟡 Parcial (Sólo interfaz en app móvil) |

---

## 14. Flujo Completo de Pedidos
```
[ Cliente Web ]            [ Backend / DB ]         [ Asesor / Cocina ]
   Crear Pedido  ➜ POST ➜  Generar Código (LD-...)
                           Deducir Insumos (Stock)
   Seguimiento   «-------  Actualizar Estado  «----  Cambiar Estado (READY)
                           Alertar por WhatsApp ➜  Entregar al Cliente
```

1. **Creación**: El cliente ingresa a la web, añade granizados al carrito, selecciona sabores, tamaños y complementos, e ingresa su nombre y celular.
2. **Cálculo de Precios**: El backend suma el precio base del tamaño del producto + el costo extra del sabor + el costo extra de los complementos seleccionados, y lo multiplica por la cantidad.
3. **Persistencia e Inventario**: Al pasar el pedido a estado `PREPARING` (En preparación), la clase `InventoryService` descuenta automáticamente las unidades necesarias de insumos (hielo, vasos, pulpas, servilletas).
4. **Auditoría**: Cada cambio en el estado del pedido o en sus ingredientes genera una entrada en la colección `orderStatusHistory` o `orderChangeHistory`.

---

## 15. Inventario
El sistema de inventario realiza deducciones lógicas de los insumos.
* Al pasar a `PREPARING`, se descuentan cantidades fijas definidas para cada granizado (ej. 1 vaso, 1 servilleta, 0.1 Kg de pulpa).
* Si el stock de un insumo cae por debajo del valor `minStock` registrado en `InventoryItem`, el sistema web del Administrador marca el ítem con una alerta visual de **"STOCK BAJO"** o **"AGOTADO"** en tiempo real.

---

## 16. Usuarios y Roles
El sistema maneja dos perfiles de acceso:
1. **ADMIN**: Tiene acceso sin restricciones. Puede acceder a los CRUDs de inventario, modificar stock, ver analíticas financieras, crear productos y habilitar/deshabilitar asesores.
2. **ASESOR**: Perfil operativo de cocina. Puede modificar los estados de los pedidos Kanban y alterar/sustituir ingredientes de un pedido si algún sabor se agota en cocina.

### Matriz de Roles y Permisos
| Ruta / Recurso | ADMIN | ASESOR | GUEST (Público) |
|----------------|-------|--------|-----------------|
| `/catalogo` | ✅ | ✅ | ✅ |
| `/pedido/seguimiento/**` | ✅ | ✅ | ✅ |
| `/api/health` | ✅ | ✅ | ✅ |
| `/advisor/**` | ✅ | ✅ | ❌ |
| `/admin/**` | ✅ | ❌ | ❌ |
| `/api/mobile/orders/**` | ✅ | ✅ | ❌ |

---

## 17. Autenticación
* **Web**: Controlada por cookies de sesión de Spring Security. El login envía las credenciales en un formulario codificado `application/x-www-form-urlencoded` cifrado por SSL (HTTPS en Render).
* **Móvil**: Utiliza la cabecera `Authorization: Bearer <TOKEN>`. El token es validado en cada solicitud entrante por la clase `ApiTokenFilter` consultando la colección de base de datos `apiToken`.

---

## 18. Configuración
Toda la configuración principal del backend reside en [`application.yml`](file:///c:/Users/USUARIO/Music/CASA/Lemondrop/src/main/resources/application.yml) y [`application-dev.yml`](file:///c:/Users/USUARIO/Music/CASA/Lemondrop/src/main/resources/application-dev.yml).
* **Variables críticas**: Deben ser inyectadas desde el panel de Render en producción. La variable primordial es `SPRING_DATA_MONGODB_URI` para evitar exponer las claves de tu cluster de MongoDB Atlas en el código fuente.

---

## 19. Despliegue
* **Plataforma**: Render (Web Service Dockerizado).
* **Base de Datos**: MongoDB Atlas (Cloud NoSQL).
* **Health Check Integrado**: El endpoint público `GET /api/health` sirve para realizar solicitudes periódicas automáticas cada 10 minutos desde cron-job.org, evitando que la instancia gratuita de Render entre en estado de inactividad (*suspension mode*).

---

## 20. Rendimiento
* **Políticas de Polling**: Tanto el panel web del Asesor (polling cada 4 segundos) como la app móvil (polling cada 5 segundos) consultan el endpoint de actualizaciones de pedidos `/api/mobile/orders/updates` pasándole la hora local del dispositivo. Esto evita consultar y transferir toda la colección de la base de datos de manera reiterada.
* **Índices de base de datos**: Es recomendable agregar un índice sobre `orderCode` y `customerPhone` en la colección de MongoDB para mantener un tiempo de respuesta rápido a medida que crezca el número de pedidos.

---

## 21. Manejo de Errores
* El backend implementa un manejador global de excepciones (`GlobalExceptionHandler`) que captura errores comunes (`IllegalArgumentException`, `IllegalStateException`) y devuelve redirecciones consistentes o respuestas JSON limpias con el código HTTP apropiado (ej. 400 Bad Request en APIs y vistas de error 404/500 amigables en web).

---

## 22. Validaciones
* Las validaciones se ejecutan en cascada: el cliente móvil y web validan campos vacíos y formatos de entrada básicos (ej. teléfonos numéricos de 10 dígitos), mientras que el backend valida existencias de IDs de productos, sabores disponibles y consistencia de inventario antes de almacenar el pedido.

---

## 23. Auditoría de Código
Se clasifica el estado del código base del proyecto:
* 🟢 **Bajo**: El código está bien estructurado y documentado, respetando las convenciones del patrón MVC.
* 🟢 **Bajo**: Nombres de variables y modularidad de servicios correctos.
* 🟡 **Medio (Solucionado)**: El desfase de la zona horaria en el polling de actualizaciones móviles impedía que funcionaran las notificaciones (Solucionado: Ahora consulta en hora local).
* 🟡 **Medio (Solucionado)**: Las pruebas unitarias limpiaban la base de datos de desarrollo al iniciar la aplicación (Solucionado: Aislado a una base de datos de test temporal).

---

## 24. Conclusiones y Estado del Proyecto
El proyecto **LEMON DROP** se encuentra en un estado funcional maduro y robusto para su despliegue comercial en ferias escolares. La integración de alertas de sonido nativas y vibración física en Android permite un control operativo dinámico en cocina, mientras que la arquitectura Dockerizada simplifica drásticamente su despliegue y escalado continuo en plataformas cloud como Render.
