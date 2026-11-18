# Proposal: sistema-microservicios-distribuidos

## Intent

Problema de negocio: un negocio que vende productos y controla stock no puede permitirse que una venta se pierda o que se prometa stock inexistente cuando un componente falla. Hoy no existe sistema alguno: no hay registro de pedidos, ni control de disponibilidad, ni forma de seguir operando si una instancia o un nodo de base de datos se cae.

Resultado esperado, en términos de negocio:

- Un pedido se confirma o se rechaza en una sola operación, con decisión de stock autoritativa y sin sobreventa.
- La operación comercial continúa durante fallos parciales: una instancia de servicio caída o una réplica de lectura caída no detienen las ventas.
- El stock consultado inmediatamente después de una venta refleja esa venta; no se promete lo ya comprometido.
- La recuperación es operativa: el componente se reinicia y vuelve a prestar servicio sin intervención manual ni reparación de datos.

## Supuesto A1 — Dominio de negocio (CONFIRMADO EL 2026-11-18)

El dominio **Pedidos** (órdenes de venta) + **Inventario** (stock por producto) fue confirmado por el usuario humano el 2026-11-18. Queda cerrada la única decisión de producto que estaba abierta.

Motivo de la elección: el enunciado ya nombra `lb://servicio-pedidos`, y la relación pedido↔stock produce una comunicación inter-servicio con valor de negocio real, no una llamada decorativa.

Impacto si el humano cambia el dominio:

- Se reescriben los capabilities `order-management` e `inventory-management` (reglas, estados, operaciones) y los entregables de W2–W3.
- Cambian las trazas esperadas de P04 y el guion del video.
- **No** se ve afectado lo estructural: descubrimiento, gateway, balanceo, cluster replicado, routing de DataSources, Compose y escenarios P01–P03, P05–P08 son agnósticos al dominio.
- Costo estimado de cambio: los paquetes W2, W3 y W4 más los informes; el resto del plan no se reabre.

## Scope

### In Scope

- Dos servicios de negocio con responsabilidades y datos separados: `servicio-pedidos` y `servicio-inventario`.
- Dos instancias simultáneas de **cada** servicio de negocio (mínimo exigido: dos de uno).
- Eureka Server standalone con registro, expulsión y reincorporación por nombre lógico.
- Spring Cloud Gateway como punto de entrada único, con rutas `lb://` declaradas.
- Comunicación `pedidos → inventario` por OpenFeign sobre nombre lógico, con Resilience4j.
- PostgreSQL 16: un primario y dos réplicas asíncronas por streaming WAL, con una base por servicio.
- Routing de persistencia propio: escrituras al primario, lecturas round-robin sobre réplicas sanas, lectura crítica al primario, degradado al primario si no hay réplicas.
- Docker Compose reproducible, sin secretos versionados.
- Ejecución y documentación de P01–P08 más P06b, con evidencia generada por script.
- README técnico, diagrama de arquitectura, colección de requests, ADRs y video demostrativo.

### Out of Scope

- Failover automático de PostgreSQL, promoción del primario o recuperación automática del cluster (excluido por el enunciado).
- RabbitMQ, Sagas, outbox transaccional o cualquier transacción distribuida.
- Autenticación, autorización, multi-tenancy y auditoría regulatoria.
- Interfaz de usuario o frontend web; el sistema expone API HTTP y una colección de requests.
- Balanceo de conexiones en un proxy externo (PgBouncer u otro) y pooling entre servicios.
- Observabilidad completa (tracing distribuido, dashboards); solo logs estructurados y métricas Micrometer mínimas.
- Despliegue en Kubernetes o nube, multi-región y geo-replicación.

## Capabilities

### New Capabilities

