#!/usr/bin/env bash
#
# Registers an ephemeral instance against the Eureka REST API, stops renewing its lease and
# measures how long the registry takes to evict it. Finally re-registers to prove that a returning
# process becomes visible again without restarting the registry.
#
# Usage:
#   bash scripts/eureka-lease-probe.sh
#
# Environment:
#   EUREKA_URL              base registry URL (default http://localhost:8761/eureka)
#   APP_NAME                logical application name used by the probe (default LEASE-PROBE)
#   LEASE_RENEWAL_SECONDS   advertised renewal interval (default 5, matches design §10)
#   LEASE_DURATION_SECONDS  advertised lease duration (default 15, matches design §10)
#   MAX_EVICTION_SECONDS    failure threshold (default 20 = lease 15 s + sweep 3 s + margin)
#
# The script never interpolates external input into a shell command and never prints credentials.

set -euo pipefail

EUREKA_URL="${EUREKA_URL:-http://localhost:8761/eureka}"
APP_NAME="${APP_NAME:-LEASE-PROBE}"
LEASE_RENEWAL_SECONDS="${LEASE_RENEWAL_SECONDS:-5}"
LEASE_DURATION_SECONDS="${LEASE_DURATION_SECONDS:-15}"
MAX_EVICTION_SECONDS="${MAX_EVICTION_SECONDS:-20}"
POLL_INTERVAL_SECONDS=1

timestamp_ms() {
    local now
    now="$(date +%s%3N 2>/dev/null || true)"
    if [[ "${now}" =~ ^[0-9]+$ ]]; then
        printf '%s' "${now}"
    else
        printf '%s000' "$(date +%s)"
    fi
}

http_status() {
    curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1" || printf '000'
}

register_instance() {
    local instance_id="$1"
    curl -s -o /dev/null --max-time 5 \
        -H 'Content-Type: application/json' \
        -H 'Accept: application/json' \
        -X POST "${EUREKA_URL}/apps/${APP_NAME}" \
        -d @- <<JSON
{"instance":{
  "instanceId":"${instance_id}",
  "hostName":"127.0.0.1",
  "app":"${APP_NAME}",
  "ipAddr":"127.0.0.1",
  "status":"UP",
  "vipAddress":"lease-probe",
  "secureVipAddress":"lease-probe",
  "port":{"\$":8080,"@enabled":"true"},
  "securePort":{"\$":8443,"@enabled":"false"},
  "leaseInfo":{"renewalIntervalInSecs":${LEASE_RENEWAL_SECONDS},"durationInSecs":${LEASE_DURATION_SECONDS}},
  "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo\$DefaultDataCenterInfo","name":"MyOwn"}
}}
JSON
}

wait_until_present() {
    local deadline="$1"
    while [[ "$(timestamp_ms)" -lt "${deadline}" ]]; do
        if [[ "$(http_status "${EUREKA_URL}/apps/${APP_NAME}")" == "200" ]]; then
            return 0
        fi
        sleep "${POLL_INTERVAL_SECONDS}"
    done
    return 1
}

deregister() {
    local instance_id="$1"
    curl -s -o /dev/null --max-time 5 -X DELETE \
        "${EUREKA_URL}/apps/${APP_NAME}/${instance_id}" || true
}

health_status="$(http_status "http://127.0.0.1:8761/actuator/health")"
echo "probe.registry=${EUREKA_URL}"
echo "probe.registry_health_http=${health_status}"

if [[ "${health_status}" != "200" ]]; then
    echo "ERROR: registry is not reachable at ${EUREKA_URL}" >&2
    exit 2
fi

INSTANCE_ID="lease-probe-$(date +%s)-$$"
register_instance "${INSTANCE_ID}"

registered_deadline=$(( $(timestamp_ms) + 10000 ))
if ! wait_until_present "${registered_deadline}"; then
    echo "ERROR: the probe instance never appeared in the registry" >&2
    exit 3
fi
echo "probe.instance_id=${INSTANCE_ID}"
echo "probe.registered=ok"

# From this point the probe sends no renewals: the lease must expire on its own.
expiry_start_ms="$(timestamp_ms)"
evicted="false"
while :; do
    status="$(http_status "${EUREKA_URL}/apps/${APP_NAME}")"
    if [[ "${status}" == "404" || "${status}" == "410" ]]; then
        evicted="true"
        break
    fi
    if [[ $(( ($(timestamp_ms) - expiry_start_ms) / 1000 )) -ge ${MAX_EVICTION_SECONDS} ]]; then
        break
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
done
evicted_ms=$(( $(timestamp_ms) - expiry_start_ms ))
evicted_seconds=$(( evicted_ms / 1000 ))

echo "probe.lease_duration_seconds=${LEASE_DURATION_SECONDS}"
echo "probe.eviction_sweep_ms=3000"
echo "probe.evicted=${evicted}"
echo "probe.eviction_measured_seconds=$(( evicted_seconds ))"
echo "probe.eviction_measured_ms=${evicted_ms}"
echo "probe.eviction_threshold_seconds=${MAX_EVICTION_SECONDS}"

if [[ "${evicted}" != "true" ]]; then
    echo "ERROR: the instance was not evicted within ${MAX_EVICTION_SECONDS}s" >&2
    deregister "${INSTANCE_ID}"
    exit 4
fi

# Re-registration: a process that comes back must become selectable again.
reregister_start_ms="$(timestamp_ms)"
register_instance "${INSTANCE_ID}"
reregister_deadline=$(( $(timestamp_ms) + 15000 ))
if ! wait_until_present "${reregister_deadline}"; then
    echo "ERROR: the instance did not reappear after re-registration" >&2
    exit 5
fi
echo "probe.reregistered=ok"
echo "probe.reregistration_measured_ms=$(( $(timestamp_ms) - reregister_start_ms ))"

# Leave the registry as we found it.
deregister "${INSTANCE_ID}"
echo "probe.cleanup=ok"
echo "probe.result=pass"
