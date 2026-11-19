# Guion Técnico para Video Demostrativo

**Sistema de Microservicios Distribuidos con Replicación de Base de Datos y Balanceo**  
**Duración Total Estimada:** 4 minutos (240 segundos)  
**Objetivo de Duración por Bloque:** ~40 segundos  
**Idioma:** Español neutro técnico  

---

## Bloque 1: Arquitectura General y Topología del Sistema
- **Duración Estimada:** 40 segundos (00:00 - 00:40)
- **Objetivo:** Presentar los componentes de la arquitectura, sus responsabilidades, puertos expuestos y la separación estricta de dominios.
- **Acción en Pantalla:**
  - Mostrar el diagrama de arquitectura de `docs/arquitectura.md`.
  - Terminal mostrando `docker compose ps` con los 9 contenedores en estado `healthy`.
- **Narración:**
  > "Bienvenidos a la demostración técnica del Sistema de Microservicios Distribuidos. La arquitectura está compuesta por nueve contenedores gestionados mediante Docker Compose.
  > Como punto único de entrada al sistema opera el API Gateway en el puerto 8080. Para el descubrimiento dinámico de servicios contamos con Eureka Server en el puerto 8761. 
  > En la capa de negocio operan dos microservicios con límites de contexto estrictamente aislados: el Servicio de Pedidos y el Servicio de Inventario, cada uno desplegado con dos instancias idénticas para garantizar alta disponibilidad.
  > Finalmente, en la capa de datos contamos con un clúster PostgreSQL 16 compuesto por un nodo primario de lectura y escritura y dos réplicas asíncronas de solo lectura mediante replicación por streaming físico."

---

## Bloque 2: Descubrimiento de Procesos y Balanceo de Carga HTTP
- **Duración Estimada:** 40 segundos (00:40 - 01:20)
- **Objetivo:** Evidenciar el registro dinámico de instancias en Eureka y el balanceo de carga en capa de transporte mediante Spring Cloud LoadBalancer.
- **Acción en Pantalla:**
  - Navegador o `curl` consultando `http://localhost:8761/eureka/apps` mostrando las 4 instancias registradas en estado `UP`: `pedidos-1`, `pedidos-2`, `inventario-1` e `inventario-2`.
  - Terminal ejecutando peticiones consecutivas al API Gateway (`curl -si http://localhost:8080/api/inventario | grep X-Service-Instance`).
- **Narración:**
  > "Examinamos el panel de Eureka Server: observamos cuatro instancias registradas y saludables, dos por cada servicio, identificadas unívocamente mediante sus metadatos de despliegue.
  > Al emitir solicitudes concurrentes al API Gateway a través de la ruta `/api/inventario`, Spring Cloud LoadBalancer aplica un algoritmo round-robin sobre las instancias vivas.
  > Inspeccionando el encabezado de respuesta HTTP `X-Service-Instance`, comprobamos cómo el tráfico se distribuye equitativamente entre `inventario-1` e `inventario-2`, demostrando la independencia entre el mecanismo de descubrimiento y la selección de transporte."

---

## Bloque 3: Comunicación Inter-Servicios y Trazabilidad Distribuida
- **Duración Estimada:** 40 segundos (01:20 - 02:00)
- **Objetivo:** Demostrar el flujo transaccional entre servicios, la propagación de `X-Correlation-Id` y el tratamiento de excepciones tipadas RFC 9457 ProblemDetail.
- **Acción en Pantalla:**
  - Ejecución de `POST http://localhost:8080/api/pedidos` para crear un pedido con reserva de stock.
  - Mostrar logs simultáneos de `pedidos-1` e `inventario-1` compartiendo el mismo `X-Correlation-Id`.
  - Demostrar la respuesta de error controlado (409 Conflict o 503 Service Unavailable con ProblemDetail).
- **Narración:**
  > "Cuando un cliente crea un pedido mediante `POST /api/pedidos`, el Servicio de Pedidos se comunica de manera síncrona con el Servicio de Inventario para reservar los artículos solicitados.
  > Para asegurar observabilidad completa, el API Gateway genera o preserva un identificador de correlación `X-Correlation-Id`, el cual se propaga automáticamente en los encabezados HTTP y se inyecta en el contexto de logging Mapped Diagnostic Context de ambos microservicios.
  > Si el servicio de inventario no cuenta con stock suficiente o está temporalmente inaccesible, el fallo se traduce de manera uniforme en un objeto RFC 9457 ProblemDetail con código tipado, garantizando que el contrato del API permanezca robusto y desacoplado."