- `service-discovery`: registro de instancias con identificador único, localización por nombre lógico, expulsión y reincorporación.
- `http-load-balancing`: distribución de solicitudes entre dos instancias del mismo servicio, identificación de la instancia que responde y continuidad ante caída de una de ellas.
- `api-gateway-routing`: entrada única, rutas `lb://`, propagación de `X-Correlation-Id`, timeouts y circuit breaker.
- `order-management`: operaciones y ciclo de vida del pedido, validación de líneas y cálculo de total.
- `inventory-management`: stock por producto, reserva idempotente por pedido, liberación y ajuste de existencias.
- `inter-service-communication`: llamada real por nombre lógico, resiliencia, errores tipados y consistencia sin transacciones distribuidas.
- `replicated-persistence-routing`: selección de nodo de base de datos por operación, lectura crítica, salud de réplicas y modo degradado.
- `postgres-replication-cluster`: primario más réplicas asíncronas, una base por servicio, aislamiento de datos y reincorporación condicionada.
- `deployment-environment`: entorno Compose reproducible desde cero, redes, salud, dependencias y ausencia de secretos.
- `experimental-evidence`: P01–P08 y P06b ejecutados con procedimiento reproducible, evidencia cruda y análisis de tiempos y errores.
- `technical-documentation`: README operativo, diagrama, colección de requests, ADRs y enlace de video.

### Modified Capabilities

- None. No existen specs previas en `openspec/specs/`.

## Operaciones REST

| Servicio | Operación | Propósito | Nodo esperado |
| :-- | :-- | :-- | :-- |
| pedidos | `POST /api/pedidos` | Crear pedido con líneas y reservar stock | PRIMARY (escritura) |
| pedidos | `GET /api/pedidos/{id}` | Consultar un pedido recién creado | PRIMARY (`@CriticalRead`) |
| pedidos | `GET /api/pedidos` | Listar y filtrar pedidos por estado | REPLICA_1 / REPLICA_2 |
| pedidos | `POST /api/pedidos/{id}/cancelar` | Cancelar pedido confirmado y liberar reserva | PRIMARY |
| inventario | `GET /api/inventario` | Listar stock disponible | REPLICA_1 / REPLICA_2 |
| inventario | `GET /api/inventario/{sku}` | Consultar stock de un producto | REPLICA_1 / REPLICA_2 |
| inventario | `POST /api/inventario/reservas` | Reservar stock para un pedido | PRIMARY |
| inventario | `POST /api/inventario/reservas/{pedidoId}/liberacion` | Liberar reserva (compensación) | PRIMARY |
| inventario | `PUT /api/inventario/{sku}` | Ajustar existencias (reposición) | PRIMARY |

## Reglas de negocio del dominio asumido

- Un pedido tiene cliente, fecha, estado, total y una o más líneas; cada línea referencia un SKU, cantidad y precio unitario.
- Un pedido no puede crearse sin líneas ni con cantidades menores o iguales a cero.
- El total se calcula con los precios vigentes en inventario, no con precios enviados por el cliente.
- La reserva de stock es atómica a nivel de pedido: o se reservan todas las líneas o ninguna.
- Si no hay stock suficiente, el pedido queda `RECHAZADO`, no se reserva nada y la respuesta identifica los SKU faltantes.
- Si inventario no responde (timeout o circuito abierto), el pedido queda `PENDIENTE` y la respuesta es un error tipado; no se permite reserva parcial ni pedido confirmado sin stock comprometido.
- La reserva es idempotente por `pedidoId`: un reintento por resiliencia no duplica el consumo de stock.
- Estados: `PENDIENTE` → `CONFIRMADO` o `RECHAZADO`; `CONFIRMADO` → `CANCELADO` con liberación de reserva best-effort.
- Un pedido `CANCELADO` o `RECHAZADO` no puede volver a confirmarse.
- Inventario es dueño exclusivo de su stock; pedidos no lee tablas de inventario ni comparte entidades con él.

## Criterios de aceptación funcional (P01–P08 + P06b)

Umbrales propuestos; se ratifican o ajustan con la primera medición real.

