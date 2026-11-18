# Delta for experimental-evidence

Capability nueva: no existe spec previa en `openspec/specs/`. Define qué constituye evidencia aceptable de P01–P08 y P06b.

> `[depende de A1]` P04 y sus valores de negocio dependen del dominio Pedidos + Inventario.

## ADDED Requirements

### Requirement: Ejecución documentada de los nueve escenarios

El proyecto MUST ejecutar y documentar P01, P02, P03, P04, P05, P06, P06b, P07 y P08. Cada informe MUST contener objetivo, procedimiento reproducible, resultado esperado, resultado obtenido, evidencia y análisis. Una captura de configuración MUST NOT sustituir una prueba funcional.

#### Scenario: Informe completo por escenario

- GIVEN un escenario ejecutado
- WHEN se revisa su informe
- THEN contiene las seis secciones exigidas
- AND el procedimiento permite repetirlo sin conocimiento tácito

#### Scenario: Escenario faltante

- GIVEN el conjunto de informes entregados
- WHEN se contrasta con la lista exigida
- THEN los nueve escenarios están presentes
- AND ninguno se sustituye por descripción teórica

### Requirement: Fuente primaria de evidencia por escenario

Cada informe MUST citar la fuente primaria de la tabla para su escenario. Una fuente ausente, genérica o sustituida por configuración MUST invalidar el escenario.

| Escenario | Fuente exigida |
| :-- | :-- |
| P01 | `GET /eureka/apps` crudo, con los 4 `instance-id` |
| P02 | archivo por solicitud y log de acceso de cada instancia |
| P03 | `GET /eureka/apps` muestreado y log de lease de Eureka con marcas temporales |
| P04 | logs de pedidos e inventario con el mismo `X-Correlation-Id` |
| P05 | `pg_stat_activity` por nodo y `log_statement=all` o `pg_stat_database`, con la sentencia identificable |
| P06 | log de health check con las muestras de intervalo y timeout |
| P06b | `pg_stat_activity`: lecturas en el primario y ninguna en réplicas, más `WARN` y métrica |
| P07 | `pg_stat_replication` (`replay_lag`) del instante de cada lectura crítica |
| P08 | `pg_stat_replication` con `state = streaming` previo a la primera lectura reincorporada |

#### Scenario: Fuente faltante

- GIVEN un informe entregado
- WHEN se audita contra la tabla
- THEN cada escenario citado nombra su fuente primaria
- AND todo escenario sin fuente se marca como no probado

### Requirement: Evidencia generada por script

La evidencia MUST ser producida por scripts versionados y conservarse como salida cruda con marcas temporales. La transcripción manual de resultados MUST NOT aceptarse como evidencia.

#### Scenario: Evidencia reproducible

- GIVEN el entorno en ejecución
- WHEN se ejecuta el script de un escenario
- THEN se genera un archivo de evidencia con timestamps y salidas crudas
- AND su contenido permite recalcular los agregados del informe

### Requirement: Tiempos de detección y recuperación medidos

Los escenarios de fallo MUST reportar tiempo de detección y tiempo de recuperación medidos, junto con el intervalo de sondeo y el timeout efectivamente configurados. El tiempo de detección MUST descomponerse en intervalo de sondeo, timeout y propagación declarada, sustentado por muestras fechadas; informar sólo el resultado final MUST NOT aceptarse. Afirmar la recuperación sin medirla MUST NOT aceptarse.

#### Scenario: Medición de P03, P06 y P08

- GIVEN un ensayo de caída y retorno
- WHEN se analiza la evidencia
- THEN aparecen el tiempo de detección y el de recuperación observados
- AND se declaran el intervalo de sondeo y el timeout usados

#### Scenario: Detección descompuesta en P03 y P06

- GIVEN una instancia caída y una réplica caída
- WHEN se revisan las muestras de lease de Eureka y de health check
- THEN el tiempo de detección se recalcula desde las marcas de las muestras
- AND coincide con intervalo más timeout más propagación declarados

### Requirement: Contraste no circular de la evidencia

La evidencia de routing MUST contrastar los headers de la aplicación con observación del lado de PostgreSQL —`pg_stat_activity` por nodo y `log_statement=all` o `pg_stat_database`— y con los logs de aplicación. El header por sí solo MUST NOT aceptarse como prueba.

#### Scenario: Contraste en P05 y P06b

- GIVEN un ensayo de escrituras, lecturas y modo degradado
- WHEN se cruzan headers, actividad por nodo y log de sentencias
- THEN el nodo declarado coincide con el que ejecutó la sentencia
- AND las discrepancias, si existen, se documentan

### Requirement: Transitorios cuantificados y explicados

Los errores transitorios observados durante fallos MUST contarse, acotarse en el tiempo y explicarse. Ocultarlos o eliminarlos mediante reintentos ilimitados MUST NOT aceptarse.

#### Scenario: Ventana de error declarada

- GIVEN un escenario con errores durante la ventana de detección
- WHEN se revisa el análisis
- THEN se informa la cantidad y la duración de la ventana
- AND se explica su causa en la caché del balanceador o en el lease del registro

### Requirement: Retraso de replicación registrado en la lectura crítica (P07)

Cada lectura crítica de P07 MUST registrar `pg_stat_replication.replay_lag` de ese instante, con su marca temporal y unidad. La política de consistencia MUST justificarse con ese valor y con el costo asumido al usar el primario. Un `replay_lag` superior a 100 ms `[umbral propuesto, ratificable tras primera medición]` MUST explicarse en el análisis.

#### Scenario: Justificación sustentada

- GIVEN las corridas de P07
- WHEN se revisa el informe
- THEN cada corrida incluye el `replay_lag` medido con su instante
- AND la conclusión explica por qué la lectura va al primario

### Requirement: Streaming verificado antes de reincorporar la réplica (P08)

La reincorporación de una réplica MUST exigir, para esa réplica, `pg_stat_replication` con `state = streaming` muestreado inmediatamente antes de la primera lectura reincorporada, más su `replay_lag`. La alcanzabilidad del nodo MUST NOT aceptarse como condición suficiente. Al reincorporar, el `replay_lag` SHOULD ser de 1 s o menos `[umbral propuesto, ratificable tras primera medición]`.

#### Scenario: Condición previa registrada

- GIVEN una réplica reiniciada y alcanzable
- WHEN se reincorpora al reparto de lecturas
- THEN la evidencia muestra `state = streaming` antes de esa primera lectura
- AND informa el `replay_lag` del muestreo y el tiempo de reincorporación
