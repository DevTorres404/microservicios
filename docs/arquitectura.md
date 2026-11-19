# Arquitectura del Sistema de Microservicios Distribuidos

Documento descriptivo de la arquitectura global, topología de red, flujos de comunicación y persistencia replicada del sistema.

---

## 1. Visión general del sistema

El sistema implementa una arquitectura orientada a microservicios desacoplados construida sobre **Java 21**, **Spring Boot 3.5.16** y **Spring Cloud 2025.0.3**, con persistencia relacional en **PostgreSQL 16** mediante replicación física por streaming de WAL asíncrono.

El entorno de ejecución está compuesto por 9 contenedores coordinados mediante Docker Compose:

1. **`eureka-server`**: Servidor de descubrimiento de servicios en modo standalone.
2. **`gateway`**: API Gateway reactivo basado en Spring Cloud Gateway (punto de entrada unificado para clientes externos).
3. **`servicio-pedidos` (2 instancias: `pedidos-1`, `pedidos-2`)**: Gestión de ciclo de vida de pedidos, agregados, máquina de estados y cálculo de totales.
4. **`servicio-inventario` (2 instancias: `inventario-1`, `inventario-2`)**: Gestión de catálogo de stock, reservas atómicas y liberaciones por compensación.
5. **Clúster PostgreSQL (3 nodos: `pg-primary`, `pg-replica-1`, `pg-replica-2`)**: Base de datos primaria de lectura/escritura y dos réplicas físicas de solo lectura en modo *hot standby*.

---

## 2. Diagrama de arquitectura y topología de componentes

```mermaid
flowchart TD
    subgraph Host["Host / Cliente Externo"]
        Client["Cliente HTTP / Pruebas de Carga"]
    end

    subgraph Edge["Perímetro de Red Pública"]
        GW["API Gateway<br/>Puerto 8080:8080"]
        EUK["Eureka Server<br/>Puerto 8761:8761"]
    end

    subgraph Services["Red Interna: Microservicios de Aplicación"]
        P1["pedidos-1<br/>Puerto interno 8082"]
        P2["pedidos-2<br/>Puerto interno 8082"]
        I1["inventario-1<br/>Puerto interno 8081"]
        I2["inventario-2<br/>Puerto interno 8081"]
    end

    subgraph Persistence["Red Interna: Clúster PostgreSQL Replicado"]
        PG_PRI["pg-primary<br/>Puerto interno 5432<br/>pedidos_db / inventario_db"]
        PG_REP1["pg-replica-1<br/>Puerto interno 5432<br/>hot standby"]
        PG_REP2["pg-replica-2<br/>Puerto interno 5432<br/>hot standby"]
    end

    Client -->|Peticiones HTTP| GW
    Client -.->|Monitoreo /apps| EUK

    P1 -.->|Heartbeat 5s| EUK
    P2 -.->|Heartbeat 5s| EUK
    I1 -.->|Heartbeat 5s| EUK
    I2 -.->|Heartbeat 5s| EUK
    GW -.->|Consulta de catálogo| EUK

    GW -->|RoundRobinLoadBalancer| P1
    GW -->|RoundRobinLoadBalancer| P2
    GW -->|RoundRobinLoadBalancer| I1
    GW -->|RoundRobinLoadBalancer| I2

    P1 -->|Feign + CB + LB| I1
    P1 -->|Feign + CB + LB| I2
    P2 -->|Feign + CB + LB| I1
    P2 -->|Feign + CB + LB| I2

    P1 -->|RoutingDataSource| PG_PRI
    P1 -->|RoutingDataSource| PG_REP1
    P1 -->|RoutingDataSource| PG_REP2

    P2 -->|RoutingDataSource| PG_PRI
    P2 -->|RoutingDataSource| PG_REP1
    P2 -->|RoutingDataSource| PG_REP2

    I1 -->|RoutingDataSource| PG_PRI
    I1 -->|RoutingDataSource| PG_REP1
    I1 -->|RoutingDataSource| PG_REP2

    I2 -->|RoutingDataSource| PG_PRI
    I2 -->|RoutingDataSource| PG_REP1
    I2 -->|RoutingDataSource| PG_REP2

    PG_PRI ==>|Streaming asincrono WAL| PG_REP1
    PG_PRI ==>|Streaming asincrono WAL| PG_REP2
```

