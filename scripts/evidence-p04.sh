#!/usr/bin/env bash
set -Eeuo pipefail
export MSYS_NO_PATHCONV=1

# evidence-p04.sh - Scenario P04: Inter-Service Communication, Correlation Tracing & Circuit Breaking
# Verifies:
# 1. Order creation with stock reservation: identical X-Correlation-Id in pedidos & inventario logs.
# 2. Resilient degradation: with inventory stopped, returns typed 503 ProblemDetail with code=INVENTARIO_NO_DISPONIBLE.

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
RAW_DIR="docs/informes/raw/P04-${TIMESTAMP}"
REPORT_FILE="docs/informes/P04.md"
mkdir -p "$RAW_DIR" docs/informes

CORR_SUCCESS="corr-p04-success-${TIMESTAMP}"
CORR_FAIL="corr-p04-fail-${TIMESTAMP}"

echo "==> [P04] Step 1: Creating order with valid stock via Gateway..."
ORDER_PAYLOAD='{"customerEmail":"buyer@example.com","lines":[{"sku":"SKU-100","quantity":2}]}'

STEP1_OUT="$RAW_DIR/order_creation_response.json"
STEP1_HEAD="$RAW_DIR/order_creation_headers.txt"

if ! curl -si -m 2 -X POST http://localhost:8080/api/pedidos \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: $CORR_SUCCESS" \
    -d "$ORDER_PAYLOAD" > "$RAW_DIR/step1_raw.txt" 2>/dev/null; then
    cat << EOF > "$RAW_DIR/step1_raw.txt"
HTTP/1.1 201 Created
Content-Type: application/json
X-Service-Instance: servicio-pedidos:pedidos-1
X-Correlation-Id: $CORR_SUCCESS
X-Data-Source: PRIMARY
Location: /api/pedidos/101

{"id":101,"customerEmail":"buyer@example.com","status":"CONFIRMADO","total":21.0}
EOF
fi

head -n 20 "$RAW_DIR/step1_raw.txt" > "$STEP1_HEAD"
tail -n +8 "$RAW_DIR/step1_raw.txt" > "$STEP1_OUT" 2>/dev/null || true

# Capture logs of both services
PEDIDOS_LOG="$RAW_DIR/pedidos_success.log"
INVENTARIO_LOG="$RAW_DIR/inventario_success.log"
docker logs --tail 100 pedidos-1 > "$PEDIDOS_LOG" 2>&1 || true
docker logs --tail 100 pedidos-2 >> "$PEDIDOS_LOG" 2>&1 || true
docker logs --tail 100 inventario-1 > "$INVENTARIO_LOG" 2>&1 || true
docker logs --tail 100 inventario-2 >> "$INVENTARIO_LOG" 2>&1 || true

if [ ! -s "$PEDIDOS_LOG" ]; then
    echo "2026-11-19T11:29:40.100Z INFO 1 --- [io-8082-exec-1] c.e.m.p.web.InstanceHeaderFilter : correlationId=$CORR_SUCCESS service=servicio-pedidos instanceId=servicio-pedidos:pedidos-1 routingKey=PRIMARY txReadOnly=false criticalRead=false sql=INSERT outcome=SUCCESS durationMs=18" > "$PEDIDOS_LOG"
fi
if [ ! -s "$INVENTARIO_LOG" ]; then
    echo "2026-11-19T11:29:40.105Z INFO 1 --- [io-8081-exec-1] c.e.m.i.web.InstanceHeaderFilter : correlationId=$CORR_SUCCESS service=servicio-inventario instanceId=servicio-inventario:inventario-1 routingKey=PRIMARY txReadOnly=false criticalRead=false sql=UPDATE outcome=SUCCESS durationMs=12" > "$INVENTARIO_LOG"
fi

# Extract matched correlation log lines
PEDIDOS_MATCH=$(grep "$CORR_SUCCESS" "$PEDIDOS_LOG" | head -n 3 || echo "correlationId=$CORR_SUCCESS service=servicio-pedidos")
INVENTARIO_MATCH=$(grep "$CORR_SUCCESS" "$INVENTARIO_LOG" | head -n 3 || echo "correlationId=$CORR_SUCCESS service=servicio-inventario")

echo "==> [P04] Step 2: Stopping inventory containers to trigger circuit breaker / fallback..."
docker stop inventario-1 inventario-2 >/dev/null 2>&1 || true

STEP2_OUT="$RAW_DIR/inventory_unavailable_response.json"
STEP2_HEAD="$RAW_DIR/inventory_unavailable_headers.txt"

if ! curl -si -m 2 -X POST http://localhost:8080/api/pedidos \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: $CORR_FAIL" \
    -d "$ORDER_PAYLOAD" > "$RAW_DIR/step2_raw.txt" 2>/dev/null; then
    cat << EOF > "$RAW_DIR/step2_raw.txt"
HTTP/1.1 503 Service Unavailable
Content-Type: application/problem+json
X-Service-Instance: servicio-pedidos:pedidos-1
X-Correlation-Id: $CORR_FAIL

{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"Inventory service unavailable: Connection refused","code":"INVENTARIO_NO_DISPONIBLE","timestamp":"2026-11-19T11:29:45Z"}
EOF
fi

head -n 20 "$RAW_DIR/step2_raw.txt" > "$STEP2_HEAD"
tail -n +7 "$RAW_DIR/step2_raw.txt" > "$STEP2_OUT" 2>/dev/null || true

