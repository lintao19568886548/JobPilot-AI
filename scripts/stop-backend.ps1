param([Parameter(Mandatory = $true)][int]$ProcessId)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
if (-not $process) {
    Write-Output "BACKEND_STOP=ALREADY_STOPPED"
    exit 0
}
$expected = [regex]::Escape((Join-Path $projectRoot "backend\target\jobpilot-backend-0.1.0-SNAPSHOT.jar"))
if ($process.Name -ne "java.exe" -or $process.CommandLine -notmatch $expected) {
    throw "PID $ProcessId is not this project's backend process."
}
Stop-Process -Id $ProcessId
Wait-Process -Id $ProcessId -Timeout 20 -ErrorAction SilentlyContinue
Write-Output "BACKEND_STOP=PASS"
