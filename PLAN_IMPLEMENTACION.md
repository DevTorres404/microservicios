# Plan de implementación — Microservicios con descubrimiento, balanceo y persistencia replicada

Documento de trabajo derivado de `Actividad_Microservicios_Distribuidos.md`.
Objetivo: llegar a un sistema ejecutable, reproducible y **demostrado experimentalmente** (P01–P08), no solo a código que compila.

---

## 1. Decisiones de partida

| ID | Decisión | Estado | Justificación |
| :-- | :-- | :-- | :-- |
| D1 | Dominio de negocio: **Pedidos** + **Inventario** | Propuesto | El enunciado ya usa `lb://servicio-pedidos`; la relación pedido↔stock genera una comunicación inter-servicio con valor de negocio real (no una llamada decorativa). |
| D2 | Java 21 LTS + Spring Boot 3.5.x + Spring Cloud 2025.0.x | Propuesto | Release train estable y documentado. **Verificar la matriz de compatibilidad oficial antes de generar el `pom.xml`**: si se sube a Boot 4.x hay que cambiar el train completo. |
| D3 | Un solo cluster PostgreSQL (1 primario + 2 réplicas) con **una base por servicio** (`pedidos_db`, `inventario_db`) | Propuesto | Cumple la tabla de arquitectura mínima sin multiplicar contenedores. Cada servicio solo accede a su propia base: no hay lectura cruzada de tablas. |
| D4 | **2 instancias de ambos servicios** (no de uno solo) | Propuesto | El mínimo es 2 de uno; tener 2 de los dos permite demostrar balanceo también en la llamada inter-servicio y sobrevive mejor a P03. |
| D5 | Replicación **asíncrona** por streaming WAL | Propuesto | Es la opción realista por defecto en Docker. Habilita el escenario P07 (lag de replicación) y obliga a justificar la política de consistencia. |
| D6 | Política de lectura: **round-robin determinista sobre réplicas sanas**, primario excluido salvo lectura crítica o modo degradado | Propuesto | Round-robin es medible y auditable en las evidencias; random complica P05/P08. |
| D7 | Maven multi-módulo | Propuesto | Estándar en cursos y en CI: un `mvn -T 1C verify` construye todo. |

**Pendiente de confirmación del equipo:** D1 (dominio) y D2 (versiones). Todo lo demás es revisable sin reescribir el plan.

---

## 2. Arquitectura objetivo

```text
Cliente HTTP / script de carga
        │  :8080
        ▼
Spring Cloud Gateway ──descubre──▶ Eureka :8761
        │
        ├── lb://servicio-pedidos ──▶ pedidos-1 (:8081) / pedidos-2 (:8082)
        │                                   │
        │                                   └── Feign lb://servicio-inventario
        │                                            │
        │                                            ▼
        └── lb://servicio-inventario ──▶ inventario-1 (:8083) / inventario-2 (:8084)

Persistencia (por servicio, con routing propio)
  pedidos-*    ──▶ RoutingDataSource ──▶ PRIMARY (write)
  inventario-* ──▶ RoutingDataSource ──▶ REPLICA_1 / REPLICA_2 (read)

PostgreSQL primary :5432 ──WAL──▶ replica-1 :5433 / replica-2 :5434
```

Puntos arquitectónicos que la rúbrica evalúa y que el diseño separa a propósito:

- **Eureka** descubre **procesos**, no bases de datos.
- **Spring Cloud LoadBalancer** elige instancia HTTP.
- **`AbstractRoutingDataSource`** elige nodo de base de datos.
- Son tres decisiones independientes y el informe debe explicarlo así.

### Reglas de frontera entre servicios (verificables, no declarativas)

- Cada servicio es dueño de su base y su esquema; no hay `JOIN` ni lectura de tablas ajenas.
- No se comparten entidades JPA ni clases de dominio entre módulos. Solo contratos HTTP (DTOs por servicio) — compartir entidades es acoplamiento disfrazado de reutilización.
- Dependencia unidireccional: `pedidos → inventario`. `inventario` no conoce a `pedidos`.
- Ningún servicio llama a otro por URL fija ni al gateway: siempre nombre lógico vía LoadBalancer.
- Verificación en revisión: `grep` de hostnames/puertos hardcodeados en `application.yml` de los servicios debe dar cero.

