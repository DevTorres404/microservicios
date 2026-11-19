#!/usr/bin/env bash
set -euo pipefail

echo "================================================================================"
echo "    Verify From Scratch — End-to-End System Validation"
echo "================================================================================"

# 1. Verify or create .env file
if [ ! -f .env ]; then
    echo "==> Creating .env from .env.example..."
    cp .env.example .env
else
    echo "==> .env file already present."
fi

# 2. Ensure compose cluster is up
echo "==> Ensuring Docker Compose environment is started (docker compose up -d)..."
docker compose up -d

# 3. Wait for all 9 containers to report healthy
echo "==> Polling health status for all 9 containers..."
HEALTHY_COUNT=0
for i in $(seq 1 45); do
    HEALTHY_COUNT=$(docker compose ps --format '{{.Health}}' 2>/dev/null | (grep -c "healthy" || true) | tr -d ' ')
    if [ "$HEALTHY_COUNT" -eq 9 ]; then
        echo "    All 9 containers reported healthy ($HEALTHY_COUNT/9)."
        break
    fi
    echo "    Waiting for containers to become healthy... ($HEALTHY_COUNT/9 healthy, attempt $i/45)"
    sleep 2
done

if [ "$HEALTHY_COUNT" -ne 9 ]; then
    echo "ERROR: Expected 9 healthy containers, found $HEALTHY_COUNT."
    docker compose ps
    exit 1
fi

# 4. Wait for Eureka to register all 4 service instances in UP state
echo "==> Polling Eureka registry (http://localhost:8761/eureka/apps)..."
UP_COUNT=0
for j in $(seq 1 30); do
    APPS_JSON=$(curl -sf -H "Accept: application/json" http://localhost:8761/eureka/apps 2>/dev/null || echo "")
    if [ -n "$APPS_JSON" ]; then
        UP_COUNT=$(echo "$APPS_JSON" | (grep -o '"status"[[:space:]]*:[[:space:]]*"UP"' || true) | wc -l | tr -d ' ')
        if [ "$UP_COUNT" -ge 4 ]; then
            echo "    Eureka registry has all 4 instances registered as UP."
            break
        fi
    fi
    echo "    Waiting for 4 UP instances in Eureka... ($UP_COUNT detected, attempt $j/30)"
    sleep 2
done

if [ "$UP_COUNT" -lt 4 ]; then
    echo "ERROR: Eureka registered only $UP_COUNT instances as UP (expected 4)."
    exit 1
fi

# 5. Execute functional business request through API Gateway and validate headers
echo "==> Sending business request to API Gateway (http://localhost:8080/api/inventario)..."
TMP_HEADERS=$(mktemp)
HTTP_CODE=$(curl -si -m 5 -o /dev/null -w "%{http_code}" -D "$TMP_HEADERS" http://localhost:8080/api/inventario || echo "000")

echo "    HTTP response status code: $HTTP_CODE"
if [ "$HTTP_CODE" -ne 200 ]; then
    echo "ERROR: Business request failed with status $HTTP_CODE."
    cat "$TMP_HEADERS"
    rm -f "$TMP_HEADERS"
    exit 1
fi

# Assert X-Service-Instance header
INSTANCE_HEADER=$( (grep -i "X-Service-Instance" "$TMP_HEADERS" || true) | tr -d '\r\n')
echo "    Header captured: $INSTANCE_HEADER"
if [ -z "$INSTANCE_HEADER" ]; then
    echo "ERROR: Missing required header 'X-Service-Instance'."
    rm -f "$TMP_HEADERS"
    exit 1
fi

# Assert X-Data-Source header
DATASOURCE_HEADER=$( (grep -i "X-Data-Source" "$TMP_HEADERS" || true) | tr -d '\r\n')
echo "    Header captured: $DATASOURCE_HEADER"
if [ -z "$DATASOURCE_HEADER" ]; then
    echo "ERROR: Missing required header 'X-Data-Source'."
    rm -f "$TMP_HEADERS"
    exit 1
fi

rm -f "$TMP_HEADERS"

echo "================================================================================"
echo "SUCCESS: Entire system verified from scratch!"
echo "  - 9/9 containers healthy"
echo "  - 4/4 microservice instances UP in Eureka"
echo "  - Functional request answered with 200 OK"
echo "  - Both headers verified (X-Service-Instance and X-Data-Source)"
echo "================================================================================"
exit 0
