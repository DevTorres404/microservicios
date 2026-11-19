# Tasks: sistema-microservicios-distribuidos

Desglose de implementación derivado de `proposal.md`, de los 11 deltas en `specs/`, de `design.md` y de `PLAN_IMPLEMENTACION.md` (§7 paquetes W0–W10).
Estado inicial de la planificación: todas las casillas comenzaban en `[ ]`. El avance real verificado se registra en `apply-progress.md`.

## Formato y convenciones

Cada tarea declara: ID estable, descripción accionable en una línea, criterio de aceptación observable, dependencias, capability/spec que satisface y comando de verificación exacto. El grupo (W0…W10) se expresa en el ID y en el encabezado de sección.

Códigos de spec usados en el campo `Spec:` — todos bajo `openspec/changes/sistema-microservicios-distribuidos/specs/`:

| Código | Spec |
| :-- | :-- |
| SE | `service-discovery` |
| LB | `http-load-balancing` |
| GW | `api-gateway-routing` |
| OM | `order-management` |
| IM | `inventory-management` |
| ISC | `inter-service-communication` |
| RPR | `replicated-persistence-routing` |
| PRC | `postgres-replication-cluster` |
| DE | `deployment-environment` |
| EE | `experimental-evidence` |
| TD | `technical-documentation` |

Los comandos se escriben en forma POSIX (Git Bash o WSL en Windows). Cuando existe contraparte `.ps1`, la verificación equivalente es el script homónimo. Los comandos de `mvn` se ejecutan desde la raíz del reactor.

## Review Workload Forecast

```
Review Workload Forecast
- Estimated changed lines (authored, additions + deletions): ≈10 000–14 000 (código Java ≈4 500–6 000, tests ≈2 000–3 000, Compose/YAML/scripts ≈1 200, documentación e informes ≈2 500–3 500; excluye artefactos generados)
- Chained PRs recommended: Yes
- 400-line budget risk: High
- Suggested slice boundaries: 11 slices por paquete — (1) W0+W1 andamiaje y Eureka, (2) W2 dominio y migraciones, (3) W3 API REST, (4) W4 Feign y resiliencia, (5) W5 gateway, (6) W6a núcleo de `libs/persistence-routing` (W6-1..W6-10), (7) W6b cableado por servicio (W6-11..W6-20), (8) W7 entorno Compose, (9) W8 evidencia P01–P08/P06b, (10) W9 documentación, (11) W10 video
- Decision needed before apply: No
```

Chain strategy: pending

Aclaraciones obligatorias sobre este forecast:

- El usuario desactivó el freno por carga de review (presupuesto sin límite), por eso `Decision needed before apply` es `No`. El forecast es informativo y no bloquea la fase de aplicación.
- El valor `Chain strategy: pending` es honesto: la estrategia de encadenado no fue decidida por el equipo. Si se decide encadenar, la recomendación es `stacked-to-main` con los 11 slices de arriba, porque cada paquete es aditivo y verificable de forma aislada (§7 del plan).
- La estimación es alta por naturaleza del cambio: es un sistema completo greenfield, no un ajuste. Supera el presupuesto de 400 líneas en dos órdenes de magnitud.

### Unidades de trabajo sugeridas

| Unidad | Objetivo | PR | Comando de test focalizado | Arnés de ejecución | Frontera de rollback |
| :-- | :-- | :-- | :-- | :-- | :-- |
| 1 | Andamiaje + Eureka operativo | PR 1 | `mvn -q -pl eureka-server -am test` | `bash scripts/eureka-lease-probe.sh` | W0-* y W1-*; revertir no afecta otros módulos |
| 2 | Dominio + migraciones por servicio | PR 2 | `mvn -q -pl servicio-inventario,servicio-pedidos -am test` | Testcontainers `postgres:16` | migraciones V1 y entidades; sin consumidores HTTP aún |
| 3 | API REST de ambos servicios | PR 3 | `mvn -q -pl servicio-pedidos,servicio-inventario -am test` | `curl` local contra el servicio levantado | controladores, DTOs y manejo de errores |
| 4 | Feign + Resilience4j + compensación | PR 4 | `mvn -q -pl servicio-pedidos -am test -Dtest='*Feign*,*Compensation*'` | inventario detenido a mano, `POST /api/pedidos` | cliente Feign, configuración de CB y liberación compensatoria |
| 5 | Gateway con rutas `lb://` | PR 5 | `mvn -q -pl gateway -am test` | `curl` a `:8080/api/pedidos` | módulo gateway completo |
| 6 | Núcleo de routing (selector, registry, aspecto) | PR 6 | `mvn -q -pl libs/persistence-routing -am test` | N/A: unitario puro, sin infraestructura | paquete `libs/persistence-routing`, sin consumidores |
| 7 | Cableado de routing en ambos servicios | PR 7 | `mvn -q -pl servicio-pedidos -am test -Dtest='RoutingOrderGuard*'` | Testcontainers + `X-Data-Source` | clases de configuración de pools/proxy y `routing.enabled` |
| 8 | Entorno Compose de 9 contenedores | PR 8 | `docker compose config --quiet` | `docker compose up -d` y `docker compose ps` | `docker-compose*.yml`, `infra/postgres/`, `Dockerfile` |
| 9 | Evidencia P01–P08 + P06b | PR 9 | N/A: evidencia experimental, no testeable en CI | `bash scripts/evidence-p0*.sh` sobre Compose | `scripts/` e `docs/informes/` |
| 10 | Documentación técnica | PR 10 | N/A: entregable documental | lectura del README en clon limpio | `README.md`, `docs/`, `requests/` |
| 11 | Video demostrativo | PR 11 | N/A: entregable audiovisual | `bash scripts/video-drill.sh` antes de grabar | enlace en README y guion; no altera código |

Prioridad de aplicación: no aplicar dos slices a la vez sobre los mismos archivos. El slice 7 depende de 6; el 8 depende de 1, 4, 5 y 7.

---

## W0 — Andamiaje, contrato de build y activación de TDD

- [x] **W0-1** Inicializar el repositorio Git con rama `main` y commit raíz del andamiaje.
  - Aceptación: `HEAD` apunta a `main` y existe al menos un commit raíz.
  - Depende: —
  - Spec: DE (entorno reproducible desde clon limpio, base del entregable)
  - Verificación: `git rev-parse --abbrev-ref HEAD`

- [ ] **W0-2** Crear el repositorio remoto en GitHub y publicar `main` con seguimiento.
  - Aceptación: `origin/main` existe y apunta al commit raíz publicado.
  - Depende: W0-1
  - Spec: DE (reproducibilidad del clon), TD (repositorio público)
  - Verificación: `git ls-remote --heads origin main`

- [x] **W0-3** Crear `.gitignore` con `.env`, `target/`, `*.log`, `.idea/` y `*.iml`.
  - Aceptación: `.env` queda ignorado y los artefactos de build no aparecen en `git status`.
  - Depende: W0-1
  - Spec: DE (ausencia de secretos en el repositorio)
  - Verificación: `git check-ignore -v .env`

- [x] **W0-4** Crear `.env.example` con todas las variables requeridas y valores no productivos, sin secretos reales.
  - Aceptación: el ejemplo lista todas las variables usadas por Compose y `.env` no está versionado.
  - Depende: W0-3
  - Spec: DE (archivo de ejemplo versionado; barrido de secretos)
  - Verificación: `test -f .env.example && grep -qE '^POSTGRES_PASSWORD=' .env.example && ! git ls-files --error-unmatch .env >/dev/null 2>&1`

- [x] **W0-5** Crear `.gitattributes` con `*.sh text eol=lf` y `*.ps1 text eol=crlf`.
  - Aceptación: la regla LF queda declarada para todos los scripts montados en contenedores Linux.
  - Depende: W0-1
  - Spec: DE (arranque reproducible; regla fijada en `openspec/config.yaml`)
  - Verificación: `grep -nE '^\*\.sh text eol=lf$' .gitattributes`