---

## Bloque 4: Persistencia Replicada y Enrutamiento Dinámico de Consultas
- **Duración Estimada:** 40 segundos (02:00 - 02:40)
- **Objetivo:** Demostrar la replicación física streaming en PostgreSQL y el enrutamiento dinámico mediante `AbstractRoutingDataSource`.
- **Acción en Pantalla:**
  - Consulta SQL a `pg_stat_replication` en `pg-primary` mostrando `state = streaming` y `replay_lag`.
  - Petición de lectura `GET /api/inventario` mostrando `X-Data-Source: REPLICA_1` o `REPLICA_2`.
  - Petición de escritura `POST /api/pedidos` mostrando `X-Data-Source: PRIMARY`.
  - Petición de lectura crítica `@CriticalRead` en `GET /api/pedidos/{id}` mostrando `X-Data-Source: PRIMARY`.
- **Narración:**
  > "En el clúster de base de datos, el nodo primario transmite el registro de escritura WAL de forma continua a las dos réplicas en modo streaming.
  > A nivel de aplicación, implementamos un `RoutingDataSource` respaldado por un proxy perezoso `LazyConnectionDataSourceProxy` y un aspecto AOP. 
  > Las transacciones de solo lectura (`@Transactional(readOnly = true)`) adquieren conexión física exclusivamente en los nodos de réplica, como confirma el encabezado `X-Data-Source: REPLICA`.
  > Las transacciones de modificación van dirigidas obligatoriamente al primario. Además, para evitar inconsistencias por retraso de replicación en lecturas inmediatas posteriores a una escritura, la anotación `@CriticalRead` fuerza el enrutamiento al primario garantizando consistencia read-your-writes."

---

## Bloque 5: Simulación de Fallos e Inyección de Resiliencia
- **Duración Estimada:** 40 segundos (02:40 - 03:20)
- **Objetivo:** Ejecutar simulacros en vivo de parada de instancia de aplicación y caída de réplica de base de datos.
- **Acción en Pantalla:**
  - Ejecución de `docker compose stop pedidos-2`.
  - Envío sostenido de peticiones a `/api/pedidos` demostrando continuidad de servicio atendida por `pedidos-1`.
  - Ejecución de `docker compose stop pg-replica-1` y `docker compose stop pg-replica-2`.
  - Demostración de lecturas ordinarias atendidas en modo degradado por `pg-primary`, con incremento de métrica `db_routing_degraded_total`.
- **Narración:**
  > "Procedemos a evaluar la resiliencia del sistema ante fallos. Primero, detenemos de forma abrupta el contenedor `pedidos-2`. Al emitir tráfico concurrente, el LoadBalancer detecta el fallo tras una breve ventana transitoria y redirige el 100% de las solicitudes a `pedidos-1` sin interrupción de cara al cliente.
  > A continuación, simulamos una pérdida total de réplicas deteniendo `pg-replica-1` y `pg-replica-2`. En lugar de propagar un fallo catastrófico, el selector de fuentes activa el Modo Degradado hacia el nodo primario.
  > Las consultas continúan respondiendo con éxito directamente desde el primario, emitiendo una alerta estructurada y actualizando la métrica en Micrometer."

---

## Bloque 6: Conclusiones Técnicas y Garantías del Diseño
- **Duración Estimada:** 40 segundos (03:20 - 04:00)
- **Objetivo:** Sintetizar las lecciones de arquitectura, trade-offs y reproducibilidad del entorno.
- **Acción en Pantalla:**
  - Mostrar los informes en `docs/informes/` y la ejecución del script automatizado `scripts/verify-from-scratch.sh`.
  - Demostración de reinicio limpio y reincorporación de réplicas en streaming.
- **Narración:**
  > "En conclusión, este diseño demuestra cómo desacoplar y orquestar tres mecanismos independientes de enrutamiento: el registro y descubrimiento de procesos en Eureka, el balanceo HTTP a nivel de aplicación en el Gateway, y la segregación lectura-escritura en la capa de persistencia.
  > Mediante las nueve pruebas experimentales documentadas en el repositorio, cuantificamos una latencia de replicación típicamente inferior a 50 milisegundos y tiempos de detección y reconducción transparentes.
  > Todo el entorno es 100% reproducible y auditable a partir de un único comando de validación. Muchas gracias por su atención."
