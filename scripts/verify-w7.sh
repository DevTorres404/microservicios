#!/usr/bin/env bash
set -Eeuo pipefail
export MSYS_NO_PATHCONV=1

echo "=== Verifying W7-1: PostgreSQL nodes health ==="
docker compose ps --format '{{.Name}} {{.Health}}' | grep pg-

echo "=== Verifying W7-2: PostgreSQL replication smoke test ==="
bash scripts/smoke-postgres-replication.sh

echo "=== Verifying W7-3: Replica in recovery ==="
RECOVERY=$(docker exec pg-replica-1 psql -U replicator -tAc 'select pg_is_in_recovery()')
echo "pg-replica-1 pg_is_in_recovery: $RECOVERY"
test "$RECOVERY" = "t"

echo "=== Verifying W7-4: Published ports ==="
PUBLISHED=$(docker compose config --format json | grep -c published)
echo "Published ports count: $PUBLISHED (expected 2)"
test "$PUBLISHED" -eq 2

echo "=== Verifying W7-5: Registered Eureka UP instances ==="
UP_COUNT=$(curl -sf -H 'Accept: application/json' http://localhost:8761/eureka/apps | grep -o 'status.:.UP' | wc -l)
echo "Eureka UP instances count: $UP_COUNT (expected 4)"
test "$UP_COUNT" -eq 4

echo "=== Verifying W7-6: Memory limits ==="
MEM_LIMITS=$(docker compose config | grep -cE 'mem_limit|limits:')
echo "Memory limits declared: $MEM_LIMITS (expected >= 9)"
test "$MEM_LIMITS" -ge 9

echo "=== Verifying W7-7: Evidence config ==="
LOG_ALL=$(docker compose -f docker-compose.yml -f docker-compose.evidence.yml config | grep -c 'log_statement=all')
echo "Evidence log_statement=all count: $LOG_ALL (expected 3)"
test "$LOG_ALL" -ge 3

echo "=== Verifying W7-8: All 9 containers healthy ==="
HEALTHY_COUNT=$(docker compose ps --format '{{.Health}}' | grep -c healthy)
echo "Healthy containers count: $HEALTHY_COUNT (expected 9)"
test "$HEALTHY_COUNT" -eq 9

echo "ALL W7 TASKS (W7-1 through W7-8) VERIFIED SUCCESSFULLY!"
