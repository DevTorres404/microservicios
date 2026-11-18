# Delta for postgres-replication-cluster

Capability nueva: no existe spec previa en `openspec/specs/`. Cubre topología, aislamiento de datos y reincorporación condicionada de réplicas.

## ADDED Requirements

### Requirement: Un primario y dos réplicas asíncronas por streaming

El cluster MUST constar de un nodo primario y dos réplicas de lectura de PostgreSQL 16, replicadas por streaming asíncrono de WAL. Las réplicas MUST ser nodos independientes; una tabla duplicada en la misma instancia MUST NOT considerarse réplica.

#### Scenario: Topología verificable

- GIVEN el entorno en ejecución
- WHEN se consulta el estado de replicación en el primario
- THEN aparecen dos réplicas en estado `streaming`
- AND cada réplica corresponde a un contenedor distinto

### Requirement: Réplicas estrictamente de lectura

Las réplicas MUST operar en espera caliente y rechazar escrituras a nivel del motor; el rechazo MUST provenir de PostgreSQL, no de la aplicación.

#### Scenario: Escritura directa a réplica rechazada

- GIVEN una réplica sana
- WHEN se intenta una escritura directa contra ella
- THEN PostgreSQL rechaza la sentencia por ser de sólo lectura
- AND el primario no refleja cambio alguno

### Requirement: Una base por servicio con credenciales propias

Cada servicio MUST operar sobre su propia base con su propio usuario, con permisos limitados a esa base. Un servicio MUST NOT leer ni escribir la base del otro, ni con un `JOIN` o una vista, ni compartir entidades o repositorios.

#### Scenario: Aislamiento de bases

- GIVEN las bases de pedidos e inventario en el mismo cluster
- WHEN se auditan usuarios, permisos y sentencias de cada servicio
- THEN cada usuario sólo alcanza su base
- AND no existe sentencia que cruce ambas bases

### Requirement: Estado de replicación observable

El sistema MUST exponer el estado de cada réplica y su retraso de aplicación para permitir evidencia reproducible. El retraso MUST registrarse en el instante de cada lectura crítica.

#### Scenario: Retraso medido en el momento

- GIVEN replicación activa
- WHEN se ejecuta una lectura crítica
- THEN la evidencia incluye el retraso de cada réplica de ese instante
- AND el valor aparece junto al resultado de la lectura

### Requirement: Reincorporación condicionada de una réplica (P08)

Una réplica reiniciada MUST NOT recibir lecturas hasta cumplir dos condiciones simultáneas: health check verde y replicación en streaming activa. El sistema MUST NOT reincorporarla por el solo hecho de estar conectada o alcanzable.

#### Scenario: Réplica reiniciada vuelve al reparto

- GIVEN una réplica detenida y excluida
- WHEN se reinicia y alcanza streaming activo con health check verde
- THEN vuelve a recibir lecturas dentro de 60 s o menos `[umbral propuesto, ratificable tras primera medición]`
- AND la evidencia muestra las dos condiciones cumplidas antes de la primera lectura

#### Scenario: Réplica alcanzable pero sin replicar

- GIVEN una réplica que responde pero cuyo streaming no está activo
- WHEN se ejecutan lecturas ordinarias
- THEN la réplica permanece excluida
- AND el estado de exclusión queda registrado en log

### Requirement: Sin promoción automática del primario

El sistema MUST NOT promover réplicas ni ejecutar failover automático. La pérdida del primario MUST tratarse como indisponibilidad de escritura, notificada al cliente con error tipado y documentada como límite explícito del alcance.

#### Scenario: Primario detenido

- GIVEN el primario detenido
- WHEN se intenta una escritura
- THEN responde un error tipado de indisponibilidad
- AND ninguna réplica pasa a aceptar escrituras

### Requirement: Estado del cluster apto como evidencia

La evidencia de P06 y P08 MUST incluir estado de contenedores, estado de replicación y lecturas atendidas por réplica; una captura de configuración MUST NOT sustituirla.

#### Scenario: Evidencia completa de recuperación

- GIVEN un ensayo de caída y retorno de réplica
- WHEN se recopila la evidencia
- THEN incluye estado de contenedores, estado de replicación y requests atendidas por nodo
- AND las marcas temporales permiten medir detección y recuperación
