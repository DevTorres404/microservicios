# Decisiones de arquitectura (ADR)

Registro consolidado de decisiones de arquitectura para el sistema de microservicios distribuidos con persistencia replicada.

---

## ADR-01 — Mecanismo de selección de nodo de base de datos

**Decisión**: La selección dinámica del nodo de base de datos se implementa en la capa de aplicación mediante la combinación de `AbstractRoutingDataSource`, `LazyConnectionDataSourceProxy`, un contexto `ThreadLocal<Deque<DataSourceKey>>` y un aspecto AOP (`ReadWriteRoutingAspect`) configurado con precedencia `@Order(100)` sobre el interceptor de `@Transactional`.

### Contexto

El sistema requiere dirigir automáticamente las transacciones de escritura hacia el nodo primario (`PRIMARY`) y distribuir las transacciones de solo lectura entre dos réplicas asíncronas (`REPLICA_1` y `REPLICA_2`). Se debe mantener la transparencia total hacia las capas de repositorio y servicios de dominio basados en Spring Data JPA e Hibernate, sin obligar al desarrollador a seleccionar datasources de forma manual en cada consulta.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Múltiples `DataSource` inyectados con `@Qualifier` por repositorio | Dispersa la política en múltiples componentes; permite que un desarrollador escriba por descuido en una réplica; rompe el modelo estándar de un único `EntityManagerFactory` por unidad de persistencia. |
| AOP manual con `TransactionTemplate` programático sin `AbstractRoutingDataSource` | Obliga a abandonar la gestión declarativa `@Transactional`; duplica la gestión de sesiones de Hibernate y pierde la sincronización de recursos del contenedor Spring. |
| Dos `EntityManagerFactory` y dos `TransactionManager` independientes | Duplica metadatos y mapeos relacionales; transforma el routing en una decisión dependiente de la anotación de inyección y no de la política operativa. |
| Proxy externo de conexión (PgBouncer, HAProxy o pgpool) | Queda fuera del alcance arquitectónico del laboratorio; oculta la política interna a la aplicación e impide recolectar métricas y cabeceras de observabilidad requeridas (`X-Data-Source`). |

### Consecuencias y límites aceptados

- **Positivo**: Punto único de decisión centralizado en `determineCurrentLookupKey()`; transparencia absoluta para los repositorios de Spring Data.
- **Aclaración crítica**: `@Transactional(readOnly = true)` **no balancea nada por sí solo**. La anotación únicamente declara el modo transaccional; la derivación física la ejecuta el selector de réplicas en conjunción con el aspecto AOP. Si se elimina el aspecto, el 100% de las operaciones se dirigen al primario sin fallos aparentes.
- **Límite aceptado**: La decisión de enrutamiento se adopta una sola vez por transacción (no por sentencia SQL aislada). Si un método transaccional de escritura ejecuta lecturas previas, dichas lecturas se ejecutan en el nodo primario para garantizar consistencia dentro de la transacción.

### Verificación

```bash
mvn -q -pl libs/persistence-routing -am test -Dtest='RoutingDataSourceTest,ReadWriteRoutingAspectTest'
```

---

## ADR-02 — Detección de réplica caída mediante sondeo activo

**Decisión**: La salud de las réplicas de base de datos se gestiona mediante un componente de sondeo periódico activo (`ReplicaHealthRegistry`) ejecutado cada 5000 ms con un timeout estricto de 1000 ms, complementado con una democión inmediata reactiva ante cualquier fallo al solicitar conexiones físicas del pool de Hikari.

### Contexto

En una arquitectura con replicación asíncrona, las réplicas pueden quedar inaccesibles por caída del contenedor, saturación de conexiones o interrupción de la red. Si el sistema dependiera únicamente de los errores lanzados durante las peticiones de los usuarios para descubrir la falla, el usuario final experimentaría retardos prolongados debidos al timeout de adquisición de conexiones.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Exclusión reactiva exclusiva ante fallos de usuario | El primer usuario en consultar la réplica caída paga la penalización completa del timeout; un socket semiabierto no arroja error inmediato; no distingue entre nodo disponible pero desincronizado. |
| Monitoreo basado únicamente en el healthcheck del contenedor Docker | Evalúa el estado del proceso del sistema operativo del contenedor, no la ruta de comunicación JDBC real ni el estado de sincronización del proceso receptor de WAL. |

### Consecuencias y límites aceptados