| Escenario | Criterio medible |
| :-- | :-- |
| P01 Descubrimiento | `/eureka/apps` lista las 4 instancias con identificadores distintos; una request por el gateway se resuelve por nombre lógico |
| P02 Balanceo HTTP | 100 requests: al menos 95 exitosas, ambas instancias responden y ninguna concentra más del 75 % |
| P03 Fallo de instancia | Con una instancia detenida, al menos 90 % de requests exitosas tras la detección; recuperación observable en 30 s o menos, con ventana de errores transitorios contada |
| P04 Comunicación entre servicios | Un pedido se crea atravesando pedidos → inventario con traza de correlación en ambos logs; el caso de inventario caído devuelve error tipado |
| P05 Routing de base de datos | Cero sentencias de escritura en réplicas, contrastado con `pg_stat_activity` y `log_statement=all`; lecturas ordinarias repartidas entre ambas réplicas |
| P06 Fallo de réplica | Con una réplica detenida, el 100 % de las lecturas posteriores a la detección (10 s o menos) se resuelven en la réplica restante |
| P06b Modo degradado | Con ambas réplicas detenidas, el 100 % de las lecturas se resuelven en el primario con WARN y métrica, sin error al cliente |
| P07 Consistencia de lectura | En 20 corridas de escritura seguida de lectura crítica, el 100 % devuelve el dato recién creado, con `replay_lag` registrado en el instante |
| P08 Recuperación de réplica | La réplica reiniciada vuelve a recibir lecturas en 60 s o menos, solo tras health check verde y streaming activo |

## Approach

- Eureka descubre procesos, Spring Cloud LoadBalancer elige instancia HTTP y `AbstractRoutingDataSource` elige nodo de base de datos: tres decisiones independientes, documentadas por separado.
- Routing de persistencia con `LazyConnectionDataSourceProxy` envolviendo el `RoutingDataSource`, `ThreadLocal` de contexto y aspecto sobre `@Transactional`; `@CriticalRead` fuerza primario. Round-robin determinista sobre réplicas sanas; fallback a primario cuando no hay ninguna.
- Salud de réplicas por sondeo periódico con timeout corto; una réplica se excluye por health check, no por excepción del driver, y se reincorpora solo cuando responde.
- Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x, Maven multi-módulo y Docker Compose con 9 contenedores; solo gateway y Eureka publicados al host.
- Evidencia no circular: `X-Data-Source` y `X-Service-Instance` se contrastan siempre con el nodo real y con los logs de aplicación.

## Paquetes de trabajo cubiertos

Este cambio abarca el sistema completo. Los paquetes no se convierten en cambios separados; viven como grupos de tareas.

| Paquete | Entregable verificable |
| :-- | :-- |
| W0 | `mvn -q verify` verde con módulos vacíos y commit inicial publicado |
| W1 | `/eureka/apps` lista una instancia registrada |
| W2 | Migraciones Flyway aplican en el primario; tests de repositorio verdes |
| W3 | Contratos OpenAPI documentados y colección de requests ejecutable |
| W4 | `POST /api/pedidos` reserva stock real; inventario caído devuelve error tipado |
| W5 | Request por gateway llega a instancia rotativa con header de instancia visible |
| W6 | Lecturas repartidas, escrituras al primario, réplica caída excluida |
| W7 | `docker compose up -d` deja 9 contenedores `healthy` |
| W8 | `docs/informes/P01..P08.md` con evidencia generada por script |
| W9 | README, diagrama, colección y ADRs completos |
| W10 | Video de 3 a 5 minutos accesible desde el README |

## Affected Areas

| Area | Impact | Description |
| :-- | :-- | :-- |
| `pom.xml` | New | Parent POM y módulos del reactor |
| `eureka-server/` | New | Registro y descubrimiento |
| `gateway/` | New | Rutas `lb://`, correlación, timeouts |
| `servicio-pedidos/` | New | Dominio pedidos, API, Feign, routing |
| `servicio-inventario/` | New | Dominio stock, reservas, routing |
| `infra/postgres/` | New | Inicialización, bases por servicio, réplicas |
| `docker-compose.yml` | New | 9 contenedores, red `backend`, volúmenes |
| `scripts/` | New | Carga, fallos y recolección de evidencia |
| `docs/` | New | Arquitectura, informes P01–P08, ADRs |
| `requests/` | New | Colección reproducible |
| `README.md` | New | Operación, recuperación y enlace de video |

