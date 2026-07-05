param(
    [int]$Port = 19090
)

$ErrorActionPreference = "Stop"

$pids = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique

if (-not $pids) {
    Write-Host "No listening process found on port $Port."
    exit 0
}

foreach ($processId in $pids) {
    Stop-Process -Id $processId -Force
    Write-Host "Stopped PID $processId on port $Port."
}