- [x] **W0-6** Verificar en la documentación oficial la matriz Spring Boot 3.5.x / Spring Cloud 2025.0.x y registrar versiones exactas y digests en `docs/decisiones.md` (ADR-10), **antes** de escribir el POM.
  - Aceptación: ADR-10 fija versión exacta de Boot, de Cloud y del JDK, con enlace a la matriz oficial y fecha de consulta.
  - Depende: W0-1
  - Spec: DE (build reproducible), riesgo «matriz de versiones» del proposal
  - Verificación: `grep -A8 'ADR-10' docs/decisiones.md | grep -qE 'Spring Boot 3\.5\.[0-9]+' && grep -A8 'ADR-10' docs/decisiones.md | grep -qE 'Spring Cloud 2025\.0\.[0-9]+'`

- [x] **W0-7** Crear el parent POM `packaging=pom` con `spring-boot-dependencies` y `spring-cloud-dependencies` importados, versiones pinneadas desde ADR-10 y los cinco módulos declarados.
  - Aceptación: ningún módulo declara versión de dependencia; el reactor resuelve sin warnings de versión.
  - Depende: W0-6
  - Spec: DE (build reproducible con `mvn -T 1C verify`)
  - Verificación: `mvn -q -N validate`

- [x] **W0-8** Crear los módulos vacíos `eureka-server`, `gateway`, `servicio-pedidos`, `servicio-inventario` y `libs/persistence-routing` con su `pom.xml` y estructura `src/main/java`.
  - Aceptación: el reactor compila los cinco módulos sin tests.
  - Depende: W0-7
  - Spec: DE (build reproducible), arquitectura de `design.md` §15
  - Verificación: `mvn -q -T 1C -DskipTests package`

- [x] **W0-9** Escribir el primer test real (RED) en `libs/persistence-routing`: `DataSourceKeyTest` que fija el vocabulario cerrado `PRIMARY`, `REPLICA_1`, `REPLICA_2`, e implementarlo (GREEN) con el enum `DataSourceKey`.
  - Aceptación: el test enumera exactamente tres claves y falla si se añade una cuarta; `mvn -q test` ejecuta al menos un test.
  - Depende: W0-8
  - Spec: RPR (vocabulario de nodos), `design.md` §4.1
  - Verificación: `mvn -q -pl libs/persistence-routing -am test`

- [x] **W0-10** Activar `strict_tdd: true` en `openspec/config.yaml` y alinear `rules.apply.tdd` en cuanto `mvn -q test` ejecute al menos un test.
  - Aceptación: la configuración declara TDD estricto y el cambio queda registrado en `strict_tdd_source`.
  - Depende: W0-9
  - Spec: DE (contrato de verificación del proyecto; `strict_tdd_target_gate: W0`)
  - Verificación: `grep -n 'strict_tdd: true' openspec/config.yaml`

- [x] **W0-11** Añadir CI mínimo en `.github/workflows/build.yml` que ejecute `mvn -q verify` con JDK 21.
  - Aceptación: el workflow se dispara en `push` y `pull_request`, y localmente el mismo comando termina en 0.
  - Depende: W0-9
  - Spec: DE (verificación reproducible del build)
  - Verificación: `grep -q 'mvn -q verify' .github/workflows/build.yml`

---

## W1 — Eureka Server standalone

- [x] **W1-1** Implementar `eureka-server` con su `application.yml`: autopreservación desactivada, barrido cada 3000 ms, cache de respuesta 1000 ms, `use-read-only-response-cache=false`, servidor sin registro ni fetch propios.
  - Aceptación: con el servidor arriba, `GET /eureka/apps` responde y la configuración efectiva muestra los cuatro parámetros.
  - Depende: W0-8
  - Spec: SE (registro y expulsión observables), `design.md` §10
  - Verificación: `mvn -q -pl eureka-server -am package`

- [x] **W1-2** Añadir `eureka-server/Dockerfile` multi-stage con usuario no root y healthcheck contra `/actuator/health`.
  - Aceptación: la imagen construye y el healthcheck pasa en estado saludable.
  - Depende: W1-1
  - Spec: DE (healthchecks reales por servicio)
  - Verificación: `docker build -q -t eureka-server:local eureka-server/`

- [x] **W1-3** Crear `scripts/eureka-lease-probe.sh` que registra una instancia efímera contra la API de Eureka, mide su expulsión (≤20 s; la cota inferior es lease 15 s + barrido 3 s, medido 17 s) y su re-registro (≤2 s, medido 995 ms).
  - Aceptación: el script informa lease, expiración medida y re-registro, y termina en 0 con el servidor levantado.
  - Depende: W1-1
  - Spec: SE (renovación, expiración y re-registro), insumo temprano de P03
  - Verificación: `bash scripts/eureka-lease-probe.sh`

---

## W2 — Dominio, migraciones y repositorios por servicio

- [x] **W2-1** Configurar en `servicio-inventario` las propiedades de nodo (`routing.nodes.primary|replica1|replica2`), pool Hikari del primario, `open-in-view=false` y `ddl-auto=validate`.
  - Aceptación: el servicio arranca contra un PostgreSQL local usando solo el nodo `primary`; ningún host queda fijado fuera de variables de entorno.
  - Depende: W0-8
  - Spec: PRC (una base por servicio), RPR (pools por nodo), regla `apply` de `config.yaml`
  - Verificación: `grep -nE 'open-in-view: false|ddl-auto: validate' servicio-inventario/src/main/resources/application.yml`

- [x] **W2-2** Declarar el bean `Flyway` explícito sobre `primaryDataSource` con `spring.flyway.enabled=false` y `locations=classpath:db/migration/inventario`, replicando el patrón en `servicio-pedidos`.
  - Aceptación: la migración se ejecuta solo contra el primario y no contra ningún pool de réplica.
  - Depende: W2-1
  - Spec: PRC (esquema por servicio), ADR-06
  - Verificación: `grep -rn 'primaryDataSource' servicio-inventario/src/main/java servicio-pedidos/src/main/java`

- [x] **W2-3** Escribir la migración `V1__inventario_schema.sql`: SKU con precio y stock, tabla de reserva con unicidad por `pedidoId`, línea de reserva con unicidad por (`pedidoId`, `sku`).
  - Aceptación: la migración aplica en `inventario_db` y las restricciones de unicidad existen en el esquema.
  - Depende: W2-2
  - Spec: IM (stock por SKU, reserva atómica e idempotente)
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='*Migration*'`

- [x] **W2-4** Escribir la migración `V1__pedidos_schema.sql`: pedido con estado restringido por `CHECK` y línea con FK al pedido.
  - Aceptación: la migración aplica en `pedidos_db` y el `CHECK` rechaza estados fuera de `PENDIENTE|CONFIRMADO|RECHAZADO|CANCELADO`.
  - Depende: W2-2
  - Spec: OM (estados y transiciones inmutables)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*Migration*'`

- [x] **W2-5** Implementar entidades JPA y repositorios de `servicio-inventario` (stock, reserva, línea de reserva) sin referencias a tablas de pedidos.
  - Aceptación: las consultas del repositorio solo nombran objetos de `inventario_db`.
  - Depende: W2-3
  - Spec: IM (propiedad exclusiva de los datos de stock)
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='*Repository*'`

- [x] **W2-6** Implementar entidades JPA y repositorios de `servicio-pedidos` (pedido, línea) sin referencias a tablas de inventario.
  - Aceptación: las consultas del repositorio solo nombran objetos de `pedidos_db`.
  - Depende: W2-4
  - Spec: OM (aislamiento del dominio)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*Repository*'`

