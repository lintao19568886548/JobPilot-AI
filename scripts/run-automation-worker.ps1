param()

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $projectRoot ".env"
if (-not (Test-Path -LiteralPath $envFile)) { throw "Missing $envFile" }

foreach ($entry in Get-Content -LiteralPath $envFile) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith("#")) { continue }
    $separator = $line.IndexOf("=")
    if ($separator -lt 1) { continue }
    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

if ($env:AUTOMATION_WORKER_ENABLED -ne "true") {
    throw "Automation Worker is disabled. Set AUTOMATION_WORKER_ENABLED=true only for an approved local Assist test."
}
if (-not $env:AUTOMATION_WORKER_TOKEN -or $env:AUTOMATION_WORKER_TOKEN.Length -lt 24) {
    throw "AUTOMATION_WORKER_TOKEN must contain at least 24 characters."
}

$port = if ($env:AUTOMATION_WORKER_PORT) { [int]$env:AUTOMATION_WORKER_PORT } else { 8020 }
$healthUrl = "http://127.0.0.1:$port/internal/v1/health"
try {
    $existing = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
    if ($existing.status -eq "UP") { Write-Output "AUTOMATION_WORKER_RUN=ALREADY_UP"; exit 0 }
} catch { }

Push-Location (Join-Path $projectRoot "automation-worker")
try {
    if (-not (Test-Path -LiteralPath "node_modules")) { npm install }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "Automation Worker build failed" }
    Write-Output "AUTOMATION_WORKER_STARTING=$healthUrl"
    npm start
    exit $LASTEXITCODE
} finally { Pop-Location }
