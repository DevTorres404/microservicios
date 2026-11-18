# Capacidades de testing — microservicios

Artefacto de bootstrap SDD. Generado por `sdd-init` el **2026-11-18**.
Sustituye al guardado en Engram `sdd/microservicios/testing-capabilities` (no disponible en esta sesión).

## Testing Capabilities

**Strict TDD Mode**: disabled (provisional) — objetivo: `enabled` en W0
**Detected**: 2026-11-18
**Detection state**: `declared-target-only` — el proyecto es greenfield: no existe `pom.xml`, ni código, ni tests en disco.

### Justificación de Strict TDD

La tabla de decisión exige: sin runner de tests → `strict_tdd: false`. Ese es el estado real hoy.
El plan de implementación compromete tests unitarios (selector de réplicas, lógica de negocio) y
tests de repositorio con Testcontainers en W2, con `mvn verify` verde como criterio de terminado.

**Condición de activación (W0):** cuando exista el parent POM y `mvn -q test` ejecute al menos un test,
cambiar a `strict_tdd: true` en `openspec/config.yaml` y operar en ciclo RED-GREEN-REFACTOR.

### Test Runner

- Command: `mvn -q verify` (reactor completo) · `mvn -q test` (solo tests) · `mvn -q -pl <module> -am test` (módulo aislado)
- Framework: Maven Surefire + JUnit 5 (declarado; no instalado todavía)
- Estado: ❌ no presente en disco — se materializa en W0

### Test Layers

| Layer | Available | Tool |
| --- | --- | --- |
| Unit | ❌ (objetivo W2) | JUnit 5 + AssertJ + Mockito vía Surefire |
| Integration | ❌ (objetivo W2) | Testcontainers PostgreSQL (`postgres:16`) para repositorios y Flyway |
| E2E | ❌ (no planificado como test automatizado) | La validación end-to-end son los escenarios experimentales P01–P08 ejecutados contra Docker Compose con scripts en `scripts/` |

**Nota sobre E2E:** la rúbrica no exige un framework de E2E. La evidencia end-to-end se produce con
`scripts/load-test.*`, `scripts/failure-drill.*` y `scripts/collect-evidence.*`, y se documenta en
`docs/informes/P01..P08.md`. Agregar Playwright/Cypress sería trabajo no solicitado.

### Coverage

- Available: ❌
- Command: —
- Nota: JaCoCo no está en el plan ni en la rúbrica. Añadirlo es opcional; si se agrega, registrar el comando
  real en `openspec/config.yaml` (`verify.coverage_threshold`) en lugar de dejar un umbral inventado.

### Quality Tools

| Tool | Available | Command |
| --- | --- | --- |
| Linter | ❌ | — (opcional: Checkstyle o Spotless; no requerido por la rúbrica) |
| Type checker | ✅ (implícito en el compilador) | `mvn -q compile` |
| Formatter | ❌ | — (opcional: Spotless; no configurado) |

### Verificación no automatizada que sí es obligatoria

Estas comprobaciones reemplazan a un linter en los puntos que la rúbrica evalúa:

| Comprobación | Cómo se verifica |
| :-- | :-- |
| Cero hosts/puertos fijos en `application.yml` de los servicios | `grep` de hostnames y puertos; resultado esperado: cero coincidencias |
| Sin entidades JPA compartidas entre módulos | Revisión de imports entre módulos del reactor |
| Dependencia unidireccional `pedidos → inventario` | Revisión de `pom.xml` de dependencias entre módulos |
| Sin secretos en el repositorio | Revisión de `.gitignore` + ausencia de `.env` versionado |
| Line endings LF en `*.sh` | Regla en `.gitattributes` |

### Comandos de referencia (a confirmar en W0)

```bash
# Build completo del reactor
mvn -T 1C verify

# Solo tests
mvn -q test

# Un módulo con sus dependencias
mvn -q -pl servicio-pedidos -am test

# Empaquetado sin tests (build de contenedores)
mvn -q -DskipTests package

# Entorno integrado
docker compose up -d --build
docker compose ps
```

### Dependencias de testing previstas (W0/W2)

- `spring-boot-starter-test` (JUnit 5, AssertJ, Mockito, MockMvc)
- `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter`
- `spring-boot-testcontainers` (`@ServiceConnection`)
- Imagen de integración: `postgres:16` oficial — **no** `bitnami/postgresql`, cuya disponibilidad de tags es un
  riesgo abierto del plan (§10) y no debe propagarse a los tests.

---

## Mirror de persistencia

| Destino Engram | Estado | Equivalente en archivo |
| :-- | :-- | :-- |
| `sdd/microservicios/testing-capabilities` (type: config) | **Pendiente** — Engram no disponible | Este documento |

Guardar con `capture_prompt: false` cuando Engram vuelva a estar disponible.
