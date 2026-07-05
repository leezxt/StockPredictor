param(
    [int]$Port = 19090,
    [switch]$SkipTests = $true
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty OwningProcess

if ($existing) {
    Write-Host "StockPredictor is already running on port $Port (PID: $existing)."
    Write-Host "Open: http://localhost:$Port/index.html"
    exit 0
}

$env:SERVER_PORT = "$Port"

$args = @("spring-boot:run")
if ($SkipTests) {
    $args = @("-DskipTests") + $args
}

Write-Host "Starting Spring Boot on port $Port ..."
Write-Host "Open: http://localhost:$Port/index.html"

mvn @args