- **Positivo**: Detección determinista y cuantificable en un tiempo $\le 10\,\text{s}$ (5 s de intervalo + 1 s de timeout de sonda); aislamiento total de los hilos de atención HTTP.
- **Límite aceptado**: Sobrecarga fija de dos conexiones de sondeo cada 5 segundos por instancia de aplicación; ventana transitoria máxima de 5 segundos donde una caída abrupta entre sondeos puede afectar una petición, la cual es interceptada y traducida a un error HTTP 503 tipado (`DB_NODE_UNAVAILABLE`).

### Verificación

```bash
mvn -q -pl libs/persistence-routing -am test -Dtest='ReplicaHealthRegistryTest'
bash scripts/failure-drill.sh replica pg-replica-1
```

---

## ADR-03 — Política determinista de reparto de lecturas (Round-Robin)

**Decisión**: La distribución de lecturas ordinarias entre réplicas saludables se realiza mediante un algoritmo round-robin estricto basado en un cursor atómico `AtomicInteger` sobre una vista inmutable del conjunto de réplicas en estado `UP`.

### Contexto

Se requiere un reparto equitativo del tráfico de consulta entre los nodos de réplica disponibles (`REPLICA_1` y `REPLICA_2`), garantizando que la distribución sea auditable, determinista y verificable empíricamente mediante pruebas de carga.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Selección aleatoria | Comportamiento no determinista; imposibilita la predicción exacta de distribución en secuencias cortas y dificulta la auditoría no circular de la evidencia. |
| Balanceo por menor número de conexiones activas (Least-Connections) | Requiere instrumentación distribuida y sincronización de estado entre hilos con un beneficio marginal para un conjunto acotado de dos réplicas simétricas. |
| Afinada por clave de negocio (Sticky hashing por cliente o SKU) | Introduce sesgos de distribución severos ante cardinalidades bajas y contradice el objetivo de alternancia simétrica de lecturas. |

### Consecuencias y límites aceptados

- **Positivo**: Máximo desbalance teórico de $\le 1$ solicitud entre réplicas operativas; operación no bloqueante mediante lectura atómica de referencias inmutables.
- **Límite aceptado**: El balanceador round-robin no considera la duración ni el costo computacional de las consultas individuales; si una réplica experimenta mayor lentitud pero permanece saludable, continuará recibiendo su turno de consulta.

### Verificación

```bash
mvn -q -pl libs/persistence-routing -am test -Dtest='ReplicaSelectorTest'
bash scripts/load-test.sh
```

---

## ADR-04 — Topología de persistencia y aislamiento lógico multi-tenant

**Decisión**: El entorno de persistencia se implementa mediante un único clúster PostgreSQL de tres nodos (un primario de escritura `pg-primary` y dos réplicas de lectura `pg-replica-1` y `pg-replica-2`), alojando dos bases de datos lógicas independientes (`pedidos_db` e `inventario_db`) con usuarios, contraseñas y permisos estrictamente segregados (`pedidos_app` e `inventario_app`).

### Contexto

Los principios de diseño de microservicios exigen autonomía y propiedad exclusiva de los datos por cada dominio de negocio. Al mismo tiempo, los recursos computacionales del entorno de evaluación (Docker Desktop / WSL2) imponen una cota estricta en la cantidad de contenedores ejecutables en simultáneo (9 contenedores en total).

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Clúster PostgreSQL dedicado por cada microservicio (6 contenedores de BD) | Requeriría 15 contenedores en total para el sistema completo, sobrepasando los límites de memoria y CPU del entorno local. |
| Base de datos única compartida con esquemas lógicos | Viola el principio de aislamiento; permite cruces accidentales de datos si las credenciales de base de datos no están rígidamente segregadas. |
| Replicación lógica mediante tablas duplicadas | Descartada por especificación del enunciado, que exige replicación física por streaming WAL. |

### Consecuencias y límites aceptados

- **Positivo**: Cumplimiento del límite de 9 contenedores; segregación estricta de permisos verificada mediante tests donde ningún usuario puede consultar o alterar tablas del otro servicio.
- **Límite aceptado**: El clúster físico constituye un punto único de fallo compartido para la disponibilidad de infraestructura. Si el nodo `pg-primary` cae, se interrumpe la capacidad de escritura de ambos servicios simultáneamente (la promoción automática de réplicas queda fuera del alcance del laboratorio).

### Verificación

```bash
bash scripts/check-boundaries.sh
```

---

## ADR-05 — Imagen PostgreSQL de streaming replication y digest inmutable

**Decisión**: El clúster PostgreSQL se compone de la imagen oficial **`postgres:16`** pineada por digest inmutable (`postgres@sha256:fe03a7605299a34ddf5e4f285dff78c3d7190a576b3c6b46f2fcff69f4bffd54`, Debian 16.14) con entrypoints dedicados (`infra/postgres/primary-entrypoint.sh` y `infra/postgres/replica-entrypoint.sh`), aprovisionamiento de replicación vía `pg_basebackup -R` con `passfile` (`.pgpass` modo 0600) y cero credenciales expuestas en `postgresql.auto.conf`.

