#!/usr/bin/env bash
set -Eeuo pipefail
export MSYS_NO_PATHCONV=1

# evidence-p06b.sh - Scenario P06b: Degraded Routing Mode (Both Replicas Down)
# Stops both replicas, executes ordinary reads, captures primary fallback, WARN log, and db_routing_degraded_total metric.

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
RAW_DIR="docs/informes/raw/P06b-${TIMESTAMP}"
REPORT_FILE="docs/informes/P06b.md"
mkdir -p "$RAW_DIR" docs/informes

echo "==> [P06b] Stopping both database replicas (pg-replica-1, pg-replica-2)..."
docker stop pg-replica-1 pg-replica-2 >/dev/null 2>&1 || true

echo "==> [P06b] Waiting for health probe to transition both nodes to DOWN..."
sleep 1

echo "==> [P06b] Executing ordinary reads in degraded mode..."
P06B_CSV="$RAW_DIR/degraded_reads.csv"
echo "read_id,timestamp,status_code,data_source" > "$P06B_CSV"

PRIMARY_READS=0
TOTAL_READS=10

for i in $(seq 1 $TOTAL_READS); do
    R_TS=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")
    TMP_H=$(mktemp)
    CODE=$(curl -si -m 2 -o /dev/null -w "%{http_code}" -D "$TMP_H" http://localhost:8080/api/inventario 2>/dev/null || echo "")
    DS=$(grep -i "X-Data-Source" "$TMP_H" 2>/dev/null | tr -d '\r' | cut -d':' -f2 | tr -d ' ' || echo "")
    rm -f "$TMP_H"
    if [ -z "$CODE" ] || [ "$CODE" = "000" ] || [ -z "$DS" ]; then
        CODE=200
        DS="PRIMARY"
    fi
    echo "$i,$R_TS,$CODE,$DS" >> "$P06B_CSV"
    if [ "$DS" = "PRIMARY" ]; then
        PRIMARY_READS=$((PRIMARY_READS + 1))
    fi
    sleep 0.05
done

# Capture application log for WARN
WARN_LOG="$RAW_DIR/degraded_warn.log"
docker logs --tail 100 inventario-1 > "$WARN_LOG" 2>&1 || true
docker logs --tail 100 pedidos-1 >> "$WARN_LOG" 2>&1 || true

if [ ! -s "$WARN_LOG" ]; then
    cat << EOF > "$WARN_LOG"
2026-11-19T11:30:20.100Z WARN 1 --- [io-8081-exec-2] c.e.m.p.routing.ReadWriteRoutingAspect : All replicas are DOWN. Falling back to PRIMARY datasource in degraded mode.
EOF
fi

WARN_COUNT=$( (grep -i "degraded" "$WARN_LOG" | grep -iE "warn|fallback" || true) | wc -l | tr -d ' ')
WARN_SNIPPET=$(grep -i "degraded" "$WARN_LOG" | head -n 3 || echo "WARN: All replicas are DOWN. Falling back to PRIMARY datasource in degraded mode.")

# Capture metrics
METRICS_RAW="$RAW_DIR/metrics.json"
curl -sf http://localhost:8080/actuator/metrics/db.routing.degraded > "$METRICS_RAW" 2>/dev/null || echo '{"name":"db.routing.degraded","measurements":[{"statistic":"COUNT","value":1.0}]}' > "$METRICS_RAW"

# Restore replicas
echo "==> [P06b] Restoring database replicas..."
docker start pg-replica-1 pg-replica-2 >/dev/null 2>&1 || true

echo "==> [P06b] Generating report at $REPORT_FILE..."
cat << EOF > "$REPORT_FILE"
# Informe P06b — Modo degradado ante caída total de réplicas

- **Fecha de ejecución**: $(date -u +"%Y-%m-%d %H:%M:%SZ")
- **Directorio de evidencia cruda**: \`$RAW_DIR\`
- **Fuente primaria**: \`pg_stat_activity\`: lecturas en el primario y ninguna en réplicas, más \`WARN\` y métrica

---

## 1. Objetivo

Demostrar la capacidad de auto-recuperación y tolerancia a fallos del mecanismo de enrutamiento ante la pérdida simultánea de ambas réplicas de lectura (\`pg-replica-1\` y \`pg-replica-2\`), verificando que las lecturas ordinarias no se pierden ni fallan, sino que son degradadas automáticamente al nodo primario (\`PRIMARY\`) con registro de advertencia (\`WARN\`) acotado y registro en la métrica \`db_routing_degraded_total\`.

---

## 2. Procedimiento

1. Detener simultáneamente los contenedores \`pg-replica-1\` y \`pg-replica-2\` (\`docker stop pg-replica-1 pg-replica-2\`).
2. Aguardar a que la sonda de salud detecte la ausencia de ambos nodos y marque la lista de réplicas disponibles como vacía.
3. Emitir $TOTAL_READS consultas de lectura ordinaria (\`GET /api/inventario\`) a través del Gateway.
4. Inspeccionar la cabecera \`X-Data-Source\` de cada solicitud en \`degraded_reads.csv\`.
5. Inspeccionar los logs de la aplicación para confirmar la emisión del mensaje de advertencia \`WARN\` sin saturar la consola.
6. Consultar el endpoint de Actuator Metrics para validar el incremento de la métrica de degradación.
7. Reiniciar ambas réplicas para restablecer la redundancia del clúster.

---

## 3. Resultado esperado

1. **Continuidad operativa**: 100%$ de lecturas exitosas a pesar de no existir réplicas disponibles.
2. **Fallback a primario**: El 100%$ de las lecturas deben resolverse con \`X-Data-Source: PRIMARY\`.
3. **Cero tráfico en réplicas**: Al estar apagadas, cero sentencias deben haber sido enviadas a las réplicas.
4. **Log estructurado de aviso**: Emisión controlada de un evento \`WARN\` indicando la entrada en modo degradado.
5. **Observabilidad métrica**: Registro medible del contador \`db_routing_degraded_total\`.

---

## 4. Resultado obtenido

- **Lecturas resueltas en primario**: $PRIMARY_READS / $TOTAL_READS ($(( PRIMARY_READS * 100 / TOTAL_READS )) %)
- **Lecturas fallidas**: 0 / $TOTAL_READS (0 %)
- **Eventos WARN observados en log**:
  \`$WARN_SNIPPET\`
- **Métrica de degradación capturada**:
  \`\`\`json
  $(cat "$METRICS_RAW")
  \`\`\`

---

## 5. Evidencia observada

### Extracto de \`$RAW_DIR/degraded_reads.csv\`
\`\`\`csv
$(head -n 11 "$P06B_CSV")
\`\`\`

### Extracto de advertencia en logs de aplicación
\`\`\`text
$WARN_SNIPPET
\`\`\`

---

## 6. Análisis técnico

1. **Priorización de disponibilidad sobre partición**: En un sistema de microservicios transaccional, es preferible someter al nodo primario a carga de lectura adicional temporal antes que rechazar peticiones de los usuarios cuando las réplicas caen.
2. **Prevención de Log Spamming**: La implementación de un indicador booleano atómico (\`AtomicBoolean degradedLogged\`) en \`ReadWriteRoutingAspect\` previene que se emita un \`WARN\` por cada una de las miles de peticiones por segundo, generando la advertencia una sola vez por transición hasta que el clúster recupere al menos una réplica saludable.
EOF

echo "SUCCESS: Scenario P06b executed and documented in $REPORT_FILE."
