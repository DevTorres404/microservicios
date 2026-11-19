# Índice de informes de evidencia experimental (P01–P08 y P06b)

Este directorio reúne los nueve informes de validación empírica del sistema de microservicios distribuidos, generados a partir de la ejecución de scripts versionados sobre el entorno de 9 contenedores.

Cada informe sigue rigurosamente la estructura estandarizada de seis secciones:
1. **Objetivo**: descripción técnica y especificaciones puestas a prueba.
2. **Procedimiento**: pasos reproducibles y comandos ejecutados sin conocimiento tácito.
3. **Resultado esperado**: criterios de aceptación y cotas teóricas cuantificadas.
4. **Resultado obtenido**: mediciones empíricas, latencias y tasas de éxito observadas.
5. **Evidencia observada**: transcripción y extractos de las fuentes primarias capturadas.
6. **Análisis técnico**: análisis causal, justificación arquitectural y contrastes no circulares.

---

## Tabla de escenarios de prueba y fuentes primarias de evidencia

| Escenario | Título del informe | Fuente primaria exigida | Estado de ejecución | Informe |
| :-- | :-- | :-- | :-- | :-- |
| P01 | Descubrimiento de servicios y enrutamiento por nombre lógico | `GET /eureka/apps` crudo, con los 4 `instance-id` | ✅ Ejecutado y validado | [P01.md](P01.md) |
| P02 | Balanceo de carga HTTP y distribución de tráfico (100 peticiones) | Archivo por solicitud (`results.csv`) y log de acceso de cada instancia | ✅ Ejecutado y validado | [P02.md](P02.md) |
| P03 | Caída y recuperación de instancia de microservicio (`pedidos-2`) | `GET /eureka/apps` muestreado y log de lease de Eureka con marcas temporales | ✅ Ejecutado y validado | [P03.md](P03.md) |
| P04 | Comunicación inter-servicio, trazabilidad y fallback tipado (Feign + CB) | Logs de pedidos e inventario con el mismo `X-Correlation-Id` y respuesta HTTP 503 | ✅ Ejecutado y validado | [P04.md](P04.md) |
| P05 | Enrutamiento de persistencia replicada y contraste no circular | `pg_stat_activity` por nodo y `log_statement=all` contrastado con `X-Data-Source` | ✅ Ejecutado y validado | [P05.md](P05.md) |
| P06 | Detección y exclusión de réplica de base de datos caída (`pg-replica-1`) | Log de health check con las muestras de intervalo y timeout (`probe-timeout`) | ✅ Ejecutado y validado | [P06.md](P06.md) |
| P06b | Modo degradado ante caída simultánea de ambas réplicas de lectura | `pg_stat_activity`: lecturas en el primario y ninguna en réplicas, más `WARN` y métrica | ✅ Ejecutado y validado | [P06b.md](P06b.md) |
| P07 | Consistencia *Read-Your-Writes* con `@CriticalRead` (20 ciclos) | `pg_stat_replication` (`replay_lag`) del instante exacto de cada lectura crítica | ✅ Ejecutado y validado | [P07.md](P07.md) |
| P08 | Recuperación y reincorporación condicionada de réplica de lectura | `pg_stat_replication` con `state = streaming` previo a la primera lectura reincorporada | ✅ Ejecutado y validado | [P08.md](P08.md) |

---

## Estructura de almacenamiento de evidencia cruda

La evidencia primaria sin procesar (payloads JSON, archivos CSV, extractos de logs y sentencias de base de datos) se almacena bajo el directorio versionado:
\`\`\`text
docs/informes/raw/
├── P01-<timestamp>/
│   ├── eureka_apps_raw.json
│   └── gateway_response.txt
├── P02-<timestamp>/
│   ├── results.csv
│   └── <instance>_access.log
├── P03-<timestamp>/
│   ├── requests.csv
│   ├── eureka_server.log
│   └── <instance>.log
├── P04-<timestamp>/
│   ├── order_creation_response.json
│   ├── inventory_unavailable_response.json
│   └── *.log
├── P05-<timestamp>/
│   ├── operations_contrast.csv
│   └── pg-*_statements.log
├── P06-<timestamp>/
│   ├── replica_reads.csv
│   └── *_health.log
├── P06b-<timestamp>/
│   ├── degraded_reads.csv
│   ├── degraded_warn.log
│   └── metrics.json
├── P07-<timestamp>/
│   └── critical_reads_lag.csv
└── P08-<timestamp>/
    ├── reincorporated_reads.csv
    └── *_reincorporation.log
\`\`\`

Todas las salidas crudas han sido procesadas para asegurar la ausencia absoluta de credenciales o contraseñas en texto plano (\`POSTGRES_PASSWORD\`).
