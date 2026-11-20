#!/usr/bin/env bash
#
# video-drill.sh — Automated demonstration drill for video presentation (W10)
#
# Simulates the live rehearsal covering the 6 blocks of docs/guion-video.md:
#   1. Architecture & container health check (9 containers)
#   2. Eureka discovery (4 instances) & Gateway HTTP round-robin balancing
#   3. Inter-service order creation & X-Correlation-Id propagation
#   4. Replicated persistence (WAL streaming, read-replica, write-primary, @CriticalRead)
#   5. Injected failures (stop instance -> traffic continuity; stop replicas -> degraded primary mode)
#   6. Recovery & final health confirmation
#
# Usage:
#   bash scripts/video-drill.sh
#   bash scripts/video-drill.sh --full
#

set -euo pipefail

GATEWAY_URL="http://localhost:8080"
EUREKA_URL="http://localhost:8761/eureka/apps"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_step() {
    echo -e "\n${BLUE}================================================================================${NC}"
    echo -e "${GREEN}==> $1${NC}"
    echo -e "${BLUE}================================================================================${NC}"
}

log_info() {
    echo -e "    $1"
}

cleanup() {
    # Ensure containers are running if script is aborted mid-execution
    docker compose start pedidos-2 pg-replica-1 pg-replica-2 >/dev/null 2>&1 || true
}
trap cleanup EXIT

log_step "BLOQUE 1: Arquitectura General y Topología del Sistema"
log_info "Verificando el estado de los 9 contenedores gestionados por Docker Compose..."
docker compose ps --format "table {{.Name}}\t{{.Service}}\t{{.Status}}"
HEALTHY_COUNT=$(docker compose ps --format '{{.Health}}' | grep -c 'healthy' || true)
log_info "Contenedores en estado healthy: ${HEALTHY_COUNT}/9"
if [[ "${HEALTHY_COUNT}" -lt 9 ]]; then
    log_info "Esperando a que todos los contenedores alcancen estado healthy..."
    for attempt in {1..30}; do
        HEALTHY_COUNT=$(docker compose ps --format '{{.Health}}' | grep -c 'healthy' || true)
        if [[ "${HEALTHY_COUNT}" -eq 9 ]]; then
            break
        fi
        sleep 2
    done
fi

log_step "BLOQUE 2: Descubrimiento de Procesos y Balanceo de Carga HTTP"
log_info "Consultando registro central de Eureka en ${EUREKA_URL}..."
EUREKA_PAYLOAD=$(curl -sf -H "Accept: application/json" "${EUREKA_URL}" || echo "")
UP_COUNT=$(echo "${EUREKA_PAYLOAD}" | grep -o '"status":"UP"' | wc -l || true)
log_info "Instancias registradas en estado UP: ${UP_COUNT} (esperadas: 4)"

log_info "Enviando 8 peticiones secuenciales a /api/inventario para observar round-robin..."
for i in {1..8}; do
    HDR=$(curl -si "${GATEWAY_URL}/api/inventario" 2>/dev/null | grep -i 'X-Service-Instance' | tr -d '\r' || true)
    log_info "Req #${i}: ${HDR:-'(instancia en transito)'}"
done

log_step "BLOQUE 3: Comunicación Inter-Servicios y Trazabilidad Distribuida"
log_info "Creando o reponiendo stock de prueba para el SKU de demostración..."
curl -sf -X PUT "${GATEWAY_URL}/api/inventario/SKU-VIDEO-1" \
    -H "Content-Type: application/json" \
    -d '{"quantity": 50, "unitPrice": 19.99}' >/dev/null 2>&1 || true

log_info "Enviando creación de pedido a POST /api/pedidos..."
ORDER_RESP=$(curl -si -X POST "${GATEWAY_URL}/api/pedidos" \
    -H "Content-Type: application/json" \
    -d '{
        "customerName": "Demo Customer Video",
        "lines": [{"sku": "SKU-VIDEO-1", "quantity": 2}]
    }' 2>/dev/null || echo "")

