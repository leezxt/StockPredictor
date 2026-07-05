param(
    [string]$TaskName = "StockPredictor-AutoRestart",
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$scriptPath = Join-Path $PSScriptRoot "dev-start.ps1"
if (-not (Test-Path $scriptPath)) {
    throw "找不到 dev-start.ps1：$scriptPath"
}

$powershellExe = (Get-Command powershell.exe -ErrorAction Stop).Source
$taskArgs = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptPath`" -Port $Port -SkipTests"

$action = New-ScheduledTaskAction -Execute $powershellExe -Argument $taskArgs -WorkingDirectory $PSScriptRoot
$triggerLogon = New-ScheduledTaskTrigger -AtLogOn
$triggerStartup = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 0) `
    -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -MultipleInstances IgnoreNew

$userId = "$env:USERDOMAIN\$env:USERNAME"
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Highest

$task = New-ScheduledTask -Action $action -Trigger @($triggerLogon, $triggerStartup) -Settings $settings -Principal $principal
Register-ScheduledTask -TaskName $TaskName -InputObject $task -Force | Out-Null

Write-Host "已註冊工作排程器：$TaskName"
Write-Host "觸發條件：登入、開機"
Write-Host "失敗重啟：每 1 分鐘重啟，最多 999 次"
Write-Host "啟動命令：$powershellExe $taskArgs"
