# Delta for http-load-balancing

Capability nueva: no existe spec previa en `openspec/specs/`. Todo requisito es observable por HTTP: instancia respondiente por request, conteo de éxitos y errores.

## ADDED Requirements

### Requirement: Dos instancias simultáneas por servicio de negocio

Cada servicio de negocio MUST operar con dos instancias simultáneas, registradas y capaces de atender las mismas operaciones. Una instancia MUST NOT ser un proceso vacío: ambas MUST ejecutar la lógica de negocio y acceder a su base.

#### Scenario: Ambas instancias atienden negocio real

- GIVEN el entorno en ejecución
- WHEN se envía tráfico suficiente para tocar ambas instancias
- THEN las dos responden operaciones con resultado de negocio válido
- AND ambas aparecen `UP` en `GET /eureka/apps`

### Requirement: Distribución observada sobre 100 solicitudes (P02)

El balanceador HTTP MUST distribuir las solicitudes entre las instancias sanas sin intervención del cliente. Sobre 100 solicitudes sucesivas al mismo endpoint: al menos 95 MUST ser exitosas y ninguna instancia SHOULD concentrar más del 75 % `[umbral propuesto, ratificable tras primera medición]`. El cliente MUST NOT elegir la instancia, y la alternancia manual de URLs MUST NOT considerarse balanceo.

#### Scenario: Distribución medida

- GIVEN dos instancias sanas y un endpoint de lectura
- WHEN se ejecutan 100 solicitudes por el gateway registrando la instancia respondiente
- THEN al menos 95 finalizan con éxito `[umbral propuesto, ratificable tras primera medición]`
- AND ambas instancias registran al menos una respuesta

#### Scenario: Ninguna instancia monopoliza el tráfico

- GIVEN las 100 solicitudes del escenario anterior
- WHEN se agrupan por `X-Service-Instance`
- THEN la instancia más cargada atiende como máximo el 75 % `[umbral propuesto, ratificable tras primera medición]`

### Requirement: Evidencia por solicitud

Cada solicitud del ensayo de balanceo MUST quedar registrada con marca temporal, código de respuesta e instancia respondiente, en un archivo generado por script. La evidencia MUST permitir recalcular la distribución sin intervención manual.

#### Scenario: Evidencia reconstruible

- GIVEN un ensayo de 100 solicitudes
- WHEN se revisa el archivo de evidencia
- THEN cada fila contiene timestamp, resultado e instancia
- AND el resumen agregado se deriva del archivo, no de una captura

### Requirement: Continuidad ante caída de una instancia (P03)

Con una instancia detenida, el sistema MUST seguir atendiendo mediante la instancia restante. Tras la detección, al menos el 90 % de las solicitudes MUST ser exitosas `[umbral propuesto, ratificable tras primera medición]`. Los errores transitorios de la ventana de detección MUST contarse y explicarse, no ocultarse.

#### Scenario: Tráfico sostenido con una instancia caída

- GIVEN dos instancias sanas
- WHEN se detiene una y se envían solicitudes repetidas
- THEN las solicitudes posteriores a la detección se resuelven en la instancia viva
- AND la tasa de éxito posterior a la detección es al menos 90 % `[umbral propuesto, ratificable tras primera medición]`

#### Scenario: Ventana transitoria cuantificada

- GIVEN la instancia detenida en el escenario anterior
- WHEN se analizan los errores observados entre la caída y la detección
- THEN el informe declara su cantidad y duración
- AND atribuye la ventana a la caché del balanceador y al lease del registro

### Requirement: Recuperación observable de la instancia (P03)

Una instancia reiniciada MUST volver a recibir tráfico sin reiniciar el gateway ni el registro. El tiempo de reincorporación al balanceo MUST medirse y SHOULD ser de 30 s o menos `[umbral propuesto, ratificable tras primera medición]`.

#### Scenario: Retorno al balanceo

- GIVEN una instancia detenida y expulsada del registro
- WHEN se vuelve a iniciar
- THEN recibe al menos una solicitud dentro de 30 s o menos `[umbral propuesto, ratificable tras primera medición]`
- AND el tiempo medido aparece en la evidencia de P03
