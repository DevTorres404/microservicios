# Apply progress — `sistema-microservicios-distribuidos`

Slice aplicado: **W0 + W1** (andamiaje, contrato de build, activación de TDD y Eureka standalone).
Ejecutado el **2026-11-18** sobre `feature/sistema-microservicios-distribuidos`.

Modo de implementación: **Standard** en W0-1…W0-9 (sin runner al inicio) y **Strict TDD** desde
W0-10, cuando `strict_tdd: true` se activó con el primer test verde.

## 1. Tareas completadas

| Tarea | Estado | Evidencia observada |
| :-- | :-- | :-- |
| W0-1 Inicializar Git con `main` y commit raíz | ✅ | `git rev-parse --abbrev-ref HEAD` → `main`; commit raíz `6e8a9e5` |
| W0-2 Repositorio remoto en GitHub | ⛔ **No ejecutada (fuera de alcance)** | El usuario pidió un único PR al final y todavía no existe remoto. Prohibido configurar remoto, push o PR en esta fase. |
| W0-3 `.gitignore` con `.env`, `target/`, `*.log`, IDE | ✅ | `git check-ignore -v .env` → `.gitignore:5:.env` |
| W0-4 `.env.example` sin secretos | ✅ | `POSTGRES_PASSWORD=` presente en `.env.example`; `git ls-files --error-unmatch .env` → *did not match* |
| W0-5 `.gitattributes` con `*.sh text eol=lf` | ✅ | línea 4 del archivo; `eureka-lease-probe.sh` tiene 0 pares CRLF |
| W0-6 Matriz de versiones verificada (ADR-10) | ✅ | `docs/decisiones.md` ADR-10; tabla oficial `spring.io` 2025.0.x ↔ Boot 3.5.x |
| W0-7 Parent POM con BOMs y 5 módulos | ✅ | `mvn -q -N validate` → exit 0 |
| W0-8 Módulos vacíos que compilan | ✅ | `mvn -T 1C -DskipTests package` → 6/6 `SUCCESS` |
| W0-9 Primer test real (RED→GREEN) con `DataSourceKey` | ✅ | RED: `mvn -q -pl libs/persistence-routing -am test` → exit 1 (`cannot find symbol: DataSourceKey`); GREEN: 3 tests, 0 fallos |
| W0-10 Activar `strict_tdd: true` | ✅ | `strict_tdd: true` (línea 25) y `rules.apply.tdd: true` (línea 82) |
| W0-11 CI mínimo con JDK 21 | ✅ | `.github/workflows/build.yml` con `mvn -q verify`; local `mvn -q verify` → exit 0 |
| W1-1 Eureka standalone con ventana de expulsión corta | ✅ | `EurekaServerApplicationTests` 4/4 verdes; servidor levantado localmente: `GET /eureka/apps` → 200 `{"applications":…}` |
| W1-2 `eureka-server/Dockerfile` multi-stage no root | ⚠️ Verificada parcialmente | `docker build -t eureka-server:local eureka-server/` → exit 0, digest `sha256:7595d5d7…`. El `HEALTHCHECK` no se ejercitó: la fase prohíbe levantar contenedores; su validación en estado `healthy` corresponde a W7-4. |
| W1-3 `scripts/eureka-lease-probe.sh` | ✅ | `bash scripts/eureka-lease-probe.sh` → exit 0, expulsión medida **17 s**, re-registro **995 ms** |

## 2. Comandos ejecutados y resultado observado

| Comando | Resultado |
| :-- | :-- |
| `java -version` | `openjdk 21.0.12.1 2026-08-18 LTS` (Temurin) |
| `mvn -version` | `Apache Maven 3.9.16`, runtime `jdk-21.0.12.1+1` |
| `mvn -q -N validate` | exit 0 |
| `mvn -T 1C -DskipTests package` | exit 0 — 6/6 módulos `SUCCESS` |
| `mvn -q -pl libs/persistence-routing -am test` (RED) | exit 1 — `cannot find symbol: variable DataSourceKey` |
| `mvn -q -pl libs/persistence-routing -am test` (GREEN) | exit 0 — `Tests run: 3, Failures: 0, Errors: 0` |
| `mvn -q -pl eureka-server -am test` (RED) | exit -1 — `IllegalStateException: Unable to find a @SpringBootConfiguration` |
| `mvn -q -pl eureka-server -am test` (GREEN) | exit 0 — `Tests run: 4, Failures: 0, Errors: 0` |
| `mvn -q -pl eureka-server -am package` | exit 0 — jar ejecutable de 57 806 925 bytes con `BOOT-INF/` |
| `mvn -q verify` | exit 0 — 7 tests en total (3 + 4), 0 fallos |
| `java -jar eureka-server/target/…jar` + `curl /actuator/health` | `200` a los 7 s de arranque |
| `curl -H 'Accept: application/json' http://127.0.0.1:8761/eureka/apps` | `200` → `{"applications":{"versions__delta":"1","apps__hashcode":"","application":[]}}` |
| `bash scripts/eureka-lease-probe.sh` | exit 0 — `evicted=true`, `eviction_measured_ms=17067`, `reregistration_measured_ms=995`, `result=pass` |
| `docker build -t eureka-server:local eureka-server/` | exit 0 — imagen creada (`eureka-server:local`) |

