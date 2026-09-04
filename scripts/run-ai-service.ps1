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
    [Environment]::SetEnvironmentVariable($line.Substring(0, $separator).Trim(), $line.Substring($separator + 1).Trim(), "Process")
}

$python = Join-Path $projectRoot "ai-service\.venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $python)) {
    throw "AI virtual environment is missing. Create ai-service\.venv and install requirements.txt first."
}
$port = if ($env:AI_SERVICE_PORT) { [int]$env:AI_SERVICE_PORT } else { 8010 }
$healthUrl = "http://127.0.0.1:$port/internal/v1/health"
try {
    $existing = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
    if ($existing.status -eq "UP") { Write-Output "AI_SERVICE_RUN=ALREADY_UP"; exit 0 }
} catch { }

Write-Output "AI_SERVICE_STARTING=$healthUrl"
Push-Location (Join-Path $projectRoot "ai-service")
try {
    & $python -m uvicorn app.main:app --host 127.0.0.1 --port $port
    exit $LASTEXITCODE
} finally { Pop-Location }