- [x] **W2-7** Escribir tests de repositorio con Testcontainers `postgres:16`, marcados `@Tag("integration")` y `@Testcontainers(disabledWithoutDocker = true)`, para ambos servicios.
  - Aceptación: los tests de persistencia pasan contra PostgreSQL real y se omiten sin Docker.
  - Depende: W2-5, W2-6
  - Spec: IM, OM (persistencia verificada), `design.md` §14
  - Verificación: `mvn -q -pl servicio-inventario,servicio-pedidos -am test -Dtest='*RepositoryIT'`

- [x] **W2-8** Crear `infra/postgres/init/01-databases.sql`: `pedidos_db` e `inventario_db`, un usuario por base con privilegios solo sobre su base, rol `replicator` sin acceso a las bases de negocio.
  - Aceptación: cada usuario de aplicación no puede conectarse ni consultar la base del otro; `replicator` solo replica.
  - Depende: W2-3, W2-4
  - Spec: PRC (una base por servicio con credenciales propias)
  - Verificación: `grep -cE 'CREATE (DATABASE|ROLE|USER)' infra/postgres/init/01-databases.sql`

---

## W3 — API REST, validación, errores y contratos

- [x] **W3-1** Implementar los endpoints de lectura de inventario: `GET /api/inventario` y `GET /api/inventario/{sku}` con cantidad y precio vigentes.
  - Aceptación: ambas rutas devuelven el contrato documentado y 404 tipado para SKU inexistente.
  - Depende: W2-5
  - Spec: IM (stock por SKU con precio vigente)
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='*InventarioController*'`

- [x] **W3-2** Implementar los endpoints de escritura de inventario: `POST /api/inventario/reservas`, `POST /api/inventario/reservas/{pedidoId}/liberacion` y `PUT /api/inventario/{sku}`.
  - Aceptación: reserva atómica por pedido, liberación idempotente y ajuste que rechaza stock negativo.
  - Depende: W3-1
  - Spec: IM (reserva atómica, idempotencia de reserva y liberación, ajuste por el primario)
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='*Reserva*'`

- [x] **W3-3** Implementar los endpoints de pedidos: `POST /api/pedidos`, `GET /api/pedidos/{id}`, `GET /api/pedidos` y `POST /api/pedidos/{id}/cancelar`.
  - Aceptación: cada ruta responde su contrato y `GET /api/pedidos/{id}` queda marcado como lectura crítica.
  - Depende: W2-6
  - Spec: OM (creación válida, estados, selector de nodo coherente)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*PedidoController*'`

- [x] **W3-4** Escribir (RED) los tests de la máquina de estados del pedido y del cálculo de total: `PENDIENTE→CONFIRMADO|RECHAZADO`, `CONFIRMADO→CANCELADO`, transiciones prohibidas, total con precios de inventario.
  - Aceptación: los tests fallan antes de la implementación y codifican las cinco transiciones de la tabla del proposal.
  - Depende: W2-6
  - Spec: OM (estados y transiciones inmutables, total con precios de inventario)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='PedidoStateMachineTest'`

- [x] **W3-5** Implementar (GREEN) la máquina de estados y el cálculo de total del pedido hasta dejar los tests de W3-4 en verde.
  - Aceptación: ninguna transición prohibida altera el estado y el total ignora el precio enviado por el cliente.
  - Depende: W3-4
  - Spec: OM (estados y total), ISC (consistencia sin transacción distribuida)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='PedidoStateMachineTest'`

- [x] **W3-6** Implementar validación de entrada y `@RestControllerAdvice` con `application/problem+json` (RFC 9457) y códigos estables `400/404/409/503`.
  - Aceptación: pedido sin líneas o con cantidad ≤ 0 responde 400 tipado sin persistir; transición prohibida responde 409 tipado.
  - Depende: W3-3, W3-5
  - Spec: OM (líneas inválidas, transiciones prohibidas), ISC (error tipado)
  - Verificación: `mvn -q -pl servicio-pedidos,servicio-inventario -am test -Dtest='*ErrorHandling*'`

- [x] **W3-7** Añadir filtro que estampa `X-Service-Instance` con el identificador de instancia en toda respuesta de negocio.
  - Aceptación: cada respuesta informa el identificador y coincide con el `instance-id` publicado en Eureka.
  - Depende: W3-3
  - Spec: GW (exposición de la instancia que atendió), `design.md` §13
  - Verificación: `mvn -q -pl servicio-pedidos,servicio-inventario -am test -Dtest='*InstanceHeader*'`

- [x] **W3-8** Publicar los contratos OpenAPI de ambos servicios con springdoc y generar `requests/` con una solicitud por operación contra el punto de entrada único.
  - Aceptación: las nueve operaciones del proposal tienen solicitud de ejemplo y ninguna apunta a una instancia concreta.
  - Depende: W3-3
  - Spec: TD (colección de solicitudes ejecutable)
  - Verificación: `grep -c '^### ' requests/*.http`

---

## W4 — Comunicación entre servicios, resiliencia y compensación

- [x] **W4-1** Declarar `@FeignClient(name = "servicio-inventario", path = "/api/inventario")` con `spring-cloud-starter-loadbalancer`, sin URL, host ni puerto en configuración.
  - Aceptación: la llamada se resuelve por nombre lógico y la configuración no contiene destinos fijos.
  - Depende: W3-2, W3-3, W1-1
  - Spec: ISC (llamada real por nombre lógico), SE (localización exclusiva por nombre lógico)
  - Verificación: `grep -rn 'servicio-inventario' servicio-pedidos/src/main/java | grep -q FeignClient`

- [x] **W4-2** Configurar timeouts de Feign (conexión 500 ms, lectura 1500 ms) y `Retryer` de un solo reintento limitado a errores de conexión.
  - Aceptación: una dependencia bloqueada produce fallo acotado dentro de 2 s y no reintenta errores de negocio.
  - Depende: W4-1
  - Spec: ISC (timeout explícito y acotado)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*FeignTimeout*'`

- [x] **W4-3** Configurar el circuit breaker de `inventario` (ventana 10, mínimo 5 llamadas, 50 %, apertura 10 s, 3 llamadas en semiabierto) con `fallbackFactory` y traducción a `503` `code=INVENTARIO_NO_DISPONIBLE`.
  - Aceptación: con inventario caído, la llamada falla rápido sin tocar la red y el cliente recibe 503 tipado con `correlationId`.
  - Depende: W4-2
  - Spec: ISC (circuit breaker, error tipado)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*CircuitBreaker*'`

- [x] **W4-4** Implementar la orquestación de creación de pedido: Tx1 `PENDIENTE` en primario, reserva por Feign, Tx2 `CONFIRMADO` con total de inventario; `409` con SKU faltantes deja el pedido `RECHAZADO` sin reserva.
  - Aceptación: stock suficiente confirma y descuenta exactamente lo pedido; una línea insuficiente no descuenta ningún SKU.
  - Depende: W4-3, W3-5
  - Spec: OM (reserva atómica, rechazo por stock), ISC (consistencia sin transacción distribuida)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*PedidoCreation*'`

- [x] **W4-5** Implementar la liberación compensatoria best-effort cuando falla la comunicación posterior a una reserva, con `WARN` y métrica sin revertir la cancelación.
  - Aceptación: el pedido permanece en un estado honesto, la reserva aplicada se libera y un fallo de liberación solo deja `WARN` y métrica.
  - Depende: W4-4
  - Spec: ISC (compensación y consistencia), OM (cancelación con liberación best-effort)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*Compensation*'`

- [x] **W4-6** Escribir el test de idempotencia de extremo a extremo: reintento de la misma reserva no aumenta el stock consumido y devuelve resultado equivalente.
  - Aceptación: dos llamadas con el mismo `pedidoId` dejan el stock igual y la segunda respuesta es equivalente a la primera.
  - Depende: W4-4, W3-2
  - Spec: IM (idempotencia de la reserva), ISC (reintento no duplica consumo)
  - Verificación: `mvn -q -pl servicio-pedidos,servicio-inventario -am test -Dtest='*Idempotency*'`