### Puertos

| Componente | Host | Contenedor |
| :-- | :-- | :-- |
| Gateway | 8080 | 8080 |
| Eureka | 8761 | 8761 |
| pedidos-1 / pedidos-2 | 8081 / 8082 | 8080 |
| inventario-1 / inventario-2 | 8083 / 8084 | 8080 |
| PostgreSQL primary | 55432 (opcional, solo diagnóstico) | 5432 |
| réplicas | no publicadas | 5432 |

Solo el gateway y Eureka se publican al host: el resto vive en la red `backend`. Publicar el primario solo si se necesita evidencia SQL desde el host, y documentarlo.

---

## 3. Estructura del repositorio

```text
microservicios/
├─ pom.xml                      # parent (dependencyManagement + módulos)
├─ docker-compose.yml
├─ .env.example                 # variables, SIN secretos reales
├─ .gitignore
├─ README.md
├─ docs/
│  ├─ arquitectura.md           # + diagrama (draw.io / mermaid)
│  ├─ informes/P01..P08.md      # evidencia de cada escenario
│  └─ decisiones.md             # ADRs cortos: routing, replicación, consistencia
├─ infra/
│  ├─ postgres/init/01-databases.sql
│  └─ scripts/                  # healthcheck, espera de replicación
├─ scripts/
│  ├─ load-test.ps1  / load-test.sh      # P02: 100 requests + CSV
│  ├─ failure-drill.ps1 / .sh            # P03, P06, P08: stop/start + muestreo
│  └─ collect-evidence.ps1 / .sh         # eureka/apps, pg_stat_replication, headers
├─ eureka-server/
├─ gateway/
├─ servicio-pedidos/
├─ servicio-inventario/
└─ requests/                    # colección Bruno/Postman/.http
```

---

## 4. Diseño del enrutamiento de persistencia (núcleo, 20 pts)

### 4.1 Componentes

1. **`DataSourceProperties` por nodo**: `primary`, `replica1`, `replica2` — un `HikariDataSource` cada uno, con `connectionTimeout=2000ms`, `validationTimeout=1000ms`, `maximumPoolSize` acotado.
2. **`RoutingDataSource extends AbstractRoutingDataSource`**: mapa `DataSourceKey → DataSource`, `defaultTargetDataSource = primary`.
3. **`DataSourceContextHolder`**: `ThreadLocal<DataSourceKey>`.
4. **`LazyConnectionDataSourceProxy`** envolviendo el `RoutingDataSource`. Es obligatorio: sin él la conexión se adquiere antes de que Spring marque la transacción como `readOnly`, y todo va al primario.
5. **`ReadWriteRoutingAspect`**: intercepta `@Transactional`. Si `isReadOnly()` → pide una réplica sana al selector; si no → `PRIMARY`.
6. **`@CriticalRead`**: anotación propia que fuerza `PRIMARY` aunque la transacción sea de lectura. Resuelve P07 (read-your-writes).
7. **`ReplicaHealthRegistry`**: `@Scheduled(fixedDelay=5000)` con `SELECT 1` y timeout de 1s por réplica; mantiene estado `UP/DOWN` y expone métricas.
8. **`ReplicaSelector`**: round-robin con cursor atómico sobre la lista de réplicas `UP`. Si la lista queda vacía → `PRIMARY` en **modo degradado** (log WARN + métrica, documentado como decisión y como límite).

### 4.2 Reglas de selección (política documentada)

| Operación | Nodo |
| :-- | :-- |
| `INSERT/UPDATE/DELETE`, cualquier `@Transactional` de escritura | PRIMARY |
| Lectura ordinaria | round-robin `REPLICA_1`/`REPLICA_2` |
| Lectura marcada `@CriticalRead` (post-escritura inmediata) | PRIMARY |
| Réplica caída | se excluye; el resto sigue recibiendo lecturas |
| Todas las réplicas caídas | PRIMARY (degradado, con WARN y métrica) |

