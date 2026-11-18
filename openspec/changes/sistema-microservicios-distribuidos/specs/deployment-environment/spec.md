# Delta for deployment-environment

Capability nueva: no existe spec previa en `openspec/specs/`. Cubre reproducibilidad del entorno, red, salud y ausencia de secretos.

## ADDED Requirements

### Requirement: Entorno reproducible desde cero

El sistema MUST levantarse desde un clon limpio con tres pasos documentados: obtener el código, crear el archivo de entorno a partir del ejemplo versionado y ejecutar `docker compose up -d`. Al finalizar, MUST haber 9 contenedores en estado `healthy` sin pasos manuales no documentados.

#### Scenario: Levantado desde cero

- GIVEN un clon limpio y Docker operativo
- WHEN se ejecutan los tres pasos del README
- THEN los 9 contenedores quedan `healthy`
- AND `GET /eureka/apps` lista 4 instancias de servicio

#### Scenario: Sin intervención manual oculta

- GIVEN el mismo procedimiento en una máquina sin estado previo
- WHEN se sigue el README al pie de la letra
- THEN no se requiere editar archivos, cargar datos a mano ni reiniciar componentes

### Requirement: Exposición mínima de puertos

Solo el gateway y el registro de descubrimiento MUST publicarse al host. Servicios de negocio y nodos de base de datos MUST permanecer accesibles únicamente dentro de la red interna, salvo un puerto de diagnóstico documentado.

#### Scenario: Superficie publicada acotada

- GIVEN el entorno en ejecución
- WHEN se inspeccionan los puertos publicados
- THEN sólo el gateway y el registro responden desde el host
- AND ninguna instancia de negocio ni nodo de base de datos es alcanzable directamente

### Requirement: Red y dependencias de arranque

El archivo de Compose MUST declarar la red interna compartida, healthchecks reales por servicio y dependencias de arranque basadas en salud. El orden MUST garantizar que el primario esté listo antes que las réplicas y que el registro esté listo antes que los clientes.

#### Scenario: Arranque ordenado

- GIVEN todos los contenedores detenidos
- WHEN se levanta el entorno
- THEN las réplicas arrancan sólo con el primario saludable
- AND los servicios de negocio arrancan con registro y base disponibles

#### Scenario: Healthcheck representativo

- GIVEN un contenedor de base de datos sin aceptar conexiones
- WHEN se consulta su estado
- THEN el healthcheck lo reporta como no saludable
- AND las dependencias que lo esperan no arrancan antes de tiempo

### Requirement: Ausencia de secretos en el repositorio

El repositorio MUST NOT contener contraseñas, claves ni credenciales reales. Los valores sensibles MUST inyectarse por variables de entorno desde un archivo ignorado, y MUST existir un archivo de ejemplo versionado con valores no productivos.

#### Scenario: Barrido de secretos

- GIVEN el repositorio versionado
- WHEN se busca material sensible en archivos versionados e historial revisado
- THEN no aparece ninguna credencial real
- AND existe un archivo de ejemplo con las variables requeridas

### Requirement: Persistencia y reinicio controlado

Cada nodo de base de datos MUST usar volumen propio y nombrado. La destrucción completa del entorno MUST ser posible con un comando documentado, y el reinicio de un componente individual MUST NOT requerir reconstruir el entorno ni reparar datos.

#### Scenario: Reinicio de un componente

- GIVEN el entorno operativo
- WHEN se detiene y reinicia un contenedor de servicio o de réplica
- THEN vuelve a operar sin reconstruir volúmenes ni ejecutar migraciones manuales

#### Scenario: Reinicio total limpio

- GIVEN un entorno con datos de prueba
- WHEN se ejecuta el comando de destrucción documentado y se vuelve a levantar
- THEN el sistema queda operativo y reproducible desde cero
