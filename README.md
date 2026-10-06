# Sistema de Gestion de Pedidos y Delivery

API REST basada en microservicios para la gestion de autenticacion, catalogos comerciales y ciclo de vida de pedidos a domicilio. Desarrollada con Spring Boot 3, Spring Security 6, JWT, Spring Data JPA y PostgreSQL.

## Arquitectura del Proyecto

El sistema esta construido con una arquitectura de microservicios organizada bajo un proyecto multi-modulo de Maven:

- api-gateway (Puerto 8000): Punto unico de entrada para los clientes. Enruta las peticiones HTTP hacia el microservicio correspondiente y gestiona CORS.
- auth-service (Puerto 8081): Registro, autenticacion y emision de tokens JWT. Control de roles (ADMIN, REPARTIDOR, CLIENTE) y cifrado de contrasenas con BCrypt.
- comercio-service (Puerto 8082): Gestion de comercios y catalogo de productos. Control de inventario con bloqueo pesimista en base de datos para alta concurrencia.
- pedido-service (Puerto 8083): Logica transaccional de pedidos, integracion con comercio-service para validacion y descuento de stock, maquina de estados y cancelaciones.

## Requisitos Previos

- Java Development Kit (JDK) 21 instalado y configurado en el PATH.
- PostgreSQL 18 (o compatible) corriendo en el puerto 5432.
- Maven 3.8+ (o utilizar el wrapper incluido mvnw.cmd).
- Git Bash o terminal bash para la ejecucion del script de pruebas.

## Configuracion de Base de Datos

El sistema utiliza tres bases de datos independientes en PostgreSQL para mantener el aislamiento entre servicios:

- delivery_auth_db
- delivery_comercio_db
- delivery_pedido_db

Credenciales predeterminadas (configuradas en application.properties de cada servicio):
- Host: localhost
- Puerto: 5432
- Usuario: postgres
- Contrasena: admin

Si se requieren credenciales distintas, se pueden sobreescribir mediante variables de entorno o consultando el archivo .env.example.

## Usuarios Iniciales

Al iniciar el servicio de autenticacion por primera vez, se inicializan automaticamente las siguientes cuentas:

| Correo | Contrasena | Rol | Descripcion |
| --- | --- | --- | --- |
| admin@delivery.com | admin123 | ADMIN | Administrador general de la plataforma |
| repartidor@delivery.com | admin123 | REPARTIDOR | Personal de reparto para actualizacion de estados |
| cliente@delivery.com | admin123 | CLIENTE | Usuario consumidor para realizar pedidos |

Nota de seguridad: Cualquier nuevo usuario que se registre publicamente a traves del endpoint /api/v1/auth/register tendra asignado unicamente el rol CLIENTE, previniendo escalamiento de privilegios.

## Reglas de Negocio Implementadas

1. Calculo Consistente en Servidor:
   - El total a pagar se calcula en el backend multiplicando precio unitario por cantidad para cada producto del pedido, sumando un costo fijo de envio de Q20.00.
2. Control Atomico de Stock y Concurrencia:
   - Las operaciones de descuento y restauracion de stock utilizan bloqueo pesimista a nivel de base de datos (PESSIMISTIC_WRITE) para evitar condiciones de carrera ante compras simultaneas.
3. Transaccion Compensatoria (Rollback Distribuido):
   - Al procesar un pedido de multiples productos, si alguno de ellos no cuenta con existencias suficientes, se aborta la operacion y se restaura automaticamente el inventario de los articulos previamente procesados.
4. Maquina de Estados Estricta:
   - El ciclo de vida de una orden sigue exclusivamente el flujo:
     PENDIENTE -> EN_PREPARACION -> EN_CAMINO -> ENTREGADO
   - Se rechaza cualquier transicion que intente saltar pasos o modificar un pedido finalizado.
5. Politica de Cancelacion:
   - Un pedido solo puede ser cancelado mientras se encuentre en estado PENDIENTE. Al cancelarse, el sistema repone inmediatamente el stock correspondiente a los productos de la orden.

## Principales Endpoints de la API

Todas las solicitudes externas deben dirigirse a traves del API Gateway en http://localhost:8000.

### Autenticacion (/api/v1/auth)
- POST /api/v1/auth/register: Registro publico de usuarios (asigna rol CLIENTE).
- POST /api/v1/auth/login: Autenticacion de usuarios y generacion de token Bearer.

### Comercios y Productos (/api/v1/comercios)
- GET /api/v1/comercios: Consulta publica de comercios activos. Permite filtrar por parametro categoria (ej. ?categoria=RESTAURANTE).
- POST /api/v1/comercios: Creacion de un nuevo comercio (requiere rol ADMIN).
- GET /api/v1/comercios/{id}/productos: Consulta publica de productos de un comercio.
- POST /api/v1/comercios/{id}/productos: Registro de producto con stock inicial (requiere rol ADMIN).
- GET /api/v1/comercios/productos/{id}: Detalle y existencia de un producto especifico.

### Pedidos (/api/v1/pedidos)
- POST /api/v1/pedidos: Creacion de pedido y descuento de stock (requiere rol CLIENTE).
- GET /api/v1/pedidos/mis-pedidos: Consulta del historial de pedidos del cliente autenticado.
- GET /api/v1/pedidos/disponibles: Listado de pedidos activos para entrega (roles REPARTIDOR o ADMIN).
- PATCH /api/v1/pedidos/{id}/estado: Cambio de estado siguiendo el flujo formal (roles REPARTIDOR o ADMIN).
- PATCH /api/v1/pedidos/{id}/cancelar (o DELETE /api/v1/pedidos/{id}): Cancelacion de pedido y restitucion de inventario (roles CLIENTE o ADMIN).

## Instrucciones de Compilacion y Ejecucion

### 1. Compilacion y empaquetado del proyecto
Desde la raiz del proyecto:
```cmd
.\mvnw.cmd clean package
```

### 2. Ejecutar pruebas unitarias y de integracion
```cmd
.\mvnw.cmd test
```

### 3. Iniciar todos los microservicios
Para iniciar los cuatro microservicios de manera coordinada, ejecute el script de arranque:
```cmd
start-services.bat
```
El script levantara cada servicio en una ventana independiente utilizando los paquetes ejecutables construidos.

### 4. Detener los microservicios
Para finalizar los procesos en los puertos 8000, 8081, 8082 y 8083:
```cmd
stop-services.bat
```

## Pruebas de Integracion y Carga (test-fastorder.sh)

El proyecto incluye el script test-fastorder.sh, el cual ejecuta 33 casos de prueba automatizados que verifican:
- Autenticacion, generacion de tokens y rechazo de credenciales invalidas.
- Prevencion de escalamiento de privilegios y control de acceso RBAC.
- Catalogos, filtros de categoria y creacion de productos.
- Creacion de pedidos, verificacion del costo de envio de Q20.00 y total calculado.
- Rollback transaccional ante solicitudes de stock insuficiente.
- Transiciones legales e ilegales de la maquina de estados.
- Cancelacion de pedidos con verificacion de restauracion de stock.
- Prueba de estres concurrente contra inventario critico.

Para ejecutar la suite de pruebas (utilizando Git Bash o terminal bash):
```bash
./test-fastorder.sh
```

En caso de requerir evaluar el Gateway en un puerto alternativo (por ejemplo 8080):
```bash
PORT=8080 ./test-fastorder.sh
```
El script finalizara con codigo de salida 0 si todas las validaciones son exitosas.
