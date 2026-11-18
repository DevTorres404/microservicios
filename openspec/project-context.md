# Contexto del proyecto — microservicios

Artefacto de bootstrap SDD. Generado por `sdd-init` el **2026-11-18**.
Sustituye al guardado en Engram `sdd-init/microservicios` (modo `engram`), que no pudo realizarse en esta sesión.

---

## 1. Identidad del proyecto

| Campo | Valor |
| :-- | :-- |
| Nombre | `microservicios` |
| Ruta | `C:\Users\Damian\Documents\workspace\microservicios` |
| Tipo | Greenfield — no existe código de aplicación |
| Persistencia SDD | `openspec` (archivos planos); el CLI `openspec` no está instalado |
| Repositorio Git | **No inicializado** (no existe `.git`) |
| Idioma de artefactos | Español neutro/profesional (entregable académico) |

## 2. Documentos fuente

| Archivo | Rol |
| :-- | :-- |
| `Actividad_Microservicios_Distribuidos.md` | Enunciado académico: arquitectura mínima, escenarios P01–P08, entregables y rúbrica sobre 100 puntos. |
| `PLAN_IMPLEMENTACION.md` | Plan técnico acordado: decisiones D1–D7, arquitectura objetivo, paquetes W0–W10, riesgos y definición de terminado. |
| `.gitignore` | Ignora únicamente `.atl/` (estado local de runtime de IA). |

No existen archivos de convención de proyecto (`AGENTS.md`, `CLAUDE.md`, `.cursorrules`, `GEMINI.md`, `copilot-instructions.md`) ni skills a nivel de proyecto.

## 3. Stack detectado

Herramientas verificadas en la máquina (ejecutadas durante la detección):

| Herramienta | Versión verificada | Nota |
| :-- | :-- | :-- |
| JDK | Temurin 21.0.12.1 LTS | Coincide con el objetivo Java 21 LTS. |
| Maven | 3.9.16 | `C:\Users\Damian\tools\apache-maven-3.9.16` |
| Docker | 29.8.0 | Necesario para el compose de 9 contenedores y para Testcontainers. |
| Git | 2.54.0.windows.1 | Repositorio aún no inicializado. |
| Node.js | 24.15.0 | No forma parte del stack de la aplicación; disponible para tooling auxiliar. |
| PowerShell | 5.1.26100.9818 | Los scripts `.ps1` no pueden usar el operador `??` ni sintaxis de PowerShell 7. |

Stack declarado en el plan (no verificable en disco sin `pom.xml`):

- **Java 21 LTS** + **Spring Boot 3.5.x** + **Spring Cloud 2025.0.x** (decisión D2, *pendiente de confirmación del equipo*).

### 3.1 Matriz de versiones confirmada (W0-6, 2026-11-18)

Verificada contra la tabla oficial de compatibilidad antes de generar el parent POM. Registro
completo y digests en `docs/decisiones.md` (ADR-10).

| Componente | Versión elegida | Evidencia |
| :-- | :-- | :-- |
| Spring Boot | `3.5.16` | `spring.io/projects/spring-cloud#support` Tabla 1: `2025.0.x` ↔ Boot `3.5.x`; `3.5.16` es el último parche de la línea en Maven Central. |
| Spring Cloud | `2025.0.3` | Misma tabla (release train «Northfields»); `2025.0.3` es el último servicio de la train en Maven Central. |
| JDK | Temurin `21.0.12.1+1` LTS | `mvn -version` local: runtime `C:\Users\Damian\tools\jdk-21.0.12.1+1`. |
| Maven | `3.9.16` | `mvn -version` local. |

Riesgo declarado: la matriz oficial marca `2025.0.x` como *end of life*. Es la train correcta para
Boot 3.5.x; subir a Boot 4.x exige cambiar de train completa, no un bump de parche.
- **Maven multi-módulo** con parent POM: `eureka-server`, `gateway`, `servicio-pedidos`, `servicio-inventario`.
- **PostgreSQL 16**: 1 primario + 2 réplicas asíncronas por streaming WAL, una base por servicio (`pedidos_db`, `inventario_db`).
- **Docker Compose** con 9 contenedores y red `backend`.
- **Node.js / Maven wrapper**: sin wrapper detectado; se asume `mvn` del PATH hasta que W0 lo decida.

## 4. Arquitectura objetivo

```text
Cliente HTTP
    │ :8080
    ▼
Spring Cloud Gateway ──descubre──▶ Eureka :8761
    ├── lb://servicio-pedidos    ──▶ pedidos-1 :8081 / pedidos-2 :8082
    │                                    └── Feign lb://servicio-inventario
    └── lb://servicio-inventario ──▶ inventario-1 :8083 / inventario-2 :8084

Persistencia (routing por servicio)
  pedidos-*    ──▶ RoutingDataSource ──▶ PRIMARY (escritura)
  inventario-* ──▶ RoutingDataSource ──▶ REPLICA_1 / REPLICA_2 (lectura)
  PostgreSQL primary :5432 ──WAL──▶ replica-1 / replica-2
```