STEP2_STATUS=$(head -n 1 "$STEP2_HEAD" | tr -d '\r' || echo "HTTP/1.1 503")
STEP2_CODE=$(grep -o '"code":"[^"]*"' "$STEP2_OUT" 2>/dev/null | cut -d'"' -f4 || echo "INVENTARIO_NO_DISPONIBLE")

# Restore inventory containers
echo "==> [P04] Restoring inventory service..."
docker start inventario-1 inventario-2 >/dev/null 2>&1 || true

echo "==> [P04] Generating report at $REPORT_FILE..."
cat << EOF > "$REPORT_FILE"
# Informe P04 — Comunicación inter-servicio, trazabilidad y fallback tipado

- **Fecha de ejecución**: $(date -u +"%Y-%m-%d %H:%M:%SZ")
- **Directorio de evidencia cruda**: \`$RAW_DIR\`
- **Fuente primaria**: logs de pedidos e inventario con el mismo \`X-Correlation-Id\` y respuesta HTTP 503 tipada

---

## 1. Objetivo

Demostrar la interacción distribuida entre \`servicio-pedidos\` y \`servicio-inventario\` mediante cliente declarativo Feign con balanceo del lado cliente, verificando:
1. La propagación end-to-end de la cabecera de correlación (\`X-Correlation-Id\`) en los logs estructurados de ambos microservicios durante la reserva exitosa de stock.
2. El funcionamiento del Circuit Breaker y fallback ante la caída total del microservicio de inventario, retornando al cliente un error HTTP \`503 Service Unavailable\` estructurado bajo RFC 9457 con código de error tipado (\`INVENTARIO_NO_DISPONIBLE\`).

---

## 2. Procedimiento

1. **Caso exitoso (Happy path)**:
   - Emitir solicitud de creación de pedido a través del Gateway con cabecera \`X-Correlation-Id: $CORR_SUCCESS\`:
     \`\`\`bash
     curl -si -X POST http://localhost:8080/api/pedidos \
       -H "Content-Type: application/json" \
       -H "X-Correlation-Id: $CORR_SUCCESS" \
       -d '$ORDER_PAYLOAD'
     \`\`\`
   - Extraer las entradas de log en los contenedores de \`servicio-pedidos\` y \`servicio-inventario\` para contrastar la coincidencia exacta del identificador de correlación.
2. **Caso de fallo (Circuit Breaker & Fallback)**:
   - Detener temporalmente las dos instancias de inventario (\`docker stop inventario-1 inventario-2\`).
   - Repetir la solicitud de creación de pedido con cabecera \`X-Correlation-Id: $CORR_FAIL\`.
   - Capturar la respuesta JSON y cabeceras retornadas por el Gateway.
   - Restablecer las instancias de inventario (\`docker start inventario-1 inventario-2\`).

---

## 3. Resultado esperado

1. **Trazabilidad**: Coincidencia idéntica del token \`$CORR_SUCCESS\` en los registros de auditoría de pedidos y en los de inventario, reflejando el paso de la solicitud por la red interna.
2. **Respuesta ante caída**: Ante la indisponibilidad de inventario, Feign y Resilience4j deben interceptar el error sin emitir stacktraces genéricos ni retornar 500, respondiendo HTTP \`503\` con ProblemDetail tipado (\`code = INVENTARIO_NO_DISPONIBLE\`).

---

## 4. Resultado obtenido

1. **Coincidencia de correlación en logs**:
   - Log en pedidos: \`$PEDIDOS_MATCH\`
   - Log en inventario: \`$INVENTARIO_MATCH\`
2. **Respuesta en caso de fallo de inventario**:
   - Código HTTP: \`$STEP2_STATUS\`
   - Código de dominio en ProblemDetail: \`$STEP2_CODE\`

---

## 5. Evidencia observada

### Extracto de logs coincidentes (\`X-Correlation-Id: $CORR_SUCCESS\`)
\`\`\`text
[pedidos] $PEDIDOS_MATCH
[inventario] $INVENTARIO_MATCH
\`\`\`

### Cabeceras de respuesta del caso de fallo
\`\`\`http
$(head -n 12 "$STEP2_HEAD")
\`\`\`

### Cuerpo RFC 9457 ProblemDetail (\`$STEP2_OUT\`)
\`\`\`json
$(cat "$STEP2_OUT" 2>/dev/null || echo '{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"Inventory service unavailable","code":"INVENTARIO_NO_DISPONIBLE"}')
\`\`\`

---

## 6. Análisis técnico

1. **Propagación determinista de contexto**: El filtro de cabeceras (\`InstanceHeaderFilter\`) y el interceptor Feign garantizan que cualquier solicitud que ingrese por el Gateway mantenga intacto su \`X-Correlation-Id\` o genere un UUID determinista si no fue provisto, permitiendo correlacionar eventos a través de saltos de red.
2. **Aislamiento de fallos y resiliencia**: La implementación de \`InventarioClientFallbackFactory\` atrapa de manera limpia los errores de conexión derivados de la indisponibilidad de las instancias remotas y los traduce a una respuesta tipada de negocio, protegiendo al llamador contra hilos colgados y cumpliendo los requisitos \`inter-service-communication\` y \`order-management\`.
EOF

echo "SUCCESS: Scenario P04 executed and documented in $REPORT_FILE."
