# Delta for order-management

Capability nueva: no existe spec previa en `openspec/specs/`.

> `[depende de A1]` Requisitos sujetos a confirmación del dominio Pedidos + Inventario. Los códigos HTTP son decisiones de contrato propuestas, ratificables en diseño.

## ADDED Requirements

### Requirement: Creación válida de pedido (P04)

El pedido MUST tener cliente, fecha, estado, total y una o más líneas con SKU, cantidad y precio. Sin líneas, o con cantidad menor o igual a cero, MUST rechazarse sin persistirse.

#### Scenario: Pedido con líneas válidas

- GIVEN al menos una línea con SKU y cantidad mayor que cero
- WHEN se ejecuta `POST /api/pedidos`
- THEN responde `201` con el pedido y su estado final
- AND el pedido queda recuperable por su identificador

#### Scenario: Líneas inválidas

- GIVEN un pedido sin líneas o con cantidad cero o negativa
- WHEN se ejecuta `POST /api/pedidos`
- THEN responde `400` con error tipado que identifica el campo
- AND no se persiste ningún pedido

### Requirement: Total calculado con precios de inventario

El total MUST calcularse con los precios vigentes de inventario; los precios enviados por el cliente MUST NOT ser su fuente.

#### Scenario: Precio del cliente ignorado

- GIVEN una línea que envía un precio distinto al vigente
- WHEN se crea el pedido
- THEN el total usa el precio de inventario
- AND la respuesta evidencia el precio aplicado por línea

### Requirement: Reserva atómica de todas las líneas

La reserva MUST ser atómica por pedido: todas las líneas o ninguna. MUST NOT persistirse un pedido confirmado con reserva parcial.

#### Scenario: Reserva completa

- GIVEN stock suficiente para todas las líneas
- WHEN se crea el pedido
- THEN el pedido queda `CONFIRMADO`
- AND cada SKU descuenta exactamente la cantidad pedida

#### Scenario: Una línea sin stock

- GIVEN stock suficiente en una línea e insuficiente en otra
- WHEN se crea el pedido
- THEN el pedido queda `RECHAZADO`
- AND ningún SKU descuenta stock

### Requirement: Rechazo por stock insuficiente

Sin stock suficiente, la respuesta MUST identificar los SKU faltantes y el pedido MUST quedar `RECHAZADO` sin reserva aplicada.

#### Scenario: SKU faltantes informados

- GIVEN una línea cuya cantidad supera el stock disponible
- WHEN se crea el pedido
- THEN responde `409` con cuerpo de error que lista los SKU faltantes
- AND el estado persistido es `RECHAZADO`

### Requirement: Estados y transiciones inmutables

El pedido MUST respetar `PENDIENTE` → `CONFIRMADO` | `RECHAZADO` y `CONFIRMADO` → `CANCELADO`. Un pedido `CANCELADO` o `RECHAZADO` MUST NOT volver a confirmarse.

#### Scenario: Cancelación con liberación best-effort

- GIVEN un pedido `CONFIRMADO`
- WHEN se ejecuta `POST /api/pedidos/{id}/cancelar`
- THEN el pedido pasa a `CANCELADO`
- AND el fallo de la liberación se registra con `WARN` y métrica sin revertir la cancelación

#### Scenario: Transición prohibida

- GIVEN un pedido `CANCELADO`
- WHEN se intenta confirmar o cancelar de nuevo
- THEN responde `409` con error tipado
- AND el estado no cambia

### Requirement: Selector de nodo coherente con la operación (P05, P07)

Las escrituras MUST resolverse en el primario. `GET /api/pedidos/{id}` MUST declararse lectura crítica (primario); `GET /api/pedidos` MUST resolverse en réplica sana cuando exista.

#### Scenario: Nodo por tipo de operación

- GIVEN el cluster sano
- WHEN se ejecutan una creación, una consulta crítica y un listado
- THEN `X-Data-Source` informa `PRIMARY`, `PRIMARY` y una réplica
- AND la evidencia del lado de PostgreSQL coincide con esos valores

### Requirement: Aislamiento del dominio

Pedidos MUST ser dueño exclusivo de su base y MUST NOT leer tablas de inventario ni compartir entidades. El stock MUST obtenerse sólo por contrato HTTP.

#### Scenario: Frontera respetada

- GIVEN el esquema y la configuración de pedidos
- WHEN se auditan sentencias y conexiones
- THEN no hay referencias a tablas de inventario
- AND todo dato de stock proviene de la respuesta de inventario
