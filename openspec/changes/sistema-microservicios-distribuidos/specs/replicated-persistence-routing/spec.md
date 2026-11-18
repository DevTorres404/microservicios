# Delta for replicated-persistence-routing

Capability nueva. Mecanismo independiente de selección de nodo de base de datos; MUST NOT confundirse con el descubrimiento de procesos ni con el balanceo HTTP.

## ADDED Requirements

### Requirement: Escrituras siempre en el primario (P05)

Toda escritura MUST resolverse en el primario; ninguna réplica MUST recibir escrituras de la aplicación.

#### Scenario: Escritura en el primario

- GIVEN el cluster sano
- WHEN se crea o modifica un registro
- THEN `X-Data-Source` informa `PRIMARY`
- AND la sentencia figura en el log del primario, no en las réplicas

### Requirement: Lecturas ordinarias con política propia y determinista (P05)

Las lecturas ordinarias MUST resolverse en réplicas sanas por round-robin determinista sobre el conjunto seleccionable, sin usar el primario mientras exista una réplica sana. La política MUST documentarse como tabla operación → nodo.

#### Scenario: Reparto alternado y sin primario

- GIVEN dos réplicas sanas
- WHEN se ejecutan lecturas ordinarias sucesivas
- THEN las respuestas alternan entre ambas réplicas (round-robin)
- AND `X-Data-Source` nunca informa `PRIMARY`

### Requirement: Lectura crítica inmediata a la escritura (P07)

Una marca explícita de lectura crítica (`@CriticalRead`) MUST resolverse en el primario y devolver el dato recién confirmado cuando se ejecuta inmediatamente después de la escritura.

#### Scenario: Read-your-writes garantizado

- GIVEN una escritura confirmada
- WHEN se ejecuta de inmediato la lectura crítica del registro
- THEN la respuesta contiene el dato recién escrito
- AND `X-Data-Source` informa `PRIMARY`

#### Scenario: Repetición sostenida

- GIVEN 20 corridas de escritura más lectura crítica `[umbral propuesto, ratificable tras primera medición]`
- WHEN se evalúan todas
- THEN el 100 % devuelve el dato recién creado
- AND cada corrida registra el `replay_lag` del instante

### Requirement: La decisión de nodo corresponde al tipo de operación

La selección MUST resolverse antes de emitir la sentencia; un mecanismo que adquiera la conexión antes de conocer el modo de la transacción MUST NOT ser la base del routing.

#### Scenario: Coherencia entre declaración y nodo real

- GIVEN una transacción de sólo lectura y una de escritura
- WHEN se auditan sus sentencias por nodo
- THEN la lectura corre en una réplica y la escritura en el primario, coincidiendo con lo declarado

### Requirement: Exclusión de réplica por health check con timeout explícito (P06)

Cada réplica MUST someterse a sondeo periódico con timeout explícito y estado `UP`/`DOWN`. La exclusión MUST depender del sondeo, MUST NOT depender de la excepción del driver ni del primer error de consulta, y MUST ocurrir en 10 s o menos `[umbral propuesto, ratificable tras primera medición]`.

#### Scenario: Réplica caída excluida del reparto

- GIVEN dos réplicas sanas
- WHEN se detiene una
- THEN queda excluida dentro de los 10 s `[umbral propuesto, ratificable tras primera medición]`
- AND el 100 % de las lecturas posteriores se resuelven en la réplica restante

#### Scenario: Exclusión atribuida al sondeo

- GIVEN la réplica detenida
- WHEN se revisan los logs de salud
- THEN el fallo de sondeo aparece con su timeout como causa
- AND no se atribuye a un error de consulta

### Requirement: Modo degradado hacia el primario (P06b)

Sin réplicas sanas, las lecturas ordinarias MUST resolverse en el primario, con `WARN` y métrica por cada entrada al modo degradado. El cliente MUST NOT recibir error.

#### Scenario: Ambas réplicas detenidas

- GIVEN las dos réplicas detenidas
- WHEN se ejecutan lecturas ordinarias
- THEN el 100 % responde desde el primario
- AND hay `WARN` en log y métrica incrementada

### Requirement: Evidencia no circular del nodo (P05, P06b)

La prueba de routing MUST NOT basarse sólo en `X-Data-Source` ni en ningún otro header emitido por la aplicación. Cada corrida MUST contrastarse con observación del lado de PostgreSQL: `pg_stat_activity` por nodo y `log_statement=all` o `pg_stat_database`, con la sentencia identificable, además de los logs de aplicación. Sin ese cotejo, la corrida MUST considerarse no probada.

#### Scenario: Contraste obligatorio en P05

- GIVEN un ensayo de escrituras y lecturas
- WHEN se compara el header con la actividad y el log de cada nodo
- THEN coinciden en todas las filas del ensayo
- AND la sentencia no figura en el log de las réplicas

#### Scenario: Contraste en modo degradado (P06b)

- GIVEN ambas réplicas detenidas
- WHEN se ejecutan lecturas ordinarias
- THEN `pg_stat_activity` del primario registra esas lecturas
- AND las réplicas no registran lectura alguna, y el `WARN` de degradado coincide con el log del primario