---

## 3. Puertos de red y fronteras de exposición

Para garantizar aislamiento arquitectónico y seguridad perimetral, la configuración expone hacia el host exclusivamente los dos puertos de entrada necesarios:

| Servicio | Contenedor | Puerto Host | Puerto Interno | Visibilidad | Propósito |
| :-- | :-- | :-- | :-- | :-- | :-- |
| `gateway` | `gateway` | `8080` | `8080` | Pública (Host) | Punto de entrada unificado para todas las operaciones de API REST y enrutamiento perimetral. |
| `eureka-server` | `eureka-server` | `8761` | `8761` | Pública (Host) | Consola de descubrimiento y endpoint de registro de metadatos de instancias. |
| `servicio-pedidos` | `pedidos-1`, `pedidos-2` | *Ninguno* | `8082` | Red interna Docker | Acceso exclusivo vía API Gateway o mediante llamadas Feign internas. |
| `servicio-inventario` | `inventario-1`, `inventario-2` | *Ninguno* | `8081` | Red interna Docker | Acceso exclusivo vía API Gateway o mediante llamadas Feign internas. |
| PostgreSQL Primario | `pg-primary` | *Ninguno* | `5432` | Red interna Docker | Persistencia primaria de lectura y escritura; sin acceso directo desde el host. |
| PostgreSQL Réplica 1 | `pg-replica-1` | *Ninguno* | `5432` | Red interna Docker | Réplica física asíncrona de lectura; sin acceso directo desde el host. |
| PostgreSQL Réplica 2 | `pg-replica-2` | *Ninguno* | `5432` | Red interna Docker | Réplica física asíncrona de lectura; sin acceso directo desde el host. |

---

## 4. Distinción de los tres mecanismos de selección

Un aspecto fundamental de este sistema es la separación estricta entre los tres mecanismos independientes de resolución y balanceo:

| Mecanismo | Tecnología | Capa | Responsabilidad | Modo de fallo y mitigación |
| :-- | :-- | :-- | :-- | :-- |
| **1. Descubrimiento de Procesos** | **Eureka** (`DiscoveryClient`) | Catálogo distribuido de red | Registro dinámico de procesos en ejecución (`instance-id`), verificación de latidos (*heartbeats*) y expulsión por expiración de *lease*. | Si una instancia falla, Eureka la purga tras el vencimiento de su *lease* (15 s) más el ciclo del temporizador de barrido (3 s). |
| **2. Selección de Instancia HTTP** | **Spring Cloud LoadBalancer** | Transporte HTTP / Red cliente | Distribución equitativa (*round-robin*) de solicitudes HTTP entre las instancias saludables registradas en la caché local del cliente. | Si una instancia cae, el Circuit Breaker / reintento mitiga los errores durante la ventana transitoria de refresco de caché (3 s). |
| **3. Enrutamiento de Persistencia** | **RoutingDataSource** (`libs/persistence-routing`) | Transaccional JDBC en aplicación | Selección dinámica de la conexión física a base de datos (`PRIMARY` para escritura, `REPLICA_1` / `REPLICA_2` para lectura). | Si una réplica cae, la sonda activa la excluye en $\le 10\,\text{s}$; si caen ambas, entra en modo degradado transparente hacia `PRIMARY`. |

> **Nota arquitectónica**: `@Transactional(readOnly = true)` **no balancea tráfico HTTP ni selecciona instancias de microservicios**. Su función se limita a informar el modo de la transacción al aspecto AOP de persistencia para que el `RoutingDataSource` derive la consulta a una réplica de PostgreSQL.

---

## 5. Comunicación inter-servicio y resiliencia

