# Delta for inventory-management

Capability nueva: no existe spec previa en `openspec/specs/`.

> `[depende de A1]` Todos los requisitos de este archivo cambian si no se confirma el dominio Pedidos + Inventario. Los códigos HTTP son decisiones de contrato propuestas en esta fase y ratificables en diseño.

## ADDED Requirements

### Requirement: Stock por SKU con precio vigente

Inventario MUST mantener por SKU la cantidad disponible y el precio vigente, y MUST exponerlos por consulta. Las consultas de stock MUST resolverse en réplica sana cuando exista, sin requerir el primario.

#### Scenario: Consulta de stock en réplica

- GIVEN stock cargado para un SKU y réplicas sanas
- WHEN se ejecuta `GET /api/inventario/{sku}`
- THEN responde cantidad y precio vigentes
- AND `X-Data-Source` informa una réplica, confirmado por la evidencia del lado de PostgreSQL

### Requirement: Reserva atómica por pedido

La reserva MUST aplicarse como una sola unidad por `pedidoId`: todas las líneas o ninguna. Una línea sin stock suficiente MUST impedir la reserva completa y MUST informar los SKU faltantes.

#### Scenario: Reserva completa aplicada

- GIVEN stock suficiente para todas las líneas solicitadas
- WHEN se ejecuta `POST /api/inventario/reservas`
- THEN la reserva se registra asociada al `pedidoId`
- AND cada SKU descuenta exactamente su cantidad

#### Scenario: Reserva rechazada sin efectos

- GIVEN stock insuficiente en al menos una línea
- WHEN se ejecuta la reserva
- THEN responde `409` con los SKU faltantes
- AND ningún SKU descuenta stock

### Requirement: Idempotencia de la reserva por pedido

La reserva MUST ser idempotente por `pedidoId`. Un reintento sobre el mismo pedido MUST NOT duplicar el consumo de stock y MUST devolver un resultado equivalente al primero.

#### Scenario: Reintento sin doble consumo

- GIVEN una reserva ya aplicada para un `pedidoId`
- WHEN llega la misma solicitud de reserva otra vez
- THEN el stock consumido no cambia
- AND la respuesta es equivalente a la del primer intento

#### Scenario: Reserva repetida con cantidades distintas

- GIVEN una reserva existente para un `pedidoId`
- WHEN llega una solicitud con cantidades distintas para ese pedido
- THEN responde `409` con error tipado de conflicto de reserva
- AND el stock no cambia

### Requirement: Liberación idempotente de reserva

La liberación MUST reponer el stock reservado por `pedidoId` y MUST ser idempotente: liberar dos veces MUST NOT reponer dos veces. La operación MUST NOT fallar por una reserva inexistente ni por una liberación ya aplicada.

#### Scenario: Liberación única

- GIVEN una reserva aplicada
- WHEN se ejecuta `POST /api/inventario/reservas/{pedidoId}/liberacion`
- THEN el stock vuelve a su valor previo a la reserva
- AND una segunda liberación no modifica el stock

#### Scenario: Liberación de reserva inexistente

- GIVEN un `pedidoId` sin reserva
- WHEN se solicita la liberación
- THEN responde de forma idempotente sin error de servidor
- AND el stock permanece intacto

### Requirement: Ajuste de existencias por el primario

El ajuste de stock MUST ser una escritura resuelta en el primario y MUST NOT permitir cantidades negativas.

#### Scenario: Reposición aplicada

- GIVEN un SKU existente
- WHEN se ejecuta `PUT /api/inventario/{sku}` con una cantidad válida
- THEN el stock resultante refleja el ajuste
- AND `X-Data-Source` informa `PRIMARY`, confirmado por la evidencia del lado de PostgreSQL

#### Scenario: Ajuste inválido

- GIVEN un ajuste que dejaría stock negativo
- WHEN se ejecuta el ajuste
- THEN responde `400` con error tipado
- AND el stock no cambia

### Requirement: Propiedad exclusiva de los datos de stock

Inventario MUST ser dueño exclusivo de su base y MUST NOT leer tablas de pedidos ni compartir entidades con ese servicio. Inventario MUST NOT invocar a pedidos.

#### Scenario: Dirección de dependencia respetada

- GIVEN la implementación de inventario
- WHEN se auditan sus dependencias y sentencias
- THEN no existe cliente hacia el servicio de pedidos
- AND no existe sentencia contra tablas de pedidos
