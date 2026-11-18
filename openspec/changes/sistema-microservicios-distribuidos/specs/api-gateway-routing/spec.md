# Delta for api-gateway-routing

Capability nueva: no existe spec previa en `openspec/specs/`. Todo requisito es observable por HTTP en el punto de entrada único.

## ADDED Requirements

### Requirement: Punto de entrada único para clientes

El gateway MUST ser el único componente publicado para tráfico de negocio y MUST encaminar todas las operaciones REST documentadas. Los servicios de negocio MUST NOT exponerse directamente al host.

#### Scenario: Operación de negocio por el gateway

- GIVEN el entorno en ejecución
- WHEN un cliente ejecuta una operación documentada contra el puerto de entrada
- THEN la operación se atiende y responde con su contrato documentado
- AND solo el gateway y Eureka tienen puerto publicado al host

### Requirement: Rutas declaradas por nombre lógico

Las rutas MUST declararse explícitamente como `lb://servicio-pedidos` y `lb://servicio-inventario`. El gateway MUST NOT usar rutas basadas en host fijo y SHOULD NOT depender de un localizador automático de descubrimiento, para que la ruta sea auditable.

#### Scenario: Ruta declarada hacia cada servicio

- GIVEN las rutas `lb://` configuradas
- WHEN se solicita un endpoint de pedidos y uno de inventario por el gateway
- THEN ambas respuestas provienen de instancias registradas en Eureka
- AND la configuración muestra las dos rutas `lb://` explícitas

### Requirement: Propagación de identificador de correlación

El gateway MUST aceptar `X-Correlation-Id` entrante, MUST generar uno cuando falte y MUST propagarlo en la llamada descendente. La respuesta MUST incluir el mismo valor.

#### Scenario: Correlación reutilizada

- GIVEN un cliente que envía `X-Correlation-Id: abc-123`
- WHEN la operación atraviesa gateway, pedidos e inventario
- THEN los logs de todos los procesos muestran `abc-123`
- AND la respuesta devuelve `X-Correlation-Id: abc-123`

#### Scenario: Correlación generada

- GIVEN un cliente que no envía el header
- WHEN ejecuta una operación
- THEN la respuesta incluye un `X-Correlation-Id` generado
- AND ese valor aparece en los logs de todos los procesos intervinientes

### Requirement: Aislamiento de fallos en la entrada

El gateway MUST aplicar timeout explícito por intento y circuit breaker por ruta, de modo que una dependencia degradada produzca una respuesta de error tipada en lugar de una espera indefinida. Los umbrales numéricos SHOULD registrarse en la configuración y la evidencia MUST reportar el valor usado `[umbral propuesto, ratificable tras primera medición]`.

#### Scenario: Servicio destino no disponible

- GIVEN ningún destino sano para una ruta
- WHEN un cliente llama esa ruta
- THEN responde `503` con cuerpo de error tipado en un tiempo acotado
- AND el cuerpo incluye el identificador de correlación y un código de error estable

### Requirement: Exposición de la instancia que atendió

Toda respuesta de negocio MUST incluir `X-Service-Instance` con el identificador de la instancia que la produjo. Ese header MUST NOT sustituir la evidencia del nodo de base de datos ni la del propio registro de descubrimiento.

#### Scenario: Instancia identificada por request

- GIVEN dos instancias de pedidos `UP`
- WHEN se ejecutan requests sucesivas por el gateway
- THEN cada respuesta informa `X-Service-Instance`
- AND el valor coincide con un `instance-id` presente en `GET /eureka/apps`
