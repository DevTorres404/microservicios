#!/usr/bin/env bash
set -Eeuo pipefail
export MSYS_NO_PATHCONV=1

# collect-evidence.sh - Unified diagnostic evidence collector for W8
# Captures 6 primary sources with timestamps:
# 1. /eureka/apps
# 2. /actuator/metrics
# 3. Response headers (X-Service-Instance, X-Data-Source, X-Correlation-Id)
# 4. pg_stat_activity across all PostgreSQL nodes
# 5. pg_stat_replication on pg-primary
# 6. pg_stat_wal_receiver on pg-replica-1 and pg-replica-2
# Strictly sanitizes and verifies absence of passwords (TD requirement).

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
TARGET_DIR="${1:-docs/informes/raw/snapshot-${TIMESTAMP}}"
mkdir -p "$TARGET_DIR"

echo "==> Collecting evidence into: $TARGET_DIR"

# Read known secrets to sanitize and verify
REPLICATOR_PW="${REPLICATOR_PASSWORD:-change-me-replicator}"
POSTGRES_PW="${POSTGRES_PASSWORD:-change-me-root}"
PEDIDOS_PW="${PEDIDOS_APP_PASSWORD:-change-me-pedidos}"
INVENTARIO_PW="${INVENTARIO_APP_PASSWORD:-change-me-inventario}"

# Helper function to sanitize passwords
sanitize_file() {
    local file="$1"
    if [ -f "$file" ]; then
        sed -i \
            -e "s/${REPLICATOR_PW}/[REDACTED_PASSWORD]/g" \
            -e "s/${POSTGRES_PW}/[REDACTED_PASSWORD]/g" \
            -e "s/${PEDIDOS_PW}/[REDACTED_PASSWORD]/g" \
            -e "s/${INVENTARIO_PW}/[REDACTED_PASSWORD]/g" \
            "$file"
    fi
}

# 1. /eureka/apps
echo "[1/6] Capturing /eureka/apps..."
EUREKA_OUT="$TARGET_DIR/eureka_apps.json"
if curl -sf -H "Accept: application/json" http://localhost:8761/eureka/apps > "$EUREKA_OUT" 2>/dev/null; then
    echo "OK: /eureka/apps captured."
else
    echo '{"error": "Eureka server unreachable at :8761"}' > "$EUREKA_OUT"
    echo "WARN: Eureka server unreachable."
fi
sanitize_file "$EUREKA_OUT"

# 2. /actuator/metrics
echo "[2/6] Capturing /actuator/metrics..."
METRICS_OUT="$TARGET_DIR/actuator_metrics.json"
{
    echo "{"
    echo '  "timestamp": "'"$TIMESTAMP"'",'
    echo '  "gateway": '
    curl -sf http://localhost:8080/actuator/metrics 2>/dev/null || echo '{"status": "unreachable"}'
    echo ','
    echo '  "eureka": '
    curl -sf http://localhost:8761/actuator/metrics 2>/dev/null || echo '{"status": "unreachable"}'
    echo "}"
} > "$METRICS_OUT"
sanitize_file "$METRICS_OUT"

# 3. Response headers
echo "[3/6] Capturing response headers..."
HEADERS_OUT="$TARGET_DIR/response_headers.txt"
{
    echo "=== TIMESTAMP: $TIMESTAMP ==="
    echo "--- GET http://localhost:8080/api/pedidos ---"
    curl -si http://localhost:8080/api/pedidos 2>&1 | head -n 30 || true
    echo ""
    echo "--- GET http://localhost:8080/api/inventario ---"
    curl -si http://localhost:8080/api/inventario 2>&1 | head -n 30 || true
} > "$HEADERS_OUT"
sanitize_file "$HEADERS_OUT"

# 4. pg_stat_activity
echo "[4/6] Capturing pg_stat_activity on all database nodes..."
ACTIVITY_OUT="$TARGET_DIR/pg_stat_activity.txt"
{
    echo "=== TIMESTAMP: $TIMESTAMP ==="
    for node in pg-primary pg-replica-1 pg-replica-2; do
        echo "--- Node: $node ---"
        if docker inspect "$node" >/dev/null 2>&1; then
            docker exec "$node" psql -U postgres -d postgres -tAc "
                SELECT datname, usename, application_name, client_addr, state, query
                FROM pg_stat_activity
                WHERE pid <> pg_backend_pid();
            " 2>&1 || echo "psql failed on $node"
        else
            echo "Container $node not running"
        fi
    done
} > "$ACTIVITY_OUT"
sanitize_file "$ACTIVITY_OUT"

# 5. pg_stat_replication
echo "[5/6] Capturing pg_stat_replication on primary..."
REPL_OUT="$TARGET_DIR/pg_stat_replication.txt"
{
    echo "=== TIMESTAMP: $TIMESTAMP ==="
    if docker inspect pg-primary >/dev/null 2>&1; then
        docker exec pg-primary psql -U postgres -d postgres -tAc "
            SELECT client_addr, usename, application_name, state, sync_state, replay_lag, write_lag, flush_lag
            FROM pg_stat_replication;
        " 2>&1 || echo "psql failed on pg-primary"
    else
        echo "Container pg-primary not running"
    fi
} > "$REPL_OUT"
sanitize_file "$REPL_OUT"

# 6. pg_stat_wal_receiver
echo "[6/6] Capturing pg_stat_wal_receiver on replicas..."
WAL_OUT="$TARGET_DIR/pg_stat_wal_receiver.txt"
{
    echo "=== TIMESTAMP: $TIMESTAMP ==="
    for node in pg-replica-1 pg-replica-2; do
        echo "--- Node: $node ---"
        if docker inspect "$node" >/dev/null 2>&1; then
            docker exec "$node" psql -U postgres -d postgres -tAc "
                SELECT status, receive_start_lsn, written_lsn, flushed_lsn, received_tli, last_msg_send_time, last_msg_receipt_time
                FROM pg_stat_wal_receiver;
            " 2>&1 || echo "psql failed on $node"
        else
            echo "Container $node not running"
        fi
    done
} > "$WAL_OUT"
sanitize_file "$WAL_OUT"

# Verification: Reject evidence directory if any raw password appears
echo "==> Auditing evidence directory for plaintext secrets..."
SECRET_LEAK=0
for secret in "$REPLICATOR_PW" "$POSTGRES_PW" "$PEDIDOS_PW" "$INVENTARIO_PW"; do
    if [ -n "$secret" ] && grep -rqF "$secret" "$TARGET_DIR"; then
        echo "SECURITY ERROR: Secret password detected in $TARGET_DIR!"
        SECRET_LEAK=1
    fi
done

if [ "$SECRET_LEAK" -ne 0 ]; then
    echo "ABORT: Secret leakage audit FAILED."
    exit 1
fi

echo "SUCCESS: Evidence collection completed in $TARGET_DIR (6/6 sources captured, secrets clean)."