Las tres decisiones se mantienen independientes y así deben documentarse:

1. **Eureka** descubre procesos (instancias de servicio), no bases de datos.
2. **Spring Cloud LoadBalancer** elige la instancia HTTP.
3. **`AbstractRoutingDataSource`** elige el nodo de base de datos.

Selección de nodo (política documentada, sección 4.2 del plan):

| Operación | Nodo |
| :-- | :-- |
| Escrituras (`INSERT`/`UPDATE`/`DELETE`, `@Transactional` no read-only) | `PRIMARY` |
| Lectura ordinaria | round-robin determinista sobre réplicas sanas |
| Lectura marcada `@CriticalRead` | `PRIMARY` (read-your-writes) |
| Réplica caída | se excluye del conjunto seleccionable |
| Todas las réplicas caídas | `PRIMARY` en modo degradado, con WARN y métrica |

## 5. Convenciones y reglas de frontera

- **Build único:** todo se construye con `mvn -T 1C verify` desde la raíz. No hay builds por módulo aislados.
- **Aislamiento de datos:** cada servicio es dueño de su base y su esquema. Prohibido `JOIN` o lectura de tablas ajenas.
- **Sin acoplamiento compartido:** no se comparten entidades JPA ni clases de dominio entre módulos; solo contratos HTTP (DTOs por servicio).
- **Dirección de dependencia:** `servicio-pedidos → servicio-inventario`. `servicio-inventario` no conoce a `servicio-pedidos`.
- **Cero URLs fijas:** ningún servicio llama a otro por host/puerto ni al gateway; siempre nombre lógico vía LoadBalancer. Verificación en revisión: `grep` de hostnames/puertos en `application.yml` debe dar cero.
- **Puertos:** solo 8080 (gateway) y 8761 (Eureka) se publican al host; el resto vive en la red `backend`.
- **Secretos:** sin credenciales en el repositorio; `.env` ignorado, `.env.example` versionado.
- **Line endings:** `.gitattributes` con `*.sh text eol=lf`.
- **Scripts de evidencia:** PowerShell 5.1 y Bash; deben generar la evidencia, no transcribirla a mano.
- **Idioma:** documentación, informes y README en español. Identificadores de código, comentarios y mensajes de commit en inglés (convención técnica por defecto).

## 6. Estado de implementación

Ningún paquete de trabajo está iniciado. El plan define la secuencia W0 → W2 → W3 → W6 → W7 → W8 → W9/W10 como ruta crítica.

| ID | Paquete | Estado |
| :-- | :-- | :-- |
| W0 | Andamiaje: Git + remoto, parent POM, módulos vacíos, `.env.example`, `.gitattributes`, CI `mvn -q verify` | ☐ |
| W1–W10 | Ver `PLAN_IMPLEMENTACION.md` §7 | ☐ |

## 7. Definición de terminado (resumen)

1. `git clone` + `cp .env.example .env` + `docker compose up -d` deja el sistema operativo sin pasos manuales no documentados.
2. 9 contenedores `healthy`; Eureka lista 4 instancias de servicio.
3. Una request de negocio atraviesa gateway → pedidos → inventario → PostgreSQL y devuelve 2xx con `X-Service-Instance` y `X-Data-Source`.
4. P01–P08 (+ P06b) con evidencia generada por script y análisis escrito, con tiempos medidos.
5. `mvn verify` verde con tests unitarios del selector de réplicas y de la lógica de negocio.
6. Ningún secreto en el repositorio.
7. README explica iniciar, verificar, detener y recuperar cada componente.
8. Repositorio publicado con commits legibles y video accesible desde el README.

---

## 8. Mirror de persistencia

| Destino Engram | Estado | Equivalente en archivo |
| :-- | :-- | :-- |
| `sdd-init/microservicios` (type: architecture) | **Pendiente** — Engram no disponible en esta sesión | Este documento |
| `sdd/microservicios/testing-capabilities` (type: config) | **Pendiente** | `openspec/testing-capabilities.md` |
| `skill-registry` (type: config) | **Pendiente** — el índice local ya existe y está vigente en `.atl/skill-registry.md` | `.atl/skill-registry.md` (pre-existente, validado, no modificado) |

Cuando Engram vuelva a estar disponible, guardar estos tres tópicos con `capture_prompt: false`
para completar el mirror sin duplicar contenido.