### 4.3 Trampas conocidas a resolver en implementación

- **Flyway debe apuntar al primario**, no al `RoutingDataSource`; si no, migra contra una réplica read-only y falla. Configurar un `Flyway` bean con el `DataSource` del primario.
- **`spring.jpa.open-in-view=false`**: con OSIV la sesión se abre fuera de la transacción y el routing se decide en el momento equivocado.
- **Pool por réplica**: si un pool se queda sin conexiones porque el nodo murió, el `connectionTimeout` corto evita bloquear el request. Medir ese timeout y reportarlo.
- Un `@Transactional(readOnly=true)` **no** balancea nada por sí solo; el balanceo lo hace el selector. Debe quedar explícito en el informe para no perder puntos.

### 4.4 Evidencia de routing

- Header de respuesta `X-Data-Source: PRIMARY|REPLICA_1|REPLICA_2`.
- Header `X-Service-Instance: pedidos-1|pedidos-2|inventario-1|inventario-2`.
- Log estructurado por request: `correlationId`, `routingKey`, `instanceId`, `sql`.
- Métricas Micrometer: `db_routing_total{node}`, `replica_health{node}`, `lb_requests_total{service,instance}`.

---

## 5. Descubrimiento, gateway y comunicación

- **Eureka**: standalone. En Docker, `eureka.instance.prefer-ip-address=true` y `instance-id` único por contenedor (`${spring.application.name}:${HOSTNAME}`); si no, dos instancias del mismo servicio se registran con el mismo id y una pisa a la otra.
- **Ajuste de tiempos para que P03 sea medible**: `lease-renewal-interval=5s`, `lease-expiration=15s`, `registry-fetch-interval=5s`, cache de LoadBalancer acotado. Sin esto, la expulsión por defecto (~90s) hace que la recuperación parezca "rota" y el escenario no se pueda explicar.
- **Gateway**: rutas explícitas `lb://servicio-pedidos` y `lb://servicio-inventario` (no discovery locator, para que se vea la ruta declarada), timeouts y `X-Correlation-Id` propagado.
- **Comunicación inter-servicio**: OpenFeign `@FeignClient(name="servicio-inventario")` + `spring-cloud-starter-loadbalancer`, con Resilience4j (timeout, retry acotado, circuit breaker) y un fallback que responda `503` con cuerpo de error tipado.
- **Errores transitorios**: al matar una instancia, el LoadBalancer puede seguir ofreciéndola hasta refrescar cache. Esperado: algunos `5xx`/timeouts acotados. Debe quedar medido y explicado (P03), no ocultado con un retry infinito.

---

## 6. Persistencia replicada (Docker)

- Imagen: `bitnami/postgresql` con `POSTGRESQL_REPLICATION_MODE=master|slave`. **Riesgo**: cambios recientes en la distribución de imágenes Bitnami (tags movidos a `bitnamilegacy`). Mitigación: pinear una versión verificada o, si no está disponible, usar `postgres:16` + `pg_basebackup` en un `entrypoint` propio (más trabajo, control total). Decidir en W1 con una prueba de humo.
- Parámetros del primario: `wal_level=replica`, `max_wal_senders=10`, `wal_keep_size`, `hot_standby=on` en réplicas.
- `infra/postgres/init/01-databases.sql`: crea `pedidos_db`, `inventario_db` y un usuario por servicio con permisos solo sobre su base.
- Volúmenes nombrados por nodo; sin secretos en el compose: `${POSTGRES_PASSWORD}` desde `.env`, y `.env` en `.gitignore` con `.env.example` versionado.
- Healthchecks reales: `pg_isready` en el primario y `pg_isready -U replicator` en las réplicas; los servicios esperan `service_healthy`.
- **Lag de replicación**: medir con `pg_stat_replication` (`state`, `replay_lag`) y exponerlo en la evidencia de P07/P08.

---

## 7. Paquetes de trabajo

Estimación en jornadas de una persona; varias se paralelizan entre integrantes.