- [x] **W4-7** Crear `scripts/check-boundaries.sh` con tres verificaciones: cero host/puerto fijo en `application*.yml` de los servicios, cero referencias cruzadas entre `pedidos_db` e `inventario_db`, cero importaciones del dominio del otro servicio.
  - Aceptación: el script termina en 0 y falla si aparece cualquiera de las tres violaciones.
  - Depende: W4-1, W2-8
  - Spec: OM (aislamiento del dominio), IM (propiedad exclusiva), SE (sin destinos fijos), reglas `apply` de `config.yaml`
  - Verificación: `bash scripts/check-boundaries.sh`

---

## W5 — Gateway como punto de entrada único

- [x] **W5-1** Configurar `gateway` con rutas explícitas `Path=/api/pedidos/** → lb://servicio-pedidos` y `Path=/api/inventario/** → lb://servicio-inventario`, sin localizador automático de descubrimiento, en el puerto 8080.
  - Aceptación: ambas rutas aparecen declaradas en la configuración y resuelven a instancias registradas.
  - Depende: W3-6, W1-1
  - Spec: GW (punto de entrada único, rutas declaradas)
  - Verificación: `grep -nE 'lb://servicio-(pedidos|inventario)' gateway/src/main/resources/application.yml`

- [x] **W5-2** Implementar `CorrelationIdFilter` como `GlobalFilter` que reutiliza o genera `X-Correlation-Id`, muta el request descendente y lo devuelve en la respuesta.
  - Aceptación: un `X-Correlation-Id: abc-123` entrante aparece en los logs de ambos servicios y en la respuesta.
  - Depende: W5-1
  - Spec: GW (propagación del identificador de correlación)
  - Verificación: `mvn -q -pl gateway -am test -Dtest='CorrelationIdFilterTest'`

- [x] **W5-3** Añadir circuit breaker por ruta, retry solo en `GET` (máximo un intento) y timeouts `connect=2s` / `response=8s`.
  - Aceptación: sin destino sano, la ruta responde `503` tipado en tiempo acotado, por debajo de 8 s.
  - Depende: W5-2
  - Spec: GW (aislamiento de fallos en la entrada)
  - Verificación: `mvn -q -pl gateway -am test -Dtest='GatewayFallbackTest'`

- [x] **W5-4** Configurar el pool de Reactor Netty (`max-connections=200`, `max-idle-time=20s`, `acquire-timeout=5s`, `max-pending-acquire=100`).
  - Aceptación: la configuración efectiva muestra los cuatro valores y una carga secuencial de 100 requests no agota el pool.
  - Depende: W5-1
  - Spec: GW (entrada única con carga sostenida), LB (distribución medible)
  - Verificación: `grep -nE 'max-connections|acquire-timeout' gateway/src/main/resources/application.yml`

- [x] **W5-5** Añadir el registry Prometheus y `lb_requests_total{service,instance}` en el gateway para que la evidencia de balanceo sea reconstruible por script.
  - Aceptación: `/actuator/prometheus` expone el contador por instancia.
  - Depende: W5-1
  - Spec: LB (evidencia por solicitud), EE (evidencia generada por script)
  - Verificación: `mvn -q -pl gateway -am test -Dtest='*Metrics*'`

- [x] **W5-6** Verificar la frontera del gateway: solo `8080` publicado, rutas por nombre lógico y cero dependencias de base de datos en el módulo.
  - Aceptación: el gateway no declara datasource ni driver JDBC y sus rutas no contienen hosts.
  - Depende: W5-1
  - Spec: GW (entrada única), PRC (separación de responsabilidades)
  - Verificación: `! grep -qE 'jdbc:postgresql|host:|uri: *http' gateway/src/main/resources/application.yml && grep -c 'lb://' gateway/src/main/resources/application.yml`

---

## W6 — Routing de persistencia: `libs/persistence-routing` y cableado por servicio

Ciclo RED→GREEN explícito: las tareas de test preceden a su implementación. Las tareas W6-1 a W6-10 son unitarias sobre el módulo; W6-11 a W6-20 son el cableado por servicio.

- [x] **W6-1** Escribir (RED) `ReplicaSelectorTest`: round-robin determinista alternando `REPLICA_1`/`REPLICA_2`, lista vacía devuelve `PRIMARY`, cursor tras cambio de salud y ausencia de índice fuera de rango con cambios concurrentes.
  - Aceptación: los cinco casos existen y fallan porque el selector aún no existe.
  - Depende: W0-9
  - Spec: RPR (lecturas con política propia y determinista), ADR-03
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='ReplicaSelectorTest'`

- [x] **W6-2** Implementar (GREEN) `ReplicaSelector` con foto inmutable de réplicas `UP` y cursor `AtomicInteger` con `Math.floorMod`.
  - Aceptación: los casos de W6-1 pasan; con dos réplicas el desbalance es como máximo uno.
  - Depende: W6-1
  - Spec: RPR (round-robin determinista), `design.md` §5
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='ReplicaSelectorTest'`

- [x] **W6-3** Escribir (RED) `ReplicaHealthRegistryTest`: `UP→DOWN` por timeout, `DOWN→PROBING→UP` con dos verdes consecutivos, exclusión si el receiver no está en `streaming`, métrica y causa registradas.
  - Aceptación: los cinco casos existen y fallan porque el registry aún no existe.
  - Depende: W0-9
  - Spec: RPR (exclusión por health check), PRC (reincorporación condicionada), ADR-02
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='ReplicaHealthRegistryTest'`

- [x] **W6-4** Implementar (GREEN) `ReplicaHealthRegistry`: sondeo cada 5000 ms con timeout de 1000 ms, `SELECT 1` más `pg_stat_wal_receiver.status`, scheduling propio de 4 hilos y una tarea por nodo.
  - Aceptación: los casos de W6-3 pasan sin dormir el test (reloj inyectado) y la detección es ≤10 s.
  - Depende: W6-3
  - Spec: RPR (detección ≤10 s atribuida al sondeo), PRC (streaming requerido), `design.md` §6
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='ReplicaHealthRegistryTest'`

- [x] **W6-5** Implementar `DataSourceContextHolder` con `ThreadLocal<Deque<DataSourceKey>>` (`push`, `pop`, `peek`, `clear`) y su test de anidamiento y simetría.
  - Aceptación: un `push` dentro de otro restaura la clave previa al salir y el hilo no conserva contexto tras el request.
  - Depende: W0-9
  - Spec: RPR (decisión por transacción), `design.md` §4.1
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='DataSourceContextHolderTest'`

- [x] **W6-6** Implementar `RoutingDataSource` con mapa de las tres claves, `defaultTargetDataSource=primary` y `lenientFallback=false`, con test de clave desconocida que falla de forma ruidosa.
  - Aceptación: clave nula resuelve a primario; clave no nula desconocida lanza excepción en lugar de desviarse.
  - Depende: W6-5
  - Spec: RPR (la decisión corresponde al tipo de operación)
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='RoutingDataSourceTest'`

- [x] **W6-7** Escribir (RED) `ReadWriteRoutingAspectTest` con la tabla de precedencia completa: escritura, `readOnly=true`, `readOnly+@CriticalRead`, `@CriticalRead` sin transacción, sin transacción ni anotación y todas las réplicas caídas.
  - Aceptación: los seis casos de §4.2 existen y fallan antes del aspecto.
  - Depende: W6-2, W6-4, W6-6
  - Spec: RPR (escrituras en primario, lecturas en réplica, crítica en primario, degradado)
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='ReadWriteRoutingAspectTest'`

- [x] **W6-8** Implementar (GREEN) `ReadWriteRoutingAspect` interceptando `@Transactional` a nivel de método y de clase, escribiendo la clave y haciendo `pop()` en `finally`.
  - Aceptación: los seis casos de W6-7 pasan y el contexto queda limpio al salir del método.
  - Depende: W6-7
  - Spec: RPR (precedencia de decisión), `design.md` §4.2
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='ReadWriteRoutingAspectTest'`

