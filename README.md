# LEMON DROP - Plataforma de Gestión de Granizados

Plataforma web profesional, moderna, responsive y totalmente funcional diseñada para el emprendimiento escolar **LEMON DROP**, dedicada a la comercialización y gestión operativa de granizados artesanales preparados con fruta natural en ferias escolares.

## 🚀 Características
- **Web Pública (Cliente Guest)**:
  - Landing page con identidad visual atractiva (Base crema `#FFFDF6`, toques verde menta `#D8EBB5` y acentos amarillos limón `#FCE22A`).
  - Catálogo interactivo de granizados.
  - Personalizador de pedidos en tiempo real (tamaños `SMALL`, `MEDIUM`, `LARGE` con precios diferenciados, sabores y complementos como Leche condensada o Arequipe).
  - Checkout rápido sin registro (nombre y teléfono).
  - Consulta y seguimiento visual con línea de tiempo en tiempo real (`Seguimiento`).
- **Panel del Asesor (Operativo / Cocina)**:
  - Tablero de control operativa tipo Kanban y tabla.
  - Gestión del ciclo de vida del pedido (`RECEIVED` ➜ `ACCEPTED` ➜ `PREPARING` ➜ `ALMOST_READY` ➜ `READY` ➜ `DELIVERED`).
  - Deducción automática de inventario de insumos (hielo, pulpa de fruta, vasos, servilletas, etc.) al pasar a `PREPARING`.
  - Historial de cambios auditados (`OrderChangeHistory`) si se sustituye un sabor por falta de stock.
  - Alerta de audio con beeps al recibir nuevos pedidos (polling inteligente cada 4 segundos).
  - Notificación de recogida de pedido con enlace dinámico que autogenera y abre el chat de WhatsApp.
- **Panel del Administrador (Admin)**:
  - Gráficos y analíticas operativas del día (ventas totales, ticket promedio, tiempos promedio de cocina).
  - CRUD completo de productos, sabores, complementos e inventario.
  - Alertas automáticas de stock mínimo ("STOCK BAJO" / "AGOTADO").
  - Gestión de usuarios y asesores.

## 🛠️ Tecnologías Utilizadas
- **Backend**: Java 17, Spring Boot 3.3.2, Spring MVC, Spring Security (Autenticación y Autorización por roles).
- **Base de Datos**: MongoDB (Spring Data MongoDB).
- **Frontend**: HTML5, Thymeleaf, CSS3 personalizado (Variables CSS para branding), JavaScript.
- **Pruebas**: JUnit 5, Spring Boot Test.

## 📋 Prerrequisitos
1. **Java Development Kit (JDK) 17** o superior.
2. **Apache Maven 3.x**.
3. **MongoDB** corriendo localmente en el puerto `27017`.

## ⚙️ Configuración y Ejecución

### 1. Iniciar MongoDB localmente
Si deseas iniciar la base de datos localmente usando la carpeta temporal de datos en el proyecto:
```powershell
mkdir mongodb-data
& "C:\Program Files\MongoDB\Server\8.0\bin\mongod.exe" --dbpath ./mongodb-data --port 27017
```

### 2. Ejecutar la Aplicación
Desde el directorio del proyecto, compila y arranca el servidor web local con Spring Boot:
```bash
mvn spring-boot:run
```
La aplicación iniciará en [http://localhost:8080](http://localhost:8080).

## 👥 Cuentas de Acceso (Semilla Automática)
Al iniciar por primera vez, el sistema autoinicializa las siguientes cuentas administrativas para pruebas:

| Rol | Usuario | Contraseña |
|---|---|---|
| **ADMIN** | `admin` | `admin` |
| **ASESOR** | `asesor` | `asesor` |