### Contexto

El sistema requiere un nodo primario de escritura y dos réplicas asíncronas de sólo lectura (`pg-primary`, `pg-replica-1`, `pg-replica-2`). Se requiere garantizar reproducibilidad absoluta del entorno evitando derivas de versiones, descontinuaciones de imágenes de terceros o credenciales en texto plano persistidas en la configuración automática.

### Alternativas descartadas

| Alternativa | Por qué se descarta |
| :-- | :-- |
| `bitnami/postgresql` con variables de replicación | Distribución inestable: migración de tags a `bitnamilegacy`, tags flotantes sujetos a eliminación o cambios sin aviso. |
| Credenciales en `primary_conninfo` en `postgresql.auto.conf` | Viola el principio de no persistir secretos en texto plano en la configuración del motor; `passfile` en `.pgpass` con permisos 0600 aísla la credencial. |
| Slots de replicación con `max_replication_slots=0` | En primarios con slots deshabilitados (`max_replication_slots=0`), `pg_basebackup` requiere explícitamente `--no-slot` para apoyarse en `wal_keep_size=512MB`. |

### Consecuencias y límites aceptados

- **Positivo**: Reproducible, auditable, sin dependencias de repositorios comerciales externos.
- **Límite**: Ausencia de failover automático (fuera de alcance del laboratorio; las réplicas son estrictamente de lectura).

### Verificación

```bash
bash scripts/smoke-postgres-replication.sh
docker exec pg-replica-1 psql -U replicator -d postgres -tAc 'SELECT pg_is_in_recovery();'
```

---

## ADR-06 — Ejecución de migraciones Flyway contra el DataSource primario

**Decisión**: La ejecución de migraciones de base de datos con Flyway se desacopla de la autoconfiguración de Spring Boot y se cablea explícitamente sobre el bean `primaryDataSource`, garantizando que ninguna sentencia DDL intente ejecutarse sobre el `RoutingDataSource` ni sobre las réplicas.

### Contexto

Flyway aplica scripts de migración DDL (`CREATE TABLE`, `CREATE INDEX`, etc.) durante la fase de inicialización del contexto de Spring Boot. Las réplicas de PostgreSQL en modo `hot_standby` son de solo lectura y rechazan de inmediato cualquier operación DDL con error fatal de base de datos.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Permitir que la autoconfiguración de Flyway consuma el `RoutingDataSource` | Funciona únicamente de forma accidental cuando el contexto `ThreadLocal` está vacío y el router devuelve el primario por defecto; se rompe si la migración se ejecuta en un contexto transaccional previo. |
| Ejecutar migraciones independientes contra cada nodo de réplica | Imposible en PostgreSQL: las réplicas en `hot_standby` no admiten DDL y reciben los cambios estructurales automáticamente por replicación de WAL. |

### Consecuencias y límites aceptados

- **Positivo**: Migración determinista, atómica y segura sobre el único nodo autoritativo de escritura.
- **Límite aceptado**: Las réplicas deben sincronizar el WAL de migración antes de admitir consultas que hagan referencia a tablas nuevas (garantizado por la secuencia de inicio de Docker Compose y el estado de streaming).

### Verificación

```bash
mvn -q -pl servicio-pedidos,servicio-inventario -am test -Dtest='*MigrationIT'
```

---

## ADR-07 — Consistencia eventual y anotación `@CriticalRead`

**Decisión**: Para evitar lecturas inconsistentes (*stale reads*) tras operaciones de escritura en escenarios de alta criticidad, el sistema provee la anotación `@CriticalRead`, la cual es interceptada por `ReadWriteRoutingAspect` para desviar la consulta directamente hacia el nodo `PRIMARY`, aun cuando el método esté marcado como `@Transactional(readOnly = true)`.

### Contexto

En sistemas con replicación física asíncrona, existe un retardo temporal inherente (*replay lag*, típicamente de 1 a 5 ms) entre la confirmación local de una transacción en el nodo primario y su aplicación efectiva en las réplicas. Si un cliente emite un `POST` para crear un pedido y seguidamente ejecuta un `GET` para consultarlo, una lectura en la réplica podría responder un error 404 transitorio si el registro aún no fue replicado.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Forzar todas las consultas de lectura al nodo primario | Elimina el propósito de la persistencia replicada e impide descargar de tráfico al nodo de escritura. |
| Bloqueo artificial de lectura esperando `replay_lag = 0` | Introduce latencia no predecible en las solicitudes HTTP; acopla la aplicación a la consulta interna del log sequence number (LSN) del motor. |
| Retornar la entidad completa en la respuesta del `POST` sin consulta posterior | Práctica recomendada que se adopta en los controladores, pero resulta insuficiente cuando clientes desacoplados consultan el estado inmediatamente tras el webhook de confirmación. |