## Risks

| Riesgo | Tipo | Prob. | Mitigación |
| :-- | :-- | :-- | :-- |
| A1 no confirmado: el dominio cambia | Producto | Media | Contener el impacto en W2–W4; confirmar antes de iniciar W2 |
| Sobreventa por reserva no atómica o reintento duplicado | Producto | Media | Reserva transaccional por pedido e idempotencia por `pedidoId` |
| Interpretar los headers propios como prueba de routing | Producto | Alta | Contraste obligatorio con `pg_stat_activity`, `log_statement=all` y `replay_lag` |
| Imagen PostgreSQL con replicación no disponible o movida | Técnico | Media | Prueba de humo temprana; alternativa `postgres:16` con inicialización propia |
| Evidencia contaminada por recursos insuficientes de Docker | Técnico | Media | Límites de memoria, JVM acotada y documentación del entorno |
| Instancias registradas con el mismo identificador | Técnico | Media | Identificador único por contenedor y dirección IP preferida |
| Errores transitorios mal explicados en P03 | Técnico | Alta | Medir la ventana, contar errores y explicarla por caché del balanceador y lease de Eureka |
| Lag de replicación que invalida P07 | Técnico | Media | Lectura crítica al primario y registro del lag del instante |

## Rollback Plan

Proyecto greenfield sin datos productivos, por lo que el rollback es local y no destructivo:

- Revertir los commits del paquete afectado; cada paquete es aditivo y verificable de forma aislada.
- `docker compose down -v` elimina contenedores, red y volúmenes; el entorno se reconstruye desde cero con `docker compose up -d`.
- Si el routing de DataSources resulta inestable, se desactiva el aspecto y los servicios operan contra el primario únicamente: el sistema sigue funcional y P05–P08 quedan pendientes, sin pérdida de datos.
- Los artefactos publicados fuera del repositorio (video, remoto de GitHub) no son reversibles por commit; se corrigen con una nueva publicación y se documenta la corrección.

## Dependencies

- Docker y Docker Compose operativos con recursos suficientes para 9 contenedores.
- JDK 21 y Maven 3.9 disponibles.
- A1 confirmado por el humano el 2026-11-18: la dependencia que bloqueaba W2 queda cerrada.
- Verificación de la matriz de compatibilidad Spring Boot 3.5.x / Spring Cloud 2025.0.x antes de generar el parent POM.
- Repositorio remoto en GitHub para publicar entregables.

## Success Criteria

- [x] A1 confirmado (Pedidos + Inventario) el 2026-11-18, con impacto registrado en specs y tareas.
- [ ] 9 contenedores `healthy` y 4 instancias de servicio registradas en Eureka.
- [ ] Un pedido atraviesa gateway → pedidos → inventario → PostgreSQL y devuelve 2xx con headers de instancia y de nodo.
- [ ] P01–P08 y P06b documentados con evidencia generada por script y tiempos de detección y recuperación medidos.
- [ ] `mvn verify` verde con tests del selector de réplicas y de las reglas de negocio.
- [ ] Entorno reproducible desde cero siguiendo solo el README, sin secretos en el repositorio.
- [ ] Entregables 1–6 completos, incluido el video accesible desde el README.

## Supuestos abiertos para el humano

1. **A1 — Dominio de negocio**: Pedidos + Inventario. **Confirmado el 2026-11-18**; sin acción pendiente.
2. Umbrales numéricos de aceptación (95 %, 90 %, 100 %, 10 s, 30 s, 60 s): propuestos para hacer medible la rúbrica; se ratifican con la primera medición.
3. Profundidad de las operaciones de inventario: ajuste de stock incluido como operación de escritura mínima; no se asume catálogo de productos ni gestión de precios más allá del precio por SKU.
