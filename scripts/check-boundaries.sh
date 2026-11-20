#!/usr/bin/env bash
#
# Validates architectural boundaries between microservices:
# 1. Zero fixed host or port in application*.yml of business services.
# 2. Zero cross-database or cross-table references in code and migrations.
# 3. Zero cross-service domain imports.
#
# Usage:
#   bash scripts/check-boundaries.sh
#

set -euo pipefail

TOTAL_VIOLATIONS=0

echo "==> [1/3] Checking for fixed host/port/URL in service configurations..."
CONFIG_VIOLATIONS=0
for yml in servicio-pedidos/src/main/resources/application*.yml servicio-inventario/src/main/resources/application*.yml; do
    if [[ -f "$yml" ]]; then
        # Check for hardcoded URLs (http/https/jdbc) that do not use ${...} placeholders
        if grep -nE '(https?://[a-zA-Z0-9.-]+|jdbc:[a-zA-Z0-9:]+)' "$yml" | grep -v '\${' | grep -v '^[[:space:]]*#' || false; then
            echo "FAIL: Fixed URL found in $yml"
            CONFIG_VIOLATIONS=$((CONFIG_VIOLATIONS + 1))
        fi
        # Check for raw host/port keys without ${...}
        if grep -nE '^[[:space:]]*(host|port):[[:space:]]+[a-zA-Z0-9]' "$yml" | grep -v '\${' | grep -v '^[[:space:]]*#' || false; then
            echo "FAIL: Fixed host or port found in $yml"
            CONFIG_VIOLATIONS=$((CONFIG_VIOLATIONS + 1))
        fi
    fi
done

if [[ "$CONFIG_VIOLATIONS" -eq 0 ]]; then
    echo "OK: All service connection values use environment placeholders."
else
    TOTAL_VIOLATIONS=$((TOTAL_VIOLATIONS + CONFIG_VIOLATIONS))
fi

echo "==> [2/3] Checking for cross-database and cross-table references..."
DB_VIOLATIONS=0
# servicio-pedidos must never reference inventario_db or inventory tables
if grep -rnEI 'inventario_db|from[[:space:]]+stock|update[[:space:]]+stock|from[[:space:]]+reservations|from[[:space:]]+reservation_lines' servicio-pedidos/src/main/ 2>/dev/null; then
    echo "FAIL: servicio-pedidos references inventario_db or inventory tables"
    DB_VIOLATIONS=$((DB_VIOLATIONS + 1))
fi

# servicio-inventario must never reference pedidos_db or orders tables
if grep -rnEI 'pedidos_db|from[[:space:]]+orders|update[[:space:]]+orders|from[[:space:]]+order_lines' servicio-inventario/src/main/ 2>/dev/null; then
    echo "FAIL: servicio-inventario references pedidos_db or orders tables"
    DB_VIOLATIONS=$((DB_VIOLATIONS + 1))
fi

if [[ "$DB_VIOLATIONS" -eq 0 ]]; then
    echo "OK: Zero cross-database or cross-table references found."
else
    TOTAL_VIOLATIONS=$((TOTAL_VIOLATIONS + DB_VIOLATIONS))
fi

echo "==> [3/3] Checking for cross-service domain imports..."
IMPORT_VIOLATIONS=0
if grep -rnE 'import[[:space:]]+com\.example\.microservicios\.inventario' servicio-pedidos/src/main/java 2>/dev/null; then
    echo "FAIL: servicio-pedidos imports classes from servicio-inventario"
    IMPORT_VIOLATIONS=$((IMPORT_VIOLATIONS + 1))
fi

if grep -rnE 'import[[:space:]]+com\.example\.microservicios\.pedidos' servicio-inventario/src/main/java 2>/dev/null; then
    echo "FAIL: servicio-inventario imports classes from servicio-pedidos"
    IMPORT_VIOLATIONS=$((IMPORT_VIOLATIONS + 1))
fi

if [[ "$IMPORT_VIOLATIONS" -eq 0 ]]; then
    echo "OK: Zero cross-service imports found."
else
    TOTAL_VIOLATIONS=$((TOTAL_VIOLATIONS + IMPORT_VIOLATIONS))
fi

echo ""
if [[ "$TOTAL_VIOLATIONS" -eq 0 ]]; then
    echo "SUCCESS: All architectural boundary checks passed (0 violations)."
    exit 0
else
    echo "FAILURE: $TOTAL_VIOLATIONS architectural boundary violation(s) detected."
    exit 1
fi