### Consecuencias y límites aceptados

- **Positivo**: Garantía estricta de consistencia *Read-Your-Writes* en flujos de negocio críticos sin alterar la distribución de las lecturas ordinarias.
- **Límite aceptado**: Requiere uso disciplinado y selectivo; el abuso de `@CriticalRead` sobrecargaría el nodo primario anulando el beneficio de las réplicas.

### Verificación

```bash
mvn -q -pl servicio-pedidos -am test -Dtest='CriticalReadWhitelistTest'
bash scripts/evidence-p07.sh --runs 20
```

---

## ADR-08 — Adopción de replicación física asíncrona por streaming de WAL

**Decisión**: La replicación de datos del clúster PostgreSQL se configura en modo de streaming físico asíncrono, estableciendo `synchronous_standby_names = ''` y manteniendo `synchronous_commit = on` en el nodo primario.

### Contexto

El sistema busca maximizar el rendimiento y la disponibilidad de las transacciones de escritura, desacoplando la confirmación de escrituras de la latencia de red hacia las réplicas.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Replicación síncrona estricta (`synchronous_standby_names = '*'`) | La caída o retraso de cualquiera de las réplicas bloquearía de inmediato las confirmaciones de escritura en el primario, reduciendo la disponibilidad global del sistema. |
| Replicación lógica basada en triggers o publicación/suscripción | No soporta de forma nativa la replicación integral de secuencias y DDL complejos sin configuración manual minuciosa por tabla. |

### Consecuencias y límites aceptados

- **Positivo**: Máximo rendimiento en operaciones de escritura; la indisponibilidad de una o ambas réplicas no impide continuar registrando transacciones en el primario.
- **Límite aceptado**: Posibilidad de pérdida de transacciones no replicadas si el primario sufriera una destrucción catastrófica no recuperable; presencia de una ventana transitoria de retardo (*replay lag*) mitigada por `@CriticalRead`.

### Verificación

```bash
docker exec pg-primary psql -U postgres -tAc "SELECT sync_state FROM pg_stat_replication;"
```

---

## ADR-09 — Biblioteca técnica compartida para enrutamiento de persistencia

**Decisión**: Toda la infraestructura de enrutamiento dinámico, selección de réplicas, monitoreo de salud y métricas se encapsula en un módulo compartido agnóstico de negocio denominado `libs/persistence-routing`, prohibiendo estrictamente la inclusión de entidades JPA, repositorios o contratos de dominio en dicho módulo.

### Contexto

Tanto `servicio-pedidos` como `servicio-inventario` requieren la misma política de acceso a persistencia replicada. Duplicar las clases de enrutamiento en cada microservicio generaría divergencia de código y complicaría el mantenimiento.

### Alternativas descartadas

| Alternativa | Motivo del descarte |
| :-- | :-- |
| Duplicar el código de routing en cada microservicio | Viola el principio DRY; incrementa el riesgo de bugs en la sincronización de algoritmos de salud y métricas. |
| Incorporar entidades o DTOs de dominio en la biblioteca compartida | Introduce acoplamiento indebido entre microservicios, violando la autonomía de dominios de la arquitectura de microservicios. |

### Consecuencias y límites aceptados

- **Positivo**: Mantenimiento centralizado y testeado unitariamente de la infraestructura de persistencia con cobertura exhaustiva; reusabilidad limpia en ambos servicios.
- **Límite aceptado**: Acoplamiento técnico a nivel de dependencias de compilación; se controla mediante el script de barrido arquitectónico `scripts/check-boundaries.sh` para verificar que el módulo permanezca estrictamente técnico.

### Verificación

```bash
mvn -q -pl libs/persistence-routing -am test
bash scripts/check-boundaries.sh
```

---

## ADR-10 — Build fijado en Spring Boot 3.5.16 y Spring Cloud 2025.0.3 sobre JDK 21

**Decisión**: El reactor Maven se construye con **Spring Boot 3.5.16** (último parche de la línea 3.5.x publicado en Maven Central) y **Spring Cloud 2025.0.3** (último servicio de la release train 2025.0.x, «Northfields»), sobre **JDK 21** (`maven.compiler.release=21`) y Maven 3.9.x.

### Contexto