- [x] **W6-9** Implementar la anotación `@CriticalRead` (solo en método) y su test de lista blanca: únicamente `GET /api/pedidos/{id}` y la lectura de precio previa a reservar pueden portarla.
  - Aceptación: el test falla si aparece un método anotado fuera de la lista acordada.
  - Depende: W6-8, W3-3
  - Spec: RPR (lectura crítica), ADR-07 (barreras 2 y 4)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='CriticalReadWhitelistTest'`

- [x] **W6-10** Fijar el orden de interceptores con `@EnableTransactionManagement(order = N)` y precedencia del aspecto **interna** a la del interceptor transaccional, con test guardián que falla si el orden se invierte.
  - Aceptación: el test guardián pasa con el orden correcto y falla explícitamente si el aspecto queda fuera de la transacción; una lectura cae en réplica y una escritura en primario.
  - Depende: W6-8
  - Spec: RPR (coherencia entre declaración y nodo real), `design.md` §4.4 — sin este orden todas las lecturas caen al primario sin error visible
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='RoutingOrderGuardTest'`

- [x] **W6-11** Implementar los tres `HikariDataSource` por nodo (`connectionTimeout=2000`, `validationTimeout=1000`, `initializationFailTimeout=-1`, `maximumPoolSize` acotado, `ApplicationName=<instancia>`).
  - Aceptación: los tres pools se crean aunque una réplica esté caída y el arranque del servicio no falla.
  - Depende: W2-1
  - Spec: RPR (exclusión por sondeo), `design.md` §4.5, `design.md` §13
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='DataSourcePoolConfigTest'`

- [x] **W6-12** Exponer `LazyConnectionDataSourceProxy` envolviendo el `RoutingDataSource` como bean `dataSource` consumido por JPA, con test que demuestra que no se adquiere conexión física antes de la primera sentencia.
  - Aceptación: la conexión no se abre al iniciar la transacción y sí al emitir la primera sentencia.
  - Depende: W6-6, W6-11
  - Spec: RPR (la decisión se resuelve antes de emitir la sentencia), `design.md` §4.3
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='LazyDataSourceProxyTest'`

- [x] **W6-13** Cablear el módulo de routing en `servicio-inventario`: configuración de pools, `RoutingDataSource`, proxy perezoso y propiedad `routing.enabled=true`.
  - Aceptación: el servicio arranca con los tres nodos declarados y las lecturas de `GET /api/inventario` informan una réplica.
  - Depende: W6-12, W3-1
  - Spec: RPR (lecturas en réplica), IM (consulta de stock en réplica)
  - Verificación: `mvn -q -pl servicio-inventario -am test -Dtest='*RoutingWiring*'`

- [x] **W6-14** Cablear el módulo de routing en `servicio-pedidos`, incluyendo el `EntityManagerFactoryDependsOnPostProcessor` que ordena la migración antes de `ddl-auto=validate`.
  - Aceptación: Flyway migra contra el primario y la validación de esquema corre después, sin tocar réplicas.
  - Depende: W6-12, W2-2, W3-3
  - Spec: RPR, ADR-06, OM (aislamiento del dominio)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*RoutingWiring*'`

- [x] **W6-15** Implementar el modo degradado: selector sin réplicas `UP` devuelve `PRIMARY`, emite `WARN` una sola vez por entrada al modo e incrementa `db_routing_degraded_total`, sin error al cliente.
  - Aceptación: con ambas réplicas `DOWN`, el 100 % de las lecturas ordinarias responde desde el primario con un único `WARN` por transición.
  - Depende: W6-2, W6-4
  - Spec: RPR (modo degradado hacia el primario), P06b
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='DegradedModeTest'`

- [x] **W6-16** Publicar las métricas Micrometer `db_routing_total{node,outcome}`, `db_routing_degraded_total`, `replica_health{node}`, `replica_probe_duration_seconds` y `critical_read_total`, y `RoutingDecisionRecorder` para headers y logs.
  - Aceptación: tras una lectura y una escritura, `/actuator/prometheus` muestra el nodo de cada decisión y el gauge de salud de ambas réplicas.
  - Depende: W6-8, W6-4
  - Spec: RPR (evidencia no circular, soporte), `design.md` §13
  - Verificación: `mvn -q -pl libs/persistence-routing -am test -Dtest='RoutingMetricsTest'`

- [x] **W6-17** Implementar el header `X-Data-Source` y la línea de log estructurada por request (`correlationId`, `service`, `instanceId`, `routingKey`, `txReadOnly`, `criticalRead`, `sql`, `durationMs`, `outcome`).
  - Aceptación: cada request de lectura y escritura emite header y línea de log con la clave de nodo decidida.
  - Depende: W6-16
  - Spec: RPR (evidencia contrastable), EE (evidencia generada por script)
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='*RoutingLogTest'`