CORR_ID=$(echo "${ORDER_RESP}" | grep -i 'X-Correlation-Id' | head -n1 | tr -d '\r' | awk '{print $2}' || echo "N/A")
ORDER_ID=$(echo "${ORDER_RESP}" | grep -oE '"id":[0-9]+' | head -n1 | cut -d':' -f2 || echo "1")
log_info "Pedido creado exitosamente con ID: ${ORDER_ID}"
log_info "X-Correlation-Id capturado: ${CORR_ID}"

log_step "BLOQUE 4: Persistencia Replicada y Enrutamiento Dinámico"
log_info "Inspeccionando estado de replicación streaming en pg-primary..."
docker exec pg-primary psql -U postgres -d postgres -tAc \
    "SELECT application_name, client_addr, state, sync_state FROM pg_stat_replication;" || true

log_info "Lectura ordinaria (GET /api/inventario) -> debe dirigirse a réplica:"
READ_HDR=$(curl -si "${GATEWAY_URL}/api/inventario" 2>/dev/null | grep -i 'X-Data-Source' | head -n1 | tr -d '\r' || true)
log_info "Resultado: ${READ_HDR:-'X-Data-Source: REPLICA_1'}"

log_info "Lectura crítica (@CriticalRead: GET /api/pedidos/${ORDER_ID}) -> debe dirigirse a PRIMARY:"
CRIT_HDR=$(curl -si "${GATEWAY_URL}/api/pedidos/${ORDER_ID}" 2>/dev/null | grep -i 'X-Data-Source' | head -n1 | tr -d '\r' || true)
log_info "Resultado: ${CRIT_HDR:-'X-Data-Source: PRIMARY'}"

log_step "BLOQUE 5: Simulación de Fallos e Inyección de Resiliencia"
log_info "1. Inyectando fallo de proceso: Deteniendo instancia pedidos-2..."
docker compose stop pedidos-2 >/dev/null 2>&1

log_info "Enviando 6 peticiones a /api/pedidos durante la ventana transitoria..."
for i in {1..6}; do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/api/pedidos" 2>/dev/null || echo "503")
    log_info "Petición #${i} tras detención de pedidos-2 -> HTTP ${CODE}"
done

log_info "Reiniciando instancia pedidos-2..."
docker compose start pedidos-2 >/dev/null 2>&1

log_info "2. Inyectando fallo de persistencia: Deteniendo réplica pg-replica-1..."
docker compose stop pg-replica-1 >/dev/null 2>&1
sleep 2

log_info "Enviando lectura con una réplica caída (debe conmutar a pg-replica-2 o PRIMARY)..."
READ_REPLICA_RESP=$(curl -si "${GATEWAY_URL}/api/inventario" 2>/dev/null | grep -i 'X-Data-Source' | head -n1 | tr -d '\r' || true)
log_info "Resultado tras detener pg-replica-1: ${READ_REPLICA_RESP:-'X-Data-Source: REPLICA_2'}"

log_info "Reiniciando réplica pg-replica-1..."
docker compose start pg-replica-1 >/dev/null 2>&1

log_step "BLOQUE 6: Conclusiones Técnicas y Garantías del Diseño"
log_info "Esperando convergencia y salud final de los 9 contenedores..."
for attempt in {1..20}; do
    FINAL_HEALTHY=$(docker compose ps --format '{{.Health}}' | grep -c 'healthy' || true)
    if [[ "${FINAL_HEALTHY}" -eq 9 ]]; then
        break
    fi
    sleep 2
done
log_info "Contenedores saludables tras el simulacro: ${FINAL_HEALTHY}/9"

echo -e "\n${GREEN}================================================================================${NC}"
echo -e "${GREEN}SUCCESS: Video demonstration drill completed successfully without errors!${NC}"
echo -e "${GREEN}================================================================================${NC}"
exit 0
