# Delta for technical-documentation

Capability nueva: no existe spec previa en `openspec/specs/`. Define los entregables documentales verificables y su contenido mínimo.

## ADDED Requirements

### Requirement: README operativo autosuficiente

El README MUST explicar descripción del sistema, requisitos, dependencias, configuración de ejemplo, puesta en marcha, verificación, detención y recuperación de cada componente, además de los datos de prueba. Un lector sin contexto MUST poder operar el sistema siguiendo sólo el README.

#### Scenario: Reproducción guiada por el README

- GIVEN un lector sin acceso a configuraciones privadas
- WHEN sigue el README desde cero
- THEN obtiene el entorno operativo
- AND puede detener y recuperar cada componente con los comandos indicados

#### Scenario: Recuperación documentada

- GIVEN una instancia de servicio y una réplica detenidas
- WHEN se consulta el README
- THEN encuentra el procedimiento de recuperación de cada una
- AND el procedimiento coincide con el usado en la evidencia

### Requirement: Diagrama de arquitectura con flujos y puertos

El proyecto MUST incluir un diagrama que muestre componentes, responsabilidades, comunicaciones, puertos publicados, descubrimiento y flujo de persistencia. El diagrama MUST distinguir los tres mecanismos independientes: descubrimiento de procesos, selección de instancia HTTP y selección de nodo de base de datos.

#### Scenario: Diagrama consistente con la implementación

- GIVEN el diagrama publicado
- WHEN se compara con el entorno en ejecución
- THEN componentes, rutas y puertos coinciden
- AND la selección de instancia y la de nodo de base de datos aparecen como decisiones separadas

### Requirement: Colección de solicitudes ejecutable

El proyecto MUST incluir una colección de solicitudes que cubra todas las operaciones REST documentadas, con ejemplos reproducibles y valores de ejemplo válidos. La colección MUST poder ejecutarse sin editar URLs fijas de instancia.

#### Scenario: Cobertura de operaciones

- GIVEN la colección publicada
- WHEN se contrasta con el contrato de cada servicio
- THEN toda operación documentada tiene al menos una solicitud de ejemplo
- AND apunta al punto de entrada único, no a instancias concretas

### Requirement: Decisiones de arquitectura registradas

El proyecto MUST registrar decisiones con contexto y justificación para: política de lectura, modo de replicación, estrategia de consistencia para lecturas críticas, y separación entre descubrimiento, balanceo HTTP y routing de base de datos. Cada decisión MUST declarar su límite o consecuencia negativa.

#### Scenario: Decisión justificada y acotada

- GIVEN el registro de decisiones
- WHEN se revisa la decisión de política de lectura
- THEN declara el criterio de selección y su costo en consistencia
- AND declara qué ocurre cuando no hay réplicas sanas

### Requirement: Video demostrativo accesible

El proyecto MUST incluir un video de 3 a 5 minutos con enlace público o accesible desde el README, realizado sobre el sistema en ejecución. MUST cubrir arquitectura, descubrimiento y balanceo, comunicación entre servicios, persistencia replicada, simulación de fallos y conclusiones técnicas.

#### Scenario: Contenido mínimo demostrado

- GIVEN el enlace publicado en el README
- WHEN se reproduce el video completo
- THEN muestra los seis contenidos exigidos sobre el sistema en vivo
- AND se detiene una instancia y una réplica mostrando la respuesta del sistema

#### Scenario: Video no sustituye la evidencia

- GIVEN el video y los informes de escenarios
- WHEN se evalúa la entrega
- THEN los informes existen con evidencia técnica propia
- AND el video no reemplaza ninguna prueba funcional

### Requirement: Documentación sin secretos y en español neutro

La documentación MUST estar redactada en español neutro y MUST NOT exponer credenciales reales, direcciones privadas ni datos personales. Los ejemplos de configuración MUST usar valores no productivos.

#### Scenario: Documentación publicable

- GIVEN el conjunto de documentos entregados
- WHEN se revisan en busca de material sensible
- THEN no aparece ninguna credencial real
- AND los ejemplos usan valores de demostración