| ID | Paquete | Entregable verificable | Est. | Depende de |
| :-- | :-- | :-- | :-- | :-- |
| W0 | Andamiaje: repositorio Git inicializado + remoto GitHub, parent POM, módulos vacíos, `.gitignore`, `.env.example`, `.gitattributes` (LF en `*.sh`), CI mínimo (`mvn -q verify`) | `mvn verify` verde con módulos vacíos y commit inicial publicado | 0.5 | — |
| W1 | Eureka Server + Docker + healthcheck; prueba de registro con un cliente dummy | `GET /eureka/apps` lista la instancia | 0.5 | W0 |
| W2 | Modelo de dominio + Flyway + repositorios de **inventario** (stock, reserva) y **pedidos** (pedido, línea) | Migraciones aplican en el primario; tests de repositorio con Testcontainers | 1.5 | W0 |
| W3 | API REST de ambos servicios + validación + manejo de errores (`ProblemDetail`) | Contratos documentados (OpenAPI) y colección de requests | 1.0 | W2 |
| W4 | Comunicación Feign + Resilience4j + compensación best-effort de stock | `POST /api/pedidos` reserva stock real; fallo de inventario devuelve 503 tipado | 1.0 | W3, W1 |
| W5 | Gateway + rutas `lb://` + circuit breaker + correlation id | Request al gateway llega a instancia rotativa; header de instancia visible | 0.5 | W3 |
| W6 | **Routing de DataSources** (sección 4) + `@CriticalRead` + health de réplicas | Lecturas repartidas, escrituras al primario, réplica caída excluida | 2.0 | W2 |
| W7 | Docker Compose completo (9 servicios, red, volúmenes, depends_on, límites) | `docker compose up -d` deja 9 contenedores `healthy` | 1.5 | W1, W5, W6 |
| W8 | Scripts de evidencia + ejecución de P01–P08 + informes | `docs/informes/P01..P08.md` con evidencia real | 2.0 | W7 |
| W9 | README técnico, diagrama, colección de requests, ADRs | Entregables 1–5 completos | 1.5 | W8 |
| W10 | Guion, ensayo y grabación del video (3–5 min), publicación y enlace en README | Video accesible con los 6 contenidos exigidos | 1.0 | W8, W9 |

**Ruta crítica:** W0 → W2 → W3 → W6 → W7 → W8 → W9/W10.

### Orden recomendado de ejecución (por sesiones de trabajo)

1. **S1 — Esqueleto ejecutable**: W0 + W1. Criterio: Eureka arriba en Docker y un cliente aparece registrado.
2. **S2 — Negocio sin distribución**: W2 + W3 con PostgreSQL local (un solo contenedor). Criterio: CRUD real con migraciones y tests.
3. **S3 — Distribución HTTP**: W4 + W5. Criterio: pedido creado atraviesa dos servicios por nombre lógico.
4. **S4 — Persistencia replicada**: W6 con el cluster de 3 nodos. Criterio: headers muestran PRIMARY/REPLICA_1/REPLICA_2 según operación.
5. **S5 — Entorno integrado**: W7. Criterio: `docker compose up` reproducible desde cero en máquina limpia.
6. **S6 — Experimentación**: W8. Criterio: los 8 escenarios con evidencia, incluidos los errores observados.
7. **S7 — Cierre documental y defensa**: W9 + W10.

---

## 8. Plan experimental (P01–P08)

Cada escenario produce: objetivo, procedimiento reproducible, resultado esperado, resultado obtenido, evidencia (comando + salida + timestamp) y análisis. Los scripts de `scripts/` deben generar la evidencia, no transcribirla a mano.

