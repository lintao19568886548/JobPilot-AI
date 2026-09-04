param()

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $projectRoot ".env"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing $envFile. Copy .env.example to .env and configure it first."
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }
    $separator = $line.IndexOf("=")
    if ($separator -lt 1) {
        return
    }
    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
}

$serverPort = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8088 }
$healthUrl = "http://127.0.0.1:$serverPort/actuator/health"
try {
    $existingHealth = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
    if ($existingHealth.status -eq "UP") {
        Write-Output "BACKEND_RUN=ALREADY_UP"
        Write-Output "HEALTH=$healthUrl"
        exit 0
    }
} catch {
    # The backend is not currently reachable; continue with startup.
}

$java = Get-Command java -ErrorAction Stop
$jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot "backend\target") `
    -Filter "jobpilot-backend-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "Backend JAR not found. Run .\backend\mvnw.cmd package first."
}

Write-Output "BACKEND_STARTING=$healthUrl"
Push-Location $projectRoot
try {
    & $java.Source -jar $jar.FullName
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