- [x] **W6-18** Implementar la democión inmediata del nodo cuando un pool falla al prestar conexión (`markDown(key,"connection-refused")`) y el error tipado `503 DB_NODE_UNAVAILABLE`, sin reintento dentro de la transacción.
  - Aceptación: un fallo de pool excluye el nodo para el siguiente request y el request afectado recibe 503 tipado.
  - Depende: W6-4, W6-11
  - Spec: RPR (exclusión no atribuida a la consulta), `design.md` §5
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='NodeDemotionTest'`

- [x] **W6-19** Implementar el interruptor de rollback `routing.enabled` (por defecto `true`): con `false` el aspecto no se registra y todo el tráfico va al primario.
  - Aceptación: con `routing.enabled=false` el servicio sigue funcional y el 100 % de las operaciones usa el primario.
  - Depende: W6-13, W6-14
  - Spec: RPR, plan de rollback del proposal, `design.md` §18
  - Verificación: `mvn -q -pl servicio-pedidos -am test -Dtest='RoutingDisabledTest'`

- [x] **W6-20** Escribir el test de integración con Testcontainers del cableado completo: lectura ordinaria en `REPLICA_*`, escritura en `PRIMARY`, `@CriticalRead` en `PRIMARY`, contraste con `X-Data-Source` y log de decisión.
  - Aceptación: los cuatro tipos de operación coinciden con el nodo declarado y con la conexión observada.
  - Depende: W6-13, W6-14, W6-17
  - Spec: RPR (coherencia entre declaración y nodo real)
  - Verificación: `mvn -q -pl servicio-pedidos,servicio-inventario -am test -Dtest='RoutingWiringIT'`

---

## W7 — Entorno Compose de 9 contenedores

- [x] **W7-1** Crear `docker-compose.yml` con los tres nodos PostgreSQL (`postgres:16`), red `backend`, volumen nombrado por nodo, parámetros `wal_level=replica`, `max_wal_senders=10`, `wal_keep_size=512MB`, `max_replication_slots=0` y healthchecks `pg_isready`.
  - Aceptación: los tres contenedores arrancan y quedan `healthy`; las réplicas dependen del primario con `service_healthy`.
  - Depende: W2-8
  - Spec: PRC (primario y dos réplicas asíncronas), DE (red y dependencias de arranque)
  - Verificación: `docker compose up -d pg-primary pg-replica-1 pg-replica-2 && docker compose ps --format '{{.Name}} {{.Health}}'`

- [x] **W7-2** Ejecutar la prueba de humo de la imagen PostgreSQL (`postgres:16` oficial + `pg_basebackup -R` con `passfile`, ADR-05) **antes** de cablear el resto del Compose, y fijar el digest en `.env.example` y ADR-05.
  - Aceptación: el script confirma `standby.signal`, `primary_conninfo` y `pg_stat_wal_receiver.status = streaming` sin credenciales escritas en `postgresql.auto.conf` ni en la imagen.
  - Depende: W7-1
  - Spec: PRC (replicación por streaming), ADR-05
  - Verificación: `bash scripts/smoke-postgres-replication.sh`

- [x] **W7-3** Completar `infra/postgres/primary-entrypoint.sh` y `infra/postgres/replica-entrypoint.sh` con la entrada `host replication replicator 0.0.0.0/0 scram-sha-256` y el re-clone documentado.
  - Aceptación: un contenedor de réplica con `PGDATA` vacío se clona y arranca en `hot_standby`; con `PGDATA` poblado solo arranca.
  - Depende: W7-2
  - Spec: PRC (réplicas estrictamente de lectura), ADR-05
  - Verificación: `docker exec pg-replica-1 psql -U replicator -tAc 'select pg_is_in_recovery()'`

- [x] **W7-4** Añadir `eureka-server` y `gateway` al Compose con healthchecks de Actuator y `depends_on: service_healthy`; publicar solo `8761` y `8080`.
  - Aceptación: ambos quedan `healthy` y ningún otro contenedor publica puerto al host.
  - Depende: W1-2, W5-1
  - Spec: DE (exposición mínima de puertos), GW (entrada única)
  - Verificación: `test "$(docker compose config --format json | grep -c published)" -eq 2`

- [x] **W7-5** Añadir las dos instancias de cada servicio de negocio con `INSTANCE_ID` (`pedidos-1`, `pedidos-2`, `inventario-1`, `inventario-2`), `depends_on: service_healthy` de Eureka y PostgreSQL, y healthcheck de Actuator.
  - Aceptación: las cuatro instancias arrancan, se registran con identificadores distintos y responden tráfico.
  - Depende: W7-4, W6-13, W6-14, W4-1
  - Spec: LB (dos instancias simultáneas), SE (identificador único), DE (arranque ordenado)
  - Verificación: `test "$(curl -sf -H 'Accept: application/json' http://localhost:8761/eureka/apps | grep -c 'status.:.UP')" -eq 4`

- [x] **W7-6** Aplicar límites de memoria por contenedor y JVM acotada (`-XX:MaxRAMPercentage`) y documentar los requisitos de Docker Desktop/WSL2.
  - Aceptación: ningún contenedor supera su límite declarado y el total cabe en el entorno documentado.
  - Depende: W7-5
  - Spec: DE (entorno reproducible), riesgo «recursos de Docker»
  - Verificación: `test "$(docker compose config | grep -cE 'mem_limit|limits:')" -ge 9`

- [x] **W7-7** Crear `docker-compose.evidence.yml` como override que sube `log_statement=all` en los tres nodos y fija `application_name` por instancia.
  - Aceptación: con el override activo, el log del nodo registra la sentencia identificable y el perfil usado queda declarado en el informe.
  - Depende: W7-5
  - Spec: PRC (estado de replicación observable), EE (contraste no circular)
  - Verificación: `docker compose -f docker-compose.yml -f docker-compose.evidence.yml config | grep -c 'log_statement=all'`

- [x] **W7-8** Verificar el entorno completo: nueve contenedores `healthy` y cuatro instancias `UP` en Eureka desde un entorno destruido.
  - Aceptación: `docker compose down -v` seguido de `docker compose up -d` deja 9 contenedores healthy y 4 instancias registradas.
  - Depende: W7-1, W7-2, W7-3, W7-4, W7-5, W7-6, W7-7
  - Spec: DE (entorno reproducible desde cero), PRC, SE
  - Verificación: `test "$(docker compose ps --format '{{.Health}}' | grep -c healthy)" -eq 9`

---

## W8 — Evidencia experimental P01–P08 y P06b

Cada tarea produce un script versionado y un informe con las seis secciones (objetivo, procedimiento, esperado, obtenido, evidencia, análisis) que cita la fuente primaria exigida por `EE`. La evidencia cruda se guarda en `docs/informes/raw/<escenario>-<timestamp>/`.

- [ ] **W8-1** Crear `scripts/collect-evidence.sh` (y `.ps1`) que capture `/eureka/apps`, `/actuator/metrics`, headers de respuesta, `pg_stat_activity`, `pg_stat_replication` y `pg_stat_wal_receiver` con marcas temporales, filtrando `POSTGRES_PASSWORD` de la salida.
  - Aceptación: el script genera un directorio fechado con las seis fuentes y falla si detecta la contraseña en la evidencia.
  - Depende: W7-8
  - Spec: EE (evidencia generada por script, contraste no circular), TD (sin secretos)
  - Verificación: `bash scripts/collect-evidence.sh`

- [ ] **W8-2** Ejecutar y documentar **P01**: `scripts/evidence-p01.sh` captura `/eureka/apps` crudo con los cuatro `instance-id` y una request funcional por el gateway.
  - Aceptación: `docs/informes/P01.md` cita el JSON crudo con cuatro identificadores distintos y la request resuelta por nombre lógico.
  - Depende: W8-1, W7-8
  - Spec: SE (registro con identificador único, visibilidad del estado), EE (fuente P01)
  - Verificación: `bash scripts/evidence-p01.sh && grep -q 'eureka/apps' docs/informes/P01.md`

- [ ] **W8-3** Ejecutar y documentar **P02**: `scripts/load-test.sh` envía 100 solicitudes por el gateway y escribe CSV con timestamp, código e instancia.
  - Aceptación: el informe deriva del CSV el total de éxitos (≥95), las dos instancias con al menos una respuesta y el máximo por instancia (≤75 %), y cita el log de acceso de cada instancia.
  - Depende: W8-1, W7-8
  - Spec: LB (distribución sobre 100 solicitudes, evidencia por solicitud), EE (fuente P02)
  - Verificación: `bash scripts/load-test.sh && ls docs/informes/raw/P02-*/results.csv >/dev/null 2>&1`

- [ ] **W8-4** Ejecutar y documentar **P03**: `scripts/failure-drill.sh instance` detiene `pedidos-2`, envía 20 solicitudes, lo reinicia y mide detección y reincorporación.
  - Aceptación: el informe declara éxitos posteriores a la detección (≥90 %), la ventana transitoria cuantificada y su causa (cache del LB y lease de Eureka), con muestreo de `/eureka/apps` y log de lease fechados.
  - Depende: W8-1, W7-8
  - Spec: LB (continuidad, ventana cuantificada, recuperación observable), SE (expulsión y re-registro), EE (fuentes P03 y tiempos)
  - Verificación: `bash scripts/failure-drill.sh instance pedidos-2 --requests 20`

- [ ] **W8-5** Ejecutar y documentar **P04**: `scripts/evidence-p04.sh` crea un pedido con stock suficiente y repite el caso con inventario detenido.
  - Aceptación: el informe muestra el mismo `X-Correlation-Id` en los logs de pedidos e inventario, la instancia destino registrada y el `503` tipado del caso de fallo.
  - Depende: W8-1, W7-8
  - Spec: ISC (llamada real, error tipado, trazabilidad), OM (creación válida), EE (fuente P04)
  - Verificación: `bash scripts/evidence-p04.sh`

- [ ] **W8-6** Ejecutar y documentar **P05**: `scripts/evidence-p05.sh` ejecuta escrituras y lecturas y contrasta `X-Data-Source` con `pg_stat_activity` por nodo y `log_statement=all`.
  - Aceptación: el informe demuestra cero sentencias de escritura en réplicas, reparto de lecturas entre ambas réplicas y coincidencia fila por fila entre header, actividad y log.
  - Depende: W8-1, W7-7, W6-20
  - Spec: RPR (escrituras en primario, lecturas en réplica, evidencia no circular), OM (nodo por tipo de operación), EE (fuente P05)
  - Verificación: `bash scripts/evidence-p05.sh`

- [ ] **W8-7** Ejecutar y documentar **P06**: `scripts/failure-drill.sh replica pg-replica-1` detiene una réplica y ejecuta lecturas sostenidas.
  - Aceptación: el informe muestra la exclusión en ≤10 s, el 100 % de las lecturas posteriores en la réplica restante y el log de health check con intervalo, timeout y causa `probe-timeout`.
  - Depende: W8-1, W8-4, W7-8
  - Spec: RPR (exclusión por health check, atribución al sondeo), PRC, EE (fuente P06 y tiempos)
  - Verificación: `bash scripts/failure-drill.sh replica pg-replica-1`

- [ ] **W8-8** Ejecutar y documentar **P06b**: con ambas réplicas detenidas, `scripts/evidence-p06b.sh` ejecuta lecturas ordinarias y captura el primario y las réplicas.
  - Aceptación: el informe muestra el 100 % de lecturas en el primario, ninguna en réplicas, un `WARN` de degradado y el incremento de `db_routing_degraded_total`.
  - Depende: W8-1, W8-7
  - Spec: RPR (modo degradado), EE (fuente P06b)
  - Verificación: `bash scripts/evidence-p06b.sh`

- [ ] **W8-9** Ejecutar y documentar **P07**: `scripts/evidence-p07.sh` corre 20 ciclos de escritura más lectura crítica inmediata y registra `pg_stat_replication.replay_lag` del instante exacto de cada lectura.
  - Aceptación: el informe reporta 20/20 con el dato recién escrito y el `replay_lag` con marca temporal y unidad en cada corrida, explicando el caso >100 ms si ocurre.
  - Depende: W8-1, W6-20
  - Spec: RPR (read-your-writes), PRC (retraso medido en el momento), EE (fuente P07)
  - Verificación: `bash scripts/evidence-p07.sh --runs 20`

- [ ] **W8-10** Ejecutar y documentar **P08**: `scripts/failure-drill.sh replica-restart pg-replica-1` reinicia la réplica, espera streaming y mide la reincorporación.
  - Aceptación: el informe muestra `state = streaming` muestreado antes de la primera lectura reincorporada, el `replay_lag` del muestreo, la reincorporación en ≤60 s y el estado de los contenedores.
  - Depende: W8-1, W8-7
  - Spec: PRC (reincorporación condicionada), RPR, EE (fuente P08 y condición previa)
  - Verificación: `bash scripts/failure-drill.sh replica-restart pg-replica-1`

- [ ] **W8-11** Consolidar la medición de tiempos de detección y recuperación de P03, P06 y P08, descomponiendo cada uno en intervalo de sondeo, timeout y propagación declarada.
  - Aceptación: cada informe recalcula el tiempo desde marcas de las muestras y coincide con la suma declarada; ninguna cifra queda como afirmación sin muestreo.
  - Depende: W8-4, W8-7, W8-10
  - Spec: EE (tiempos medidos y descompuestos), LB (ventana transitoria)
  - Verificación: `grep -l 'tiempo de detección' docs/informes/P03.md docs/informes/P06.md docs/informes/P08.md`

- [ ] **W8-12** Crear `docs/informes/README.md` con la tabla de los nueve escenarios, su fuente primaria citada y su estado de ejecución.
  - Aceptación: la tabla enumera P01–P08 más P06b, cada uno con la fuente exigida y sin escenarios sustituidos por descripción teórica.
  - Depende: W8-2, W8-3, W8-4, W8-5, W8-6, W8-7, W8-8, W8-9, W8-10
  - Spec: EE (ejecución documentada, escenario faltante), TD
  - Verificación: `test "$(grep -cE '^\| P0(1|2|3|4|5|6|6b|7|8)' docs/informes/README.md)" -eq 9`

---

## W9 — Documentación técnica y reproducibilidad

- [ ] **W9-1** Escribir `README.md` con descripción, requisitos, configuración desde `.env.example`, puesta en marcha en tres pasos, verificación, detención, recuperación por componente, datos de prueba y procedimiento de re-clone de réplica.
  - Aceptación: un lector sin contexto levanta el entorno siguiendo solo el README y encuentra el procedimiento de recuperación de cada componente.
  - Depende: W7-8, W8-12
  - Spec: TD (README operativo autosuficiente), DE (reproducibilidad)
  - Verificación: `grep -cE '^## (Requisitos|Puesta en marcha|Verificación|Detención|Recuperación)' README.md`

- [ ] **W9-2** Crear `docs/arquitectura.md` con diagrama de componentes, puertos, comunicaciones, descubrimiento y flujo de persistencia, distinguiendo los tres mecanismos independientes.
  - Aceptación: el diagrama coincide con el entorno en ejecución y muestra separadamente descubrimiento, selección de instancia HTTP y selección de nodo de base de datos.
  - Depende: W7-8
  - Spec: TD (diagrama con flujos y puertos)
  - Verificación: `grep -cE 'Eureka|LoadBalancer|RoutingDataSource' docs/arquitectura.md`

- [ ] **W9-3** Completar `docs/decisiones.md` con ADR-01 a ADR-10, cada uno con contexto, alternativas descartadas, decisión y límite o consecuencia negativa.
  - Aceptación: la política de lectura declara su criterio, su costo en consistencia y qué ocurre sin réplicas sanas; se incluye la aclaración de que `@Transactional(readOnly=true)` no balancea por sí solo.
  - Depende: W6-20
  - Spec: TD (decisiones registradas y acotadas), RPR
  - Verificación: `test "$(grep -cE '^## ADR-[0-9]+' docs/decisiones.md)" -ge 10`

- [ ] **W9-4** Completar `requests/` con las nueve operaciones del proposal, valores de ejemplo válidos y apuntando al punto de entrada único.
  - Aceptación: toda operación documentada tiene al menos una solicitud y ninguna referencia una instancia concreta.
  - Depende: W3-8, W7-8
  - Spec: TD (colección ejecutable)
  - Verificación: `test -z "$(grep -L 'localhost:8080' requests/*.http)"`

- [ ] **W9-5** Crear `scripts/verify-from-scratch.sh` que valide el procedimiento completo desde un clon limpio: `.env`, `docker compose up -d`, nueve contenedores healthy, cuatro instancias y una request de negocio con ambos headers.
  - Aceptación: el script termina en 0 sobre un entorno destruido y falla si algún paso requiere intervención no documentada.
  - Depende: W9-1
  - Spec: DE (entorno reproducible desde cero), TD (recuperación documentada)
  - Verificación: `bash scripts/verify-from-scratch.sh`

- [ ] **W9-6** Ejecutar el barrido final: `scripts/check-boundaries.sh` sobre el árbol completo, búsqueda de material sensible en archivos versionados y verificación de enlaces del README.
  - Aceptación: cero URLs fijas en configuración, cero credenciales reales en el historial revisado y cero referencias cruzadas entre dominios.
  - Depende: W4-7, W9-1, W9-4
  - Spec: DE (ausencia de secretos), OM e IM (aislamiento), TD (documentación publicable)
  - Verificación: `bash scripts/check-boundaries.sh && ! git grep -nEi 'password *[:=] *[^$]' -- '*.yml' '*.yaml'`

---

## W10 — Video demostrativo y cierre

- [ ] **W10-1** Escribir el guion del video con los seis contenidos exigidos y una duración objetivo de 40 s por bloque, sin inventar integrantes ni plazos.
  - Aceptación: el guion lista los seis bloques en el orden del enunciado y suma entre 3 y 5 minutos.
  - Depende: W8-12
  - Spec: TD (video con contenido mínimo)
  - Verificación: `grep -cE '^## Bloque [1-6]' docs/guion-video.md`

- [ ] **W10-2** Crear `scripts/video-drill.sh` con los comandos preensayados de caída de instancia y de réplica, sin interpolación de entrada externa.
  - Aceptación: el script reproduce la secuencia completa sobre el sistema en vivo y termina en 0.
  - Depende: W7-8
  - Spec: TD (video sobre el sistema en ejecución), `design.md` §17 (frontera de proceso)
  - Verificación: `bash scripts/video-drill.sh`

- [ ] **W10-3** Ensayar la corrida completa del guion con el sistema en vivo, verificando terminal, logs y headers legibles.
  - Aceptación: los seis bloques se ejecutan en una sola pasada y cualquier fallo queda explicado en el guion, no cortado.
  - Depende: W10-1, W10-2
  - Spec: TD (contenido demostrado sobre sistema vivo)
  - Verificación: `bash scripts/video-drill.sh --full`

- [ ] **W10-4** Grabar el video de 3 a 5 minutos, publicarlo con enlace accesible y agregarlo al README.
  - Aceptación: el enlace es público, no requiere descarga, muestra los seis contenidos y el README lo referencia.
  - Depende: W10-3, W9-1
  - Spec: TD (video accesible, no sustituye la evidencia)
  - Verificación: `grep -qiE 'video.*https?://' README.md`

---

## Orden de ejecución y ruta crítica

Ruta crítica declarada por el plan (§7) y conservada aquí:

```text
W0 → W2 → W3 → W6 → W7 → W8 → W9/W10
```

Dependencias entre grupos, con las ramas paralelizables:

| Grupo | Depende de | Puede ejecutarse en paralelo con |
| :-- | :-- | :-- |
| W0 | — | — |
| W1 | W0-7, W0-8 | W2 (no comparte archivos) |
| W2 | W0-8 | W1 |
| W3 | W2-3, W2-5, W2-6 | W1 |
| W4 | W1-1, W3-2, W3-3, W3-5 | W5 (archivos distintos) |
| W5 | W1-1, W3-6 | W4 |
| W6 | W2 (migraciones y pools), W3 (endpoints para el guardián de orden) | Ninguno: consume `libs/` y los dos servicios |
| W7 | W1, W4, W5, W6 | — |
| W8 | W7-8 (y W7-7 para P05/P06b) | — |
| W9 | W8-12 | W10-1 puede escribirse desde W8-12 |
| W10 | W9-1, W10-3 | — |

Hitos de verificación que cierran cada grupo y no admiten avance sin ellos:

1. **W0**: `mvn -q verify` verde y `strict_tdd: true` activo (W0-10). Sin esto no se aplica TDD estricto aguas abajo.
2. **W1**: expulsión y re-registro medidos con `eureka-lease-probe` (W1-3); reduce el riesgo de tiempos de P03 antes de W7.
3. **W2**: migraciones aplicadas y aislamiento por credenciales verificado (W2-8).
4. **W6**: guardián de orden de interceptores en verde (W6-10) y test de cableado (W6-20). Si W6-10 falla, no se avanza a W7: el síntoma es «todo va al primario» sin error visible.
5. **W7**: prueba de humo de imagen antes de cablear el resto (W7-2) y nueve contenedores healthy (W7-8).
6. **W8**: nueve informes con fuente primaria citada (W8-12) antes de escribir el README definitivo.

Restricción de secuencia que no es obvia: W7-2 (humo de la imagen PostgreSQL) se ejecuta antes de W7-4 y W7-5, porque un fallo de la imagen elegida en ADR-05 obliga a cambiar el `entrypoint` de las réplicas y no solo un servicio de aplicación.

---

## Matriz de trazabilidad

| Capability (spec) | Tareas | Escenario P |
| :-- | :-- | :-- |
| `service-discovery` (SE) | W1-1, W1-2, W1-3, W4-1, W7-4, W7-5, W8-2, W8-4 | P01, P03 |
| `http-load-balancing` (LB) | W5-4, W5-5, W7-5, W8-3, W8-4, W8-11 | P02, P03 |
| `api-gateway-routing` (GW) | W3-7, W5-1, W5-2, W5-3, W5-4, W5-5, W5-6, W7-4 | P02, P04 |
| `order-management` (OM) | W2-4, W2-6, W3-3, W3-4, W3-5, W3-6, W4-4, W4-5, W6-14, W8-6 | P04, P05, P07 |
| `inventory-management` (IM) | W2-1, W2-3, W2-5, W3-1, W3-2, W3-6, W4-6, W6-13 | P04, P05 |
| `inter-service-communication` (ISC) | W4-1, W4-2, W4-3, W4-4, W4-5, W4-6, W8-5 | P04 |
| `replicated-persistence-routing` (RPR) | W0-9, W2-1, W6-1 a W6-20, W8-6, W8-9 | P05, P06, P06b, P07, P08 |
| `postgres-replication-cluster` (PRC) | W2-2, W2-8, W7-1, W7-2, W7-3, W7-7, W8-6, W8-9, W8-10 | P06, P06b, P07, P08 |
| `deployment-environment` (DE) | W0-1 a W0-11, W1-2, W7-1, W7-4, W7-5, W7-6, W7-8, W9-5, W9-6 | P01 (base de todos) |
| `experimental-evidence` (EE) | W5-5, W8-1 a W8-12 | P01, P02, P03, P04, P05, P06, P06b, P07, P08 |
| `technical-documentation` (TD) | W3-8, W9-1, W9-2, W9-3, W9-4, W9-6, W10-1, W10-2, W10-3, W10-4 | Video; evidencia P01–P08 |

Cobertura de escenarios por tarea de evidencia: P01→W8-2, P02→W8-3, P03→W8-4, P04→W8-5, P05→W8-6, P06→W8-7, P06b→W8-8, P07→W8-9, P08→W8-10.

---

## Riesgos de planificación

| Riesgo | Afecta | Señal temprana | Mitigación en el plan |
| :-- | :-- | :-- | :-- |
| **A1 confirmado** (dominio Pedidos + Inventario, 2026-11-18) | W2, W3, W4, W8-5 y el guion del video | El humano cambia el dominio al revisar este archivo | Confirmar antes de W2-3. Lo estructural (W0, W1, W5, W6, W7, P01–P03, P05–P08) no se reabre |
| **Matriz de versiones Boot 3.5.x / Cloud 2025.0.x** | W0-7 y todo el reactor | `mvn -q -N validate` con BOM no resoluble o incompatibilidad en runtime | W0-6 obliga a verificar y registrar antes de escribir el POM; si la matriz empuja a Boot 4.x se reescribe ADR-10 y el train completo |
| **Recursos de Docker** (9 contenedores, WSL2) | W7, W8 y la validez de la evidencia | Contenedores reiniciándose o `healthy` intermitente | W7-6 fija límites de memoria y `MaxRAMPercentage`; el entorno se documenta en el README |
| **Imagen PostgreSQL de replicación** | W7-1, W7-2, todo W8 | `pg_stat_wal_receiver` no llega a `streaming` | ADR-05: `postgres:16` con `pg_basebackup -R` y contingencia con Bitnami pineada por digest; humo dentro de Compose en W7-2, antes de cablear servicios |
| **Umbrales numéricos sin ratificar** (95 %, 90 %, 10 s, 30 s, 60 s, 100 ms) | W8-4, W8-7, W8-10, W8-11 | La primera medición queda fuera del umbral propuesto | Cada spec marca el umbral como ratificable; W8-11 registra el valor final medido y el informe lo declara |
| **Orden de interceptores invertido** | W6-10 y toda la evidencia de P05 | Todas las lecturas caen al primario sin error visible, P05 «pasa» con headers falsos | W6-10 es test guardián obligatorio y bloquea el avance a W7 |
| **Evidencia circular** (confiar en `X-Data-Source`) | W8-6, W8-8 | El header coincide pero la base no registra la sentencia | W8-1 captura las fuentes del motor y cada informe debe citarlas; sin cotejo el escenario queda «no probado» |
| **Scripts con CRLF en contenedores Linux** | W1-3, W7-2, W7-3, W8-* | `entrypoint` falla con `\r` en el shebang | W0-5 declara `*.sh text eol=lf` antes de crear el primer script |

Ningún riesgo de este cuadro se cierra con una tarea marcada como completada por adelantado: cada uno tiene una tarea de verificación ejecutable asociada.