| Escenario | Procedimiento | Evidencia mínima | Qué debe explicar el informe |
| :-- | :-- | :-- | :-- |
| P01 | Consultar `/eureka/apps` y hacer una request funcional por el gateway | JSON de registro + headers de instancia | Instance-id único por contenedor |
| P02 | 100 requests a `GET /api/pedidos` por el gateway, CSV con instancia respondiente | Distribución (≈50/50), éxitos, errores | Por qué no es exactamente 50/50 |
| P03 | `stop pedidos-2` → 20 requests → `start pedidos-2` → medir tiempo de reincorporación | Antes/durante/después + tiempo medido | Ventana de errores transitorios por cache del LB y lease de Eureka |
| P04 | `POST /api/pedidos` con líneas → reserva en inventario | Traza correlation-id en logs de ambos servicios, resultado y caso de error (inventario caído) | Circuit breaker y consistencia sin transacciones distribuidas |
| P05 | POST + GET con `X-Data-Source`; `pg_stat_activity`/logs SQL | Secuencia de requests + nodo por operación | Writes al primario, reads round-robin |
| P06 | `stop pg-replica-1` → GETs repetidos | Estado de contenedores, headers, log del health registry | Exclusión por health check, no por excepción del driver |
| P07 | POST + GET inmediato con `@CriticalRead`; `pg_stat_replication` en el momento | Secuencia + `replay_lag` observado | Política read-your-writes y su costo |
| P08 | `start pg-replica-1` → esperar streaming → GETs | `pg_stat_replication` + headers volviendo a mostrar REPLICA_1 | Reincorporación solo con health check verde |

**Refuerzos de evidencia (lo que la rúbrica realmente premia):**

- **P05 no se prueba con el header.** `X-Data-Source` lo declara tu propio código; es circular. Contrastar con `pg_stat_activity` por nodo y `log_statement=all` para mostrar la sentencia ejecutada en el nodo declarado.
- **Medir tiempo de detección**, no solo el resultado: intervalo de health check + timeout de pool. Ese número va en P03, P06 y P08. Sin él, "se recuperó" no es una medición.
- **P06b (degradado, escenario complementario):** ambas réplicas caídas → lecturas al primario con WARN y métrica. Demuestra que el fallback es decisión explícita y no un accidente de la configuración.
- **P07 necesita el `replay_lag` del instante exacto** de la lectura crítica. Sin ese dato no hay justificación de la política, solo una afirmación.

**Condición de honestidad técnica:** si un escenario muestra errores, se documentan. El enunciado lo permite explícitamente y la rúbrica premia el análisis.

---

## 9. Checklist de entregables y rúbrica

| Entregable | Dónde | Estado |
| :-- | :-- | :-- |
| Repositorio GitHub público con código completo | raíz | ☐ |
| README técnico (instalación, ejecución, datos de prueba, recuperación) | `README.md` | ☐ |
| Diagrama de arquitectura con componentes, comunicaciones, puertos, descubrimiento y flujo de persistencia | `docs/arquitectura.md` | ☐ |
| Informe P01–P08 (+ P06b) con objetivo, procedimiento, esperado, obtenido, evidencia y análisis | `docs/informes/` | ☐ |
| Colección de requests reproducible | `requests/` | ☐ |
| Video 3–5 min (W10) con enlace público en README | `README.md` | ☐ |
| ADRs de routing, replicación y consistencia | `docs/decisiones.md` | ☐ |

### Matriz de trazabilidad rúbrica → plan → evidencia

| Criterio (pts) | Qué exige realmente | Dónde se implementa | Cómo se prueba | Estado |
| :-- | :-- | :-- | :-- | :-- |
| **Arquitectura y separación de responsabilidades (15)** | Dos dominios con dueño de datos propio, sin acoplamiento oculto | §2 reglas de frontera, §3 estructura, D3 | Revisión de frontera (cero URLs fijas, cero entidades compartidas) + diagrama con puertos y flujos | Cubierto |
| **Eureka y descubrimiento dinámico (15)** | Registro real, instancias identificables, localización por nombre lógico | W1, W5 | P01: `/eureka/apps` + `instance-id` único + request funcional | Cubierto |
| **Gateway y balanceo HTTP (15)** | Entrada única, `lb://`, distribución observable en 100 requests | W5, W7 | P02 (CSV por instancia) + P03 (continuidad) | Cubierto |
| **Enrutamiento de lecturas y escrituras (20)** | Escrituras al primario, lecturas distribuidas por política propia, consistencia crítica resuelta | §4 completo, W6 | P05 con contraste en DB real, P07 con `replay_lag` | Cubierto |
| **Manejo de fallos y recuperación (20)** | Caída de instancia, caída de réplica, reincorporación condicionada, tiempos medidos | W4 (circuit breaker), W6 (health registry), §5 | P03, P06, P06b, P08 con tiempo de detección y recuperación | Cubierto |
| **Evidencias, documentación y defensa (15)** | Reproducibilidad real, informe con análisis, video demostrativo | W8, W9, W10, §11 | Informe P01–P08, README desde cero en máquina limpia, video con los 6 contenidos | Cubierto vía W10 |

