#!/usr/bin/env bash
set -Eeuo pipefail
export MSYS_NO_PATHCONV=1

# Smoke test for PostgreSQL streaming replication (W7-2 / ADR-05)
# Verifies:
# 1. pg-primary, pg-replica-1 and pg-replica-2 become healthy
# 2. standby.signal exists on replicas
# 3. primary_conninfo exists on replicas
# 4. Zero credentials (passwords) written to postgresql.auto.conf (passfile used)
# 5. pg_is_in_recovery() returns 't' on replicas
# 6. pg_stat_wal_receiver.status = 'streaming' on replicas
# 7. Replication data propagation from primary to both replicas

echo "==> [1/6] Starting PostgreSQL cluster (pg-primary, pg-replica-1, pg-replica-2)..."
docker compose up -d pg-primary pg-replica-1 pg-replica-2

echo "==> [2/6] Waiting for cluster nodes to become healthy..."
TIMEOUT=60
ELAPSED=0
while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
    P_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pg-primary 2>/dev/null || echo "starting")
    R1_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pg-replica-1 2>/dev/null || echo "starting")
    R2_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' pg-replica-2 2>/dev/null || echo "starting")

    if [ "$P_HEALTH" = "healthy" ] && [ "$R1_HEALTH" = "healthy" ] && [ "$R2_HEALTH" = "healthy" ]; then
        echo "All 3 PostgreSQL nodes are healthy (in ${ELAPSED}s)."
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
done

if [ "$ELAPSED" -ge "$TIMEOUT" ]; then
    echo "ERROR: PostgreSQL nodes failed to become healthy within ${TIMEOUT}s."
    docker compose ps
    exit 1
fi

echo "==> [3/6] Verifying standby.signal and primary_conninfo..."
for node in pg-replica-1 pg-replica-2; do
    if ! docker exec "$node" test -f /var/lib/postgresql/data/standby.signal; then
        echo "ERROR: standby.signal missing on $node"
        exit 1
    fi
    if ! docker exec "$node" grep -q "primary_conninfo" /var/lib/postgresql/data/postgresql.auto.conf; then
        echo "ERROR: primary_conninfo missing in postgresql.auto.conf on $node"
        exit 1
    fi
    echo "OK: $node has standby.signal and primary_conninfo."
done

echo "==> [4/6] Verifying no passwords in postgresql.auto.conf (ADR-05 passfile requirement)..."
for node in pg-replica-1 pg-replica-2; do
    if docker exec "$node" grep -iq "password=" /var/lib/postgresql/data/postgresql.auto.conf; then
        echo "ERROR: Found plaintext password in postgresql.auto.conf on $node!"
        exit 1
    fi
    if ! docker exec "$node" test -f /var/lib/postgresql/.pgpass; then
        echo "ERROR: /var/lib/postgresql/.pgpass missing on $node"
        exit 1
    fi
    echo "OK: $node uses passfile without committed or stored plaintext passwords in auto.conf."
done

echo "==> [5/6] Verifying recovery state and WAL streaming receiver status..."
for node in pg-replica-1 pg-replica-2; do
    IN_RECOVERY=$(docker exec "$node" psql -U replicator -d postgres -tAc 'SELECT pg_is_in_recovery();')
    if [ "$IN_RECOVERY" != "t" ]; then
        echo "ERROR: $node is not in recovery mode (got '$IN_RECOVERY')"
        exit 1
    fi

    # Wait up to 15s for receiver to reach streaming status if not already
    STREAM_STATUS=""
    for i in $(seq 1 15); do
        STREAM_STATUS=$(docker exec "$node" psql -U replicator -d postgres -tAc 'SELECT status FROM pg_stat_wal_receiver;' || echo "")
        if [ "$STREAM_STATUS" = "streaming" ]; then
            break
        fi
        sleep 1
    done

    if [ "$STREAM_STATUS" != "streaming" ]; then
        echo "ERROR: $node wal receiver status is '$STREAM_STATUS' (expected 'streaming')"
        exit 1
    fi
    echo "OK: $node is in recovery and actively streaming WAL from primary."
done

echo "==> [6/6] Verifying data propagation from primary to replicas..."
docker exec pg-primary psql -U pedidos_app -d pedidos_db -c "
    CREATE TABLE IF NOT EXISTS smoke_replication_test (id serial primary key, msg text);
    INSERT INTO smoke_replication_test (msg) VALUES ('replication-verification-token');
" > /dev/null

sleep 1

for node in pg-replica-1 pg-replica-2; do
    COUNT=$(docker exec "$node" psql -U pedidos_app -d pedidos_db -tAc "SELECT count(*) FROM smoke_replication_test WHERE msg='replication-verification-token';")
    if [ "$COUNT" -lt 1 ]; then
        echo "ERROR: Data inserted in primary was not replicated to $node!"
        exit 1
    fi
    echo "OK: $node confirmed row replicated from primary."
done

docker exec pg-primary psql -U pedidos_app -d pedidos_db -c "DROP TABLE smoke_replication_test;" > /dev/null

echo "SUCCESS: PostgreSQL streaming replication smoke test PASSED."
exit 0