Cuando un cliente crea un pedido (`POST /api/pedidos`), `servicio-pedidos` se comunica con `servicio-inventario` para reservar el stock requerido:

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant GW as API Gateway
    participant Pedidos as servicio-pedidos
    participant CB as Resilience4j Circuit Breaker
    participant Inv as servicio-inventario
    participant DB as RoutingDataSource

    Cliente->>GW: POST /api/pedidos (X-Correlation-Id: abc-123)
    GW->>Pedidos: Enrutamiento lb://SERVICIO-PEDIDOS
    Note over Pedidos: InstanceHeaderFilter captura X-Correlation-Id
    Pedidos->>DB: begin transaction (readOnly=false -> PRIMARY)
    Pedidos->>DB: INSERT INTO orders (status=PENDIENTE)
    
    Pedidos->>CB: InventarioClient.reserveStock(orderId, lines)
    alt Inventario disponible
        CB->>Inv: POST /api/inventario/reservas (X-Correlation-Id propagado)
        Inv->>DB: UPDATE stock SET available = available - qty WHERE available >= qty
        Inv-->>CB: 200 OK (ReservationResponse)
        CB-->>Pedidos: 200 OK
        Pedidos->>DB: UPDATE orders SET status=CONFIRMADO, total=Calculado
        Pedidos-->>GW: 201 Created (OrderDTO completo)
        GW-->>Cliente: 201 Created (X-Correlation-Id: abc-123)
    else Inventario no disponible (Timeout / Instancias caidas)
        CB-->>Pedidos: Fallback: InventarioClientFallbackFactory
        Note over Pedidos: Fallback atrapa excepcion y rechaza transaccion
        Pedidos->>DB: UPDATE orders SET status=RECHAZADO
        Pedidos-->>GW: 503 Service Unavailable (RFC 9457 ProblemDetail)
        GW-->>Cliente: 503 Service Unavailable (INVENTARIO_NO_DISPONIBLE)
    end
```

---

## 6. Flujo de enrutamiento transaccional de persistencia

Para resolver el problema de adquisición prematura de conexiones en Spring Data JPA e Hibernate, el sistema implementa la cadena de proxies diferidos:

```mermaid
sequenceDiagram
    autonumber
    participant App as Controlador / Servicio
    participant TxInt as TransactionInterceptor
    participant Proxy as LazyConnectionDataSourceProxy
    participant Aspect as ReadWriteRoutingAspect
    participant Holder as DataSourceContextHolder (ThreadLocal)
    participant Router as RoutingDataSource
    participant Pool as HikariPool (PRIMARY / REPLICAS)

    App->>TxInt: Invocación de método anotado
    TxInt->>Proxy: getConnection() al iniciar transacción
    Note over Proxy: Retorna proxy ligero de Connection.<br/>NO se solicita conexión física al pool.
    
    TxInt->>Aspect: Ejecución del aspecto (@Order 100)
    Note over Aspect: Inspecciona TransactionSynchronizationManager<br/>y presencia de @CriticalRead.
    Aspect->>Holder: push(REPLICA_1 o PRIMARY)
    
    App->>Proxy: Primera sentencia SQL (SELECT ...)
    Proxy->>Router: getConnection() real
    Router->>Holder: peek() -> Obtiene clave activa
    Router->>Pool: getConnection() del pool seleccionado
    Pool-->>Proxy: Conexión física establecida
    Proxy-->>App: Ejecución de la sentencia SQL
    
    TxInt->>Aspect: Post-ejecución / commit
    Aspect->>Holder: pop() (limpieza garantizada en finally)
```

### Características clave del diseño:
- **`LazyConnectionDataSourceProxy`**: Permite que el aspecto AOP capture el modo transaccional y establezca la clave en el `ThreadLocal` **antes** de que el pool entregue la conexión física.
- **`ThreadLocal<Deque<DataSourceKey>>`**: Estructura de pila que tolera transacciones anidadas (`REQUIRES_NEW`), restaurando la clave previa al completar la transacción interna.
- **`lenientFallback = false`**: Claves nulas resuelven de forma segura a `PRIMARY`; cualquier clave desconocida o inválida provoca un fallo ruidoso inmediato, evitando desvíos silenciosos.