El plan fija Java 21 LTS y la línea Boot 3.5.x / Cloud 2025.0.x, pero deja la matriz de compatibilidad como pregunta abierta (§19 de `design.md`) y prohíbe escribir el parent POM antes de confirmarla. Una combinación incompatible no falla en `mvn validate`: falla en tiempo de ejecución (Eureka, Gateway o LoadBalancer con clases puente ausentes), o degrada el arranque sin error visible.

### Evidencia consultada

| Fuente | Qué confirma | Fecha de consulta |
| :-- | :-- | :-- |
| <https://spring.io/projects/spring-cloud#support> — Tabla 1 «Release train / Spring Boot compatibility» | `2025.0.x` (Northfields) ↔ `Spring Boot 3.5.x`; `2025.1.x` (Oakwood) ↔ Boot 4.0.x/4.1.x | 2026-11-18 |
| <https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes> | Notas de la release train 2025.0.x | 2026-11-18 |
| `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml` | Última versión 3.5.x disponible: `3.5.16` | 2026-11-18 |
| `https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dependencies/maven-metadata.xml` | Última versión 2025.0.x disponible: `2025.0.3` | 2026-11-18 |

### Versiones exactas y digests

| Artefacto | Versión | Digest verificado |
| :-- | :-- | :-- |
| `org.springframework.boot:spring-boot-starter-parent` (padre del reactor) | `3.5.16` | `spring-boot-dependencies-3.5.16.pom` SHA-1 `c7d6b11f985fb61d9cdc0544a933c0a5722ac221` (Central no publica `.sha256` para este POM) |
| `org.springframework.cloud:spring-cloud-dependencies` (BOM importado) | `2025.0.3` | `spring-cloud-dependencies-2025.0.3.pom` SHA-256 `0c832713369dbdca8e1644576b208064a1619f03954af52a1ae5c22ec1c58079` |
| JDK | Temurin `21.0.12.1+1` LTS | Verificado localmente con `mvn -version` (runtime `C:\Users\Damian\tools\jdk-21.0.12.1+1`) |
| Maven | `3.9.16` | Verificado localmente con `mvn -version` |
| PostgreSQL (imágenes) | `16` | `postgres@sha256:fe03a7605299a34ddf5e4f285dff78c3d7190a576b3c6b46f2fcff69f4bffd54` (ratificado en W7-2) |

### Alternativas descartadas

| Alternativa | Por qué se descarta |
| :-- | :-- |
| Spring Boot 4.0.x/4.1.x + Spring Cloud 2025.1.x | Cambia la release train completa y con ella Micrometer, Resilience4j y el modelo de arranque; `design.md` §1 lo prohíbe explícitamente sin rediseño. Además el enunciado y el plan fijan Boot 3.5.x. |
| Spring Cloud 2026.0.x (RC) | Release candidate: no es una base reproducible para un entregable evaluable. |
| `spring-cloud-dependencies` 2025.1.x sobre Boot 3.5.x | La matriz oficial lo declara incompatible (2025.1.x apunta a Boot 4.x). |
| Dejar las versiones sin parche (`3.5.x`) o usar rangos | `mvn` no resuelve rangos de forma determinista; rompe la reproducibilidad exigida por `deployment-environment`. |

### Consecuencias y límites aceptados

- **Positivo**: combinación declarada compatible por la fuente oficial; ningún módulo declara versión de dependencia (todo llega por BOM), lo que hace que `mvn -T 1C verify` sea reproducible.
- **Límite 1**: la matriz oficial marca `2025.0.x` con asterisco de *end of life*. La línea sigue siendo la correcta para Boot 3.5.x, pero no recibirá correcciones nuevas. Subirla exige migrar a Boot 4.x y a la train 2025.1.x: es un cambio de matriz, no un bump de parche.
- **Límite 2**: los parches dentro de 3.5.x/2025.0.x son compatibles por contrato de la train, pero cualquier bump posterior (por ejemplo 3.5.17) debe repetir esta verificación y actualizar este ADR.
- **Límite 3**: `spring-boot-starter-parent` se usa como padre para heredar `pluginManagement` (Surefire, Compiler, `spring-boot-maven-plugin`). Importar solo `spring-boot-dependencies` como BOM obligaría a versionar cada plugin a mano; el resultado funcional es el mismo y el riesgo de deriva de versiones es mayor.

### Verificación

```bash
grep -A8 'ADR-10' docs/decisiones.md | grep -qE 'Spring Boot 3\.5\.[0-9]+'
grep -A8 'ADR-10' docs/decisiones.md | grep -qE 'Spring Cloud 2025\.0\.[0-9]+'
mvn -q -N validate
```
