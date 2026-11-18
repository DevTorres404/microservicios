# Decisiones de arquitectura (ADR)

Registro de decisiones del sistema de microservicios distribuidos.

> Estado de este archivo: contiene **ADR-10**, que es la decisión bloqueante de W0 (matriz de
> versiones del build). Los ADR-01 a ADR-09 se completan en W9-3 a partir de `design.md` §3.
> No se declaran aquí decisiones que todavía no estén ratificadas con evidencia.

## ADR-10 — Build fijado en Spring Boot 3.5.16 y Spring Cloud 2025.0.3 sobre JDK 21

**Decisión**: el reactor Maven se construye con **Spring Boot 3.5.16** (último parche de la línea
3.5.x publicado en Maven Central) y **Spring Cloud 2025.0.3** (último servicio de la release train
2025.0.x, «Northfields»), sobre **JDK 21** (`maven.compiler.release=21`) y Maven 3.9.x.

### Contexto

El plan fija Java 21 LTS y la línea Boot 3.5.x / Cloud 2025.0.x, pero deja la matriz de
compatibilidad como pregunta abierta (§19 de `design.md`) y prohíbe escribir el parent POM antes de
confirmarla. Una combinación incompatible no falla en `mvn validate`: falla en tiempo de ejecución
(Eureka, Gateway o LoadBalancer con clases puente ausentes), o degrada el arranque sin error visible.

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
| PostgreSQL (imágenes) | `16` | Fijado en `.env.example`; el digest se ratifica en W7-2 |

### Alternativas descartadas

| Alternativa | Por qué se descarta |
| :-- | :-- |
| Spring Boot 4.0.x/4.1.x + Spring Cloud 2025.1.x | Cambia la release train completa y con ella Micrometer, Resilience4j y el modelo de arranque; `design.md` §1 lo prohíbe explícitamente sin rediseño. Además el enunciado y el plan fijan Boot 3.5.x. |
| Spring Cloud 2026.0.x (RC) | Release candidate: no es una base reproducible para un entregable evaluable. |
| `spring-cloud-dependencies` 2025.1.x sobre Boot 3.5.x | La matriz oficial lo declara incompatible (2025.1.x apunta a Boot 4.x). |
| Dejar las versiones sin parche (`3.5.x`) o usar rangos | `mvn` no resuelve rangos de forma determinista; rompe la reproducibilidad exigida por `deployment-environment`. |

### Consecuencias y límites aceptados

- **Positivo**: combinación declarada compatible por la fuente oficial; ningún módulo declara versión
  de dependencia (todo llega por BOM), lo que hace que `mvn -T 1C verify` sea reproducible.
- **Límite 1**: la matriz oficial marca `2025.0.x` con asterisco de *end of life*. La línea sigue
  siendo la correcta para Boot 3.5.x, pero no recibirá correcciones nuevas. Subirla exige migrar a
  Boot 4.x y a la train 2025.1.x: es un cambio de matriz, no un bump de parche.
- **Límite 2**: los parches dentro de 3.5.x/2025.0.x son compatibles por contrato de la train, pero
  cualquier bump posterior (por ejemplo 3.5.17) debe repetir esta verificación y actualizar este ADR.
- **Límite 3**: `spring-boot-starter-parent` se usa como padre para heredar `pluginManagement`
  (Surefire, Compiler, `spring-boot-maven-plugin`). Importar solo `spring-boot-dependencies` como BOM
  obligaría a versionar cada plugin a mano; el resultado funcional es el mismo y el riesgo de deriva
  de versiones es mayor.

### Verificación

```bash
grep -A8 'ADR-10' docs/decisiones.md | grep -qE 'Spring Boot 3\.5\.[0-9]+'
grep -A8 'ADR-10' docs/decisiones.md | grep -qE 'Spring Cloud 2025\.0\.[0-9]+'
mvn -q -N validate
```