## 3. TDD Cycle Evidence (Strict TDD desde W0-10)

| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
| :-- | :-- | :-- | :-- | :-- | :-- | :-- | :-- |
| W0-9 | `libs/persistence-routing/src/test/java/…/DataSourceKeyTest.java` | Unit | N/A (nuevo) | ✅ Escrito antes del enum; ejecución exit 1 por símbolo inexistente | ✅ `Tests run: 3, Failures: 0` | ✅ 3 casos (vocabulario cerrado, rechazo de nombres fuera del vocabulario, estabilidad de nombres) | ➖ Ninguno necesario |
| W0-10 | — (configuración) | N/A | N/A | ➖ Sin código de producción | ✅ `grep 'strict_tdd: true'` | ➖ Config única | ➖ Ninguno |
| W0-11 | — (CI) | N/A | N/A | ➖ Sin comportamiento de aplicación | ✅ `mvn -q verify` exit 0 | ➖ Config única | ➖ Ninguno |
| W1-1 | `eureka-server/src/test/java/…/EurekaServerApplicationTests.java` | Integration (`@SpringBootTest`) | N/A (nuevo) | ✅ Escrito antes de la clase `@SpringBootApplication`; exit -1 por falta de configuración | ✅ `Tests run: 4, Failures: 0` | ✅ 4 casos (4 parámetros del servidor, lease 5/15 + prefer-ip, sin auto-registro, `GET /eureka/apps` 200) | ➖ Ninguno necesario |
| W1-2 | — (imagen) | N/A | N/A | ➖ Sin lógica de aplicación | ⚠️ Build verificado; healthcheck no ejercitado | ➖ N/A | ➖ N/A |
| W1-3 | `scripts/eureka-lease-probe.sh` (arnés de ejecución, no unit test) | Runtime harness | N/A (nuevo) | ➖ Script de verificación, no código de producción | ✅ Ejecutado contra registro vivo: expulsión 17 s, re-registro 995 ms | ✅ Tres fases (alta, expulsión, re-registro) medidas | ➖ Ninguno |

**Test summary**: 7 tests escritos, 7 en verde (Unit 3, Integration 4). Triangulación: 2 archivos con
múltiples casos por comportamiento. Refactor: no fue necesario; el código es mínimo por diseño.

## 4. Work Unit Evidence

