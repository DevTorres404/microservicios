# Delta for inter-service-communication

Capability nueva: no existe spec previa en `openspec/specs/`. Dependencia unidireccional: pedidos → inventario. Todo requisito es observable por HTTP, logs de correlación o respuesta de error.

## ADDED Requirements

### Requirement: Llamada real por nombre lógico (P04)

La operación de negocio que requiere stock MUST ejecutar una llamada HTTP real de pedidos a inventario resuelta por nombre lógico vía descubrimiento. La llamada MUST NOT usar URL, host ni puerto fijos, y MUST NOT sustituirse por acceso directo a la base de inventario.

#### Scenario: Operación de negocio atraviesa dos servicios

- GIVEN el sistema operativo
- WHEN se crea un pedido con stock suficiente
- THEN el pedido se confirma con stock reservado
- AND los logs de pedidos e inventario comparten el mismo `X-Correlation-Id`

### Requirement: Timeout explícito y acotado

Toda llamada saliente MUST declarar timeout de conexión y de lectura. Una dependencia que no responde MUST producir un fallo acotado en el tiempo, nunca una espera indefinida. Los valores MUST quedar registrados en configuración y en la evidencia `[umbral propuesto, ratificable tras primera medición]`.

#### Scenario: Dependencia que no responde

- GIVEN inventario detenido o con respuesta bloqueada
- WHEN se crea un pedido
- THEN la llamada falla dentro del timeout configurado
- AND el cliente recibe una respuesta de error tipada, no un cuelgue

### Requirement: Circuit breaker con degradación explícita

La llamada MUST protegerse con circuit breaker. Al superar el umbral de fallos, el circuito MUST abrirse y las llamadas siguientes MUST fallar rápido sin intentar la dependencia. El umbral y la ventana de semi-apertura MUST ser configurables y reportados `[umbral propuesto, ratificable tras primera medición]`.

#### Scenario: Apertura del circuito

- GIVEN inventario caído y fallos consecutivos
- WHEN se supera el umbral configurado
- THEN las llamadas siguientes fallan rápido sin alcanzar la red
- AND el estado del circuito aparece en logs y métrica

### Requirement: Error tipado hacia el cliente

Cuando inventario no responde por timeout o circuito abierto, el sistema MUST responder `503` con cuerpo `application/problem+json` (RFC 9457) que incluya un código estable de dependencia no disponible y el identificador de correlación. MUST NOT devolverse un `500` genérico ni un cuerpo sin estructura.

#### Scenario: Error tipado por inventario no disponible

- GIVEN inventario caído
- WHEN se crea un pedido
- THEN responde `503` con `type`, `title`, `detail` y `code` de dependencia no disponible
- AND el mismo código aparece en el log de pedidos

### Requirement: Compensación y consistencia sin transacción distribuida `[depende de A1]`

El sistema MUST NOT usar transacciones distribuidas, XA ni two-phase commit. Al fallar la comunicación, MUST NOT existir pedido confirmado sin stock comprometido, y cualquier reserva parcial MUST liberarse. La idempotencia de la reserva MUST cubrir los reintentos.

#### Scenario: Fallo posterior a una reserva parcial

- GIVEN un pedido multilínea cuya reserva falla a mitad de camino
- WHEN pedidos recibe el error
- THEN el stock consumido se libera
- AND el pedido no queda `CONFIRMADO`

#### Scenario: Reintento no duplica consumo

- GIVEN una reserva ya aplicada para un `pedidoId`
- WHEN el cliente de resiliencia reintenta la misma reserva
- THEN el stock consumido no aumenta
- AND la respuesta es equivalente a la del primer intento

### Requirement: Trazabilidad de la llamada entre servicios

Cada llamada inter-servicio MUST propagar el identificador de correlación y MUST registrar en log la ruta lógica invocada, la instancia destino y el resultado. La evidencia de P04 MUST consistir en esos logs, no en la configuración del cliente HTTP.

#### Scenario: Traza legible en ambos extremos

- GIVEN un pedido creado con éxito
- WHEN se revisan los logs de los dos servicios
- THEN ambos muestran el mismo `X-Correlation-Id` y la traza de la llamada
- AND la instancia destino coincide con una instancia registrada
