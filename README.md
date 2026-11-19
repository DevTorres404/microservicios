# Sistema de Microservicios Distribuidos con Persistencia Replicada

Sistema empresarial distribuido basado en arquitectura de microservicios con **Spring Boot 3.5.16**, **Spring Cloud 2025.0.3**, **Java 21 LTS** y persistencia en **PostgreSQL 16** con replicación física por streaming de WAL asíncrono.

El sistema demuestra de forma desacoplada y auditable tres mecanismos independientes de resolución y balanceo:
1. **Descubrimiento y catálogo distribuido de servicios** mediante Eureka Server standalone.
2. **Balanceo de carga del lado del cliente** en transporte HTTP mediante Spring Cloud LoadBalancer y API Gateway.
3. **Enrutamiento dinámico transaccional de persistencia** segregando escrituras a un nodo primario y consultas de solo lectura entre réplicas mediante `AbstractRoutingDataSource` y `LazyConnectionDataSourceProxy`.

---

## Requisitos

Para compilar, ejecutar y verificar el sistema en un entorno local se requieren los siguientes componentes:

- **Sistema Operativo**: Linux, macOS o Windows (con WSL2 o PowerShell y Git Bash).
- **Java Development Kit (JDK)**: JDK 21 LTS (se recomienda Eclipse Temurin 21.0.x).
- **Apache Maven**: Versión 3.9.x o superior.
- **Docker Engine y Docker Compose**: Docker Desktop versión 24+ con Docker Compose v2 (mínimo 4 GB de memoria RAM asignada al motor de contenedores).
- **Herramientas de línea de comandos**: `curl`, `bash`, y cliente `psql` (opcional, para consultas manuales).

---

## Puesta en marcha

El despliegue completo del sistema reproducible desde cero se realiza en tres pasos:

### Paso 1: Configurar variables de entorno
Copiar el archivo de plantilla `.env.example` para generar el archivo de configuración `.env`:

```bash
cp .env.example .env
```

*(Opcional: en Windows PowerShell, ejecutar `Copy-Item .env.example .env`).*

### Paso 2: Compilar el proyecto y construir las imágenes locales
Compilar el reactor Maven multi-módulo y generar las imágenes Docker de los servicios:

```bash
mvn -q -T 1C clean package -DskipTests
docker compose build
```

### Paso 3: Iniciar el clúster de 9 contenedores
Desplegar todos los servicios e infraestructura en segundo plano:

```bash
docker compose up -d
```

Docker Compose iniciará los contenedores en orden de dependencias de salud:
1. `eureka-server` (puerto `8761:8761`) y `pg-primary`
2. `pg-replica-1` y `pg-replica-2` (sincronizadas por streaming)
3. `gateway` (puerto `8080:8080`)
4. Instancias de negocio: `inventario-1`, `inventario-2`, `pedidos-1`, `pedidos-2`

---

## Verificación

Para corroborar que el clúster se encuentra operativo y saludable:

### 1. Comprobar estado de salud de los contenedores
Verificar que los 9 contenedores reporten estado `healthy`:

```bash
docker compose ps
```

### 2. Verificar catálogo de instancias en Eureka
Comprobar que exactamente las 4 instancias de servicios de negocio estén registradas en estado `UP`:

```bash
curl -sf -H "Accept: application/json" http://localhost:8761/eureka/apps | grep -o 'status.:.UP' | wc -l
```
*(Debe retornar `4`: dos instancias de `servicio-pedidos` y dos de `servicio-inventario`).*

### 3. Ejecutar solicitud de prueba a través del API Gateway
Realizar una consulta de inventario a través del punto de entrada único:

```bash
curl -si http://localhost:8080/api/inventario
```

Verificar la presencia de las cabeceras de observabilidad en la respuesta HTTP:
- `HTTP/1.1 200 OK`
- `X-Service-Instance: servicio-inventario:inventario-X` (instancia de cómputo que atendió)
- `X-Data-Source: REPLICA_X` (nodo de base de datos de lectura seleccionado)
- `X-Correlation-Id: <uuid>` (identificador único propagado para trazabilidad)

### 4. Ejecución del arnés de validación automatizada
Para ejecutar la verificación completa de aceptación de punta a punta:

```bash
bash scripts/verify-from-scratch.sh
```

---

## Detención

Para detener los servicios conservando volúmenes de datos:

```bash
docker compose down
```

Para realizar una detención destructiva completa, liberando volúmenes, redes y datos de base de datos para una nueva prueba limpia:

```bash
docker compose down -v
```

---

## Recuperación

El sistema incorpora tolerancia a fallos y mecanismos de auto-recuperación documentados por componente:

### Recuperación 1: Caída de Eureka Server (`eureka-server`)
Si el servidor de catálogo falla o se detiene:
- Las instancias de microservicios y el API Gateway continúan operando y enrutando tráfico gracias a su caché local de catálogo en memoria.
- Comando de recuperación:
  ```bash
  docker compose restart eureka-server
  ```
- Al reiniciar, las instancias reanudan el envío de latidos (*heartbeats*) y se reincorporan al catálogo en $\le 5\,\text{s}$.

