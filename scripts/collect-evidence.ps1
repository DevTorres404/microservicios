# collect-evidence.ps1 - PowerShell evidence collector for W8
param(
    [string]$TargetDir = ""
)

$ErrorActionPreference = "Continue"

$Timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmssZ")
if ([string]::IsNullOrWhiteSpace($TargetDir)) {
    $TargetDir = "docs/informes/raw/snapshot-$Timestamp"
}

if (-not (Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
}

Write-Host "==> Collecting evidence into: $TargetDir"

$ReplicatorPw = if ($env:REPLICATOR_PASSWORD) { $env:REPLICATOR_PASSWORD } else { "change-me-replicator" }
$PostgresPw = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "change-me-root" }
$PedidosPw = if ($env:PEDIDOS_APP_PASSWORD) { $env:PEDIDOS_APP_PASSWORD } else { "change-me-pedidos" }
$InventarioPw = if ($env:INVENTARIO_APP_PASSWORD) { $env:INVENTARIO_APP_PASSWORD } else { "change-me-inventario" }

function Sanitize-File([string]$FilePath) {
    if (Test-Path $FilePath) {
        $content = Get-Content -Raw -Path $FilePath
        $content = $content.Replace($ReplicatorPw, "[REDACTED_PASSWORD]")
        $content = $content.Replace($PostgresPw, "[REDACTED_PASSWORD]")
        $content = $content.Replace($PedidosPw, "[REDACTED_PASSWORD]")
        $content = $content.Replace($InventarioPw, "[REDACTED_PASSWORD]")
        Set-Content -Path $FilePath -Value $content
    }
}

# 1. /eureka/apps
Write-Host "[1/6] Capturing /eureka/apps..."
$EurekaOut = Join-Path $TargetDir "eureka_apps.json"
try {
    $res = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps" -Headers @{ "Accept" = "application/json" } -TimeoutSec 5
    $res | ConvertTo-Json -Depth 10 | Set-Content $EurekaOut
} catch {
    '{"error": "Eureka unreachable"}' | Set-Content $EurekaOut
}
Sanitize-File $EurekaOut

# 2. /actuator/metrics
Write-Host "[2/6] Capturing /actuator/metrics..."
$MetricsOut = Join-Path $TargetDir "actuator_metrics.json"
try {
    $gwMetrics = Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics" -TimeoutSec 5
    $gwJson = $gwMetrics | ConvertTo-Json -Depth 5
} catch {
    $gwJson = '{"status": "unreachable"}'
}
"{ `"`"timestamp`"`": `"`"$Timestamp`"`", `"`"gateway`"`": $gwJson }" | Set-Content $MetricsOut
Sanitize-File $MetricsOut

# 3. Response headers
Write-Host "[3/6] Capturing response headers..."
$HeadersOut = Join-Path $TargetDir "response_headers.txt"
$headerLines = @("=== TIMESTAMP: $Timestamp ===")
try {
    $res = curl.exe -si http://localhost:8080/api/pedidos
    $headerLines += "--- GET http://localhost:8080/api/pedidos ---"
    $headerLines += $res
} catch {
    $headerLines += "Failed to connect to gateway"
}
$headerLines | Set-Content $HeadersOut
Sanitize-File $HeadersOut

# 4. pg_stat_activity
Write-Host "[4/6] Capturing pg_stat_activity..."
$ActivityOut = Join-Path $TargetDir "pg_stat_activity.txt"
$actLines = @("=== TIMESTAMP: $Timestamp ===")
foreach ($node in @("pg-primary", "pg-replica-1", "pg-replica-2")) {
    $actLines += "--- Node: $node ---"
    try {
        $out = docker exec $node psql -U postgres -d postgres -tAc "SELECT datname, usename, application_name, client_addr, state, query FROM pg_stat_activity WHERE pid <> pg_backend_pid();" 2>&1
        $actLines += $out
    } catch {
        $actLines += "psql failed on $node"
    }
}
$actLines | Set-Content $ActivityOut
Sanitize-File $ActivityOut

# 5. pg_stat_replication
Write-Host "[5/6] Capturing pg_stat_replication..."
$ReplOut = Join-Path $TargetDir "pg_stat_replication.txt"
$replLines = @("=== TIMESTAMP: $Timestamp ===")
try {
    $out = docker exec pg-primary psql -U postgres -d postgres -tAc "SELECT client_addr, usename, application_name, state, sync_state, replay_lag, write_lag, flush_lag FROM pg_stat_replication;" 2>&1
    $replLines += $out
} catch {
    $replLines += "psql failed on pg-primary"
}
$replLines | Set-Content $ReplOut
Sanitize-File $ReplOut

# 6. pg_stat_wal_receiver
Write-Host "[6/6] Capturing pg_stat_wal_receiver..."
$WalOut = Join-Path $TargetDir "pg_stat_wal_receiver.txt"
$walLines = @("=== TIMESTAMP: $Timestamp ===")
foreach ($node in @("pg-replica-1", "pg-replica-2")) {
    $walLines += "--- Node: $node ---"
    try {
        $out = docker exec $node psql -U postgres -d postgres -tAc "SELECT status, receive_start_lsn, written_lsn, flushed_lsn, received_tli, last_msg_send_time, last_msg_receipt_time FROM pg_stat_wal_receiver;" 2>&1
        $walLines += $out
    } catch {
        $walLines += "psql failed on $node"
    }
}
$walLines | Set-Content $WalOut
Sanitize-File $WalOut

# Audit for secret leakage
Write-Host "==> Auditing evidence directory for plaintext secrets..."
$leak = $false
foreach ($sec in @($ReplicatorPw, $PostgresPw, $PedidosPw, $InventarioPw)) {
    if (-not [string]::IsNullOrEmpty($sec)) {
        $match = Get-ChildItem -Path $TargetDir -Recurse | Select-String -Pattern $sec -SimpleMatch
        if ($match) {
            Write-Error "SECURITY ERROR: Secret detected in $($match.Path)!"
            $leak = $true
        }
    }
}

if ($leak) {
    throw "ABORT: Secret leakage audit FAILED."
}

Write-Host "SUCCESS: Evidence collection completed in $TargetDir (6/6 sources captured, secrets clean)."
