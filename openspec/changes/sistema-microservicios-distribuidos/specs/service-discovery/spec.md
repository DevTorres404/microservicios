# Delta for service-discovery

Capability nueva: no existe spec previa en `openspec/specs/`. Todo requisito es observable vía `GET /eureka/apps` o vía una request funcional a través del gateway.

## ADDED Requirements

### Requirement: Registro automático de cada instancia con identificador único (P01)

Cada instancia de servicio de negocio MUST registrarse en Eureka al arrancar con un `instance-id` único por proceso. El identificador MUST derivarse del nombre lógico más la identidad del contenedor, y MUST diferenciar dos instancias del mismo servicio. El sistema MUST NOT permitir que dos instancias compartan identificador.

#### Scenario: Cuatro instancias con identificadores distintos

- GIVEN el entorno Compose en ejecución
- WHEN se consulta `GET /eureka/apps`
- THEN `SERVICIO-PEDIDOS` e `SERVICIO-INVENTARIO` aparecen cada uno con 2 instancias `UP`
- AND los 4 `instance-id` son distintos entre sí

#### Scenario: Reinicio sin colisión de identificador

- GIVEN dos instancias del mismo servicio registradas
- WHEN una de ellas se reinicia
- THEN recupera su identificador previo y la gemela conserva el suyo
- AND el conteo de instancias `UP` vuelve a 2 al estabilizarse

### Requirement: Localización exclusiva por nombre lógico

Toda llamada entre procesos MUST resolverse por nombre lógico registrado. Ni el gateway ni los servicios MUST NOT invocar a otro proceso por host o puerto fijado en configuración.

#### Scenario: Resolución por nombre lógico

- GIVEN una ruta `lb://servicio-pedidos` habilitada en el gateway
- WHEN un cliente llama `GET /api/pedidos` al gateway
- THEN responde una instancia `UP` de `SERVICIO-PEDIDOS`
- AND la configuración versionada no contiene hostname ni puerto de destino

### Requirement: Eureka registra procesos, no nodos de base de datos

Eureka MUST registrar únicamente instancias de servicio. Ningún nodo PostgreSQL MUST registrarse como instancia y la selección de nodo de base de datos MUST NOT depender del registro.

#### Scenario: Registro ajeno al cluster de base de datos

- GIVEN el entorno en ejecución
- WHEN se inspecciona `GET /eureka/apps`
- THEN solo aparecen aplicaciones de servicio
- AND ningún `instance-id` corresponde a un nodo PostgreSQL

### Requirement: Renovación, expiración y re-registro (P03)

Cada instancia MUST renovar su lease y MUST expirar del registro cuando deja de renovar. Los plazos MUST ser lo bastante cortos para que la expulsión sea observable: renovación cada 5 s y expiración a los 15 s `[umbral propuesto, ratificable tras primera medición]`. Una instancia reiniciada MUST re-registrarse sin intervención manual.

#### Scenario: Expulsión de instancia detenida

- GIVEN dos instancias registradas
- WHEN una se detiene
- THEN deja de aparecer como `UP` en `GET /eureka/apps` dentro de los 15 s `[umbral propuesto, ratificable tras primera medición]`

#### Scenario: Reincorporación tras reinicio

- GIVEN una instancia detenida y expulsada
- WHEN se vuelve a iniciar
- THEN aparece como `UP` y recibe tráfico del gateway en 30 s o menos `[umbral propuesto, ratificable tras primera medición]`

### Requirement: Visibilidad del estado de descubrimiento

El sistema MUST exponer el registro completo con nombre de aplicación, `instance-id`, estado y dirección de cada instancia, para permitir auditoría y evidencia reproducible.

#### Scenario: Registro auditable

- GIVEN el sistema operativo
- WHEN se consulta `GET /eureka/apps`
- THEN cada instancia informa estado, identificador y dirección
- AND la salida es apta como evidencia de P01 sin edición manual
