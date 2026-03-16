$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendStartScript = Join-Path $PSScriptRoot "start-backend-jdk17.cmd"
$backendLog = Join-Path $projectRoot "backend-runtime.log"

$backendListener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($backendListener) {
    Stop-Process -Id $backendListener.OwningProcess -Force
    Start-Sleep -Seconds 2
}

Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like "*inference_server.py*" } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

if (Test-Path $backendLog) {
    Remove-Item $backendLog -Force
}

Start-Process -FilePath $backendStartScript -WorkingDirectory $projectRoot

Write-Output "Backend restart triggered with JDK17 environment."