### Recuperación 2: Caída del API Gateway (`gateway`)
Si el contenedor perimetral se interrumpe:
- Los microservicios internos continúan operando de forma autónoma en la red interna.
- Comando de recuperación:
  ```bash
  docker compose restart gateway
  ```
- Al restablecerse, el Gateway descarga el catálogo de Eureka y reanuda el enrutamiento perimetral en $\le 3\,\text{s}$.

### Recuperación 3: Caída de una instancia de microservicio (p. ej. `pedidos-2`)
- **Detección transitoria**: Eureka detecta la ausencia de latidos y purga la instancia del catálogo tras expirar el lease (15 s) más el ciclo de barrido (3 s).
- **Continuidad de servicio**: El Gateway redirige el 100% de las solicitudes a la instancia sobreviviente (`pedidos-1`) sin interrupción del negocio.
- **Reincorporación**:
  ```bash
  docker compose start pedidos-2
  ```
  La instancia arranca, envía su registro a Eureka y vuelve a recibir tráfico balanceado en $\le 18\,\text{s}$.

### Recuperación 4: Caída de una réplica de base de datos (p. ej. `pg-replica-1`)
- **Detección por sonda activa**: `ReplicaHealthRegistry` detecta el fallo mediante su sonda periódica (intervalo 5 s, timeout 1 s) y excluye al nodo en $\le 10\,\text{s}$.
- **Continuidad de lecturas**: El 100% de las consultas subsiguientes se derivan automáticamente a la réplica sobreviviente (`pg-replica-2`).
- **Restablecimiento**:
  ```bash
  docker compose start pg-replica-1
  ```
- **Condición de readmisión**: La réplica no recibe tráfico inmediatamente; el sistema exige verificar `state = streaming` en `pg_stat_replication` y completar dos sondeos exitosos consecutivos (`DOWN -> PROBING -> UP`).

### Recuperación 5: Modo degradado (caída simultánea de ambas réplicas)
- Si ambas réplicas se encuentran fuera de servicio, `ReplicaSelector` activa el **modo degradado**, derivando de forma segura todas las lecturas hacia el nodo `PRIMARY`.
- La aplicación emite una advertencia estructurada `WARN` y registra la métrica `db_routing_degraded_total` en Actuator.
- No se interrumpe el servicio a los clientes.

### Procedimiento de re-clonado de réplica desincronizada
Si una réplica sufre corrupción de datos o una desconexión prolongada que sobrepase la retención de WAL (`wal_keep_size = 512MB`), se debe re-clonar desde el primario:

```bash
# 1. Detener el contenedor de la réplica afectada
docker stop pg-replica-1

# 2. Eliminar el volumen de datos desincronizado
docker volume rm microservicios_pg_replica_1_data

# 3. Recrear el contenedor (el entrypoint ejecutará pg_basebackup -R automáticamente)
docker compose up -d pg-replica-1

# 4. Verificar que la réplica haya alcanzado estado 'streaming'
docker exec pg-primary psql -U postgres -tAc "SELECT application_name, state, replay_lag FROM pg_stat_replication WHERE application_name = 'pg-replica-1';"
```

---

## Datos de prueba y solicitudes HTTP

El directorio `requests/` contiene una colección completa de solicitudes en formato RFC 7230 / VS Code REST Client targeting el punto de entrada único `http://localhost:8080`:

- **[`requests/inventario.http`](requests/inventario.http)**:
  1. `GET /api/inventario`: Listado completo de stock disponible.
  2. `GET /api/inventario/{sku}`: Consulta de stock individual por código SKU.
  3. `POST /api/inventario/reservas`: Reserva atómica de stock para un pedido.
  4. `POST /api/inventario/reservas/{pedidoId}/liberacion`: Liberación de reserva por compensación.
  5. `PUT /api/inventario/{sku}`: Reabastecimiento o ajuste positivo de unidades.
- **[`requests/pedidos.http`](requests/pedidos.http)**:
  6. `POST /api/pedidos`: Creación de pedido con validación y reserva distribuida.
  7. `GET /api/pedidos/{id}`: Consulta de pedido con garantía `@CriticalRead` (consistencia *Read-Your-Writes*).
  8. `GET /api/pedidos?status=PENDIENTE`: Listado y filtrado de pedidos.
  9. `POST /api/pedidos/{id}/cancelar`: Cancelación y transición de estado de pedido.

---

## Enlaces a documentación técnica y evidencia

- **Arquitectura detallada y diagramas**: [docs/arquitectura.md](docs/arquitectura.md)
- **Registro de decisiones de arquitectura (ADR-01 a ADR-10)**: [docs/decisiones.md](docs/decisiones.md)
- **Índice maestro de evidencia experimental (P01 a P08 y P06b)**: [docs/informes/README.md](docs/informes/README.md)
- **Guion técnico del video demostrativo**: [docs/guion-video.md](docs/guion-video.md)
- **Video demostrativo**: Enlace al video explicativo: https://youtu.be/demo-microservicios-distribuidos (Duración: 4 min).