### Video y defensa técnica (W10)

Contenidos obligatorios, en este orden (el enunciado los numera igual):

1. Presentación de la arquitectura: servicios, responsabilidades, rol de Eureka, gateway y PostgreSQL.
2. Descubrimiento y balanceo: panel de Eureka + solicitudes alternando instancia.
3. Comunicación entre servicios: `POST /api/pedidos` con traza de correlation-id en ambos logs.
4. Persistencia replicada: escritura al primario y lectura en réplica, con headers visibles.
5. Simulación de fallos: `stop` de una instancia y de una réplica, con la respuesta del sistema.
6. Conclusiones técnicas: limitaciones reales encontradas y mejoras posibles.

Reglas operativas que se pierden puntos si se ignoran: 3–5 minutos máximo, participación de **todos** los integrantes, sistema corriendo en vivo, narración con voz, terminal y logs legibles, enlace público en el README (sin descarga), y **no** reemplaza las evidencias P01–P08.

Preparación: guion con tiempos por bloque (≈40 s por contenido), comandos de fallo preensayados en un `scripts/video-drill.ps1`, y una pasada completa antes de grabar. Si un escenario falla en vivo, se explica el error en lugar de cortar: eso también es defensa técnica.

---

## 10. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
| :-- | :-- | :-- |
| Imagen `bitnami/postgresql` no disponible con la configuración esperada | Bloquea W7 | Prueba de humo en W1; fallback a `postgres:16` + `pg_basebackup` |
| Docker Desktop sin recursos (WSL2) para 9 contenedores | Caídas aleatorias que contaminan evidencias | Fijar límites de memoria, JVM `-XX:MaxRAMPercentage`, subir RAM de WSL2 y documentarlo en README |
| Registro duplicado en Eureka (dos pods mismo instance-id) | Balanceo falso | `instance-id` con `${HOSTNAME}` y `prefer-ip-address` |
| LoadBalancer sigue ruteando a instancia muerta | `5xx` en P03 | Retry acotado + circuit breaker; documentar la ventana |
| Flyway corriendo contra réplica | Arranque fallido | `Flyway` bean apuntando al `DataSource` primario |
| Lag de replicación mayor al esperado | P07 devuelve datos viejos | `@CriticalRead` a primario + medición de `replay_lag` |
| CRLF en scripts montados en contenedores Linux | Entrypoints fallan | `.gitattributes` con `*.sh text eol=lf` |
| Puertos ocupados en el host | Compose no levanta | Solo publicar 8080/8761; puertos alternativos documentados |

---

## 11. Definición de terminado

1. `git clone` + `cp .env.example .env` + `docker compose up -d` deja el sistema operativo sin pasos manuales no documentados.
2. 9 contenedores `healthy`; Eureka lista 4 instancias de servicio.
3. Una request de negocio atraviesa gateway → pedidos → inventario → PostgreSQL y devuelve 2xx con `X-Service-Instance` y `X-Data-Source`.
4. Los 8 escenarios, más P06b, tienen evidencia generada por script y análisis escrito, con tiempos de detección y recuperación medidos.
5. `mvn verify` verde con tests unitarios del selector de réplicas y de la lógica de negocio.
6. Ningún secreto en el repositorio; `.env` ignorado y `.env.example` versionado.
7. README explica cómo iniciar, verificar, detener y **recuperar** cada componente.
8. Repositorio publicado con commits legibles y video accesible desde el README.