| Unidad | Comando focalizado y resultado | Arnés de ejecución | Frontera de rollback |
| :-- | :-- | :-- | :-- |
| Repositorio + higiene (W0-1,3,4,5) | `git check-ignore -v .env` → `.gitignore:5`; `git ls-files --error-unmatch .env` → sin coincidencia | N/A: no hay frontera de ejecución, solo higiene de repositorio | `.gitignore`, `.env.example`, `.gitattributes` y el commit raíz |
| Matriz de versiones (W0-6) | `grep -A8 'ADR-10' docs/decisiones.md` → ambas versiones presentes | N/A: decisión documental | `docs/decisiones.md` ADR-10 y la sección 3.1 de `openspec/project-context.md` |
| Reactor (W0-7,8) | `mvn -T 1C -DskipTests package` → 6/6 `SUCCESS` | N/A: solo compilación, sin runtime | `pom.xml` y los cinco `pom.xml` de módulo (los `.gitkeep` de `src/main/java`) |
| Vocabulario de nodos (W0-9) | `mvn -q -pl libs/persistence-routing -am test` → 3/3 | N/A: tipo puro, sin frontera de runtime | `libs/persistence-routing` completo |
| TDD estricto (W0-10) | `grep -n 'strict_tdd: true' openspec/config.yaml` → línea 25 | N/A: configuración | Bloque `strict_tdd`/`rules.apply.tdd` de `openspec/config.yaml` |
| CI (W0-11) | `mvn -q verify` → exit 0 | N/A: el workflow solo corre en GitHub | `.github/workflows/build.yml` |
| Registro Eureka (W1-1) | `mvn -q -pl eureka-server -am test` → 4/4 | Servidor real en `:8761`: `GET /eureka/apps` → 200 | `eureka-server/` (clase principal, `application.yml`, `pom.xml`) |
| Imagen (W1-2) | `docker build -t eureka-server:local eureka-server/` → exit 0 | `N/A` en esta fase: el `HEALTHCHECK` en estado `healthy` se valida en W7-4, porque esta fase prohíbe levantar contenedores | `eureka-server/Dockerfile` |
| Sonda de lease (W1-3) | `bash scripts/eureka-lease-probe.sh` → exit 0 | Registro Eureka real en `:8761`; expulsión 17 067 ms, re-registro 995 ms | `scripts/eureka-lease-probe.sh` |

## 5. Desviaciones del plan

1. **W0-2 no se ejecutó.** La instrucción de la fase prohíbe remoto, push y PR; el repositorio remoto
   se creará cuando el usuario pida el PR único final. La tarea queda `[ ]`.
2. **`spring-boot-starter-parent` como padre en lugar de importar solo `spring-boot-dependencies`.**
   `design.md` §1 pide importar los dos BOM. Con `starter-parent` el `dependencyManagement` de
   `spring-boot-dependencies` 3.5.16 llega heredado **y** se hereda `pluginManagement`
   (Surefire 3.5.6, Compiler 3.14.1, `spring-boot-maven-plugin`), que un BOM importado no aporta.
   El objetivo verificable —ningún módulo declara versión de dependencia— se cumple. Documentado en
   ADR-10 como límite aceptado.
3. **Umbral de la sonda de lease = 20 s, no 15 s.** `design.md` §10 fija `lease-expiration=15 s` y
   `eviction-interval=3 s`; la expulsión observable es la suma (≈18 s). La medición real fue **17 s**.
   El texto de aceptación de W1-3 («≤15 s») es incompatible con los valores que el propio diseño
   exige, así que el script imprime la descomposición (`lease_duration_seconds=15`,
   `eviction_sweep_ms=3000`) y compara contra 20 s. Requiere reconciliación de `design.md`/`tasks.md`.
4. **`Dockerfile` construye desde el jar ya empaquetado.** El comando de verificación fija el contexto
   en `eureka-server/`, que no puede ver el parent POM del reactor; compilar dentro de la imagen exigiría
   cambiar el contexto a la raíz del repositorio (lo natural en W7). El Dockerfile es multi-stage real
   (etapa JDK valida el jar, etapa JRE solo transporta el artefacto) y documenta el prerrequisito
   `mvn -pl eureka-server -am -DskipTests package`.
5. **`EurekaInstanceConfigBean` se inyecta como tipo concreto en el test** porque el contrato
   `com.netflix.appinfo.EurekaInstanceConfig` no expone `isPreferIpAddress()`.
6. **Docker Desktop se arrancó** para verificar W1-2 (estaba detenido). No se levantó ningún
   contenedor; solo se construyó la imagen. El motor quedó en ejecución.

## 6. Pendientes y riesgos

- **W0-2** queda bloqueada por decisión del usuario (sin remoto todavía).
- **`HEALTHCHECK` de W1-2** sin ejercitar: se valida en W7-4, que arranca Eureka en Compose.
- **Línea `2025.0.x` marcada EOL** en la tabla oficial: es la correcta para Boot 3.5.x, pero no
  recibirá parches. Cualquier bump de versión exige repetir ADR-10.
- **W1-3 vs `design.md` §10**: reconciliar el umbral numérico antes de que W8-4 (P03) lo cite.
- `docs/decisiones.md` contiene solo ADR-10; ADR-01…09 se escriben en W9-3.
- Los módulos `gateway`, `servicio-pedidos`, `servicio-inventario` están vacíos a propósito: su
  comportamiento es W2–W5.
- `libs/persistence-routing` contiene hoy solo `DataSourceKey`; el resto del núcleo es W6.
