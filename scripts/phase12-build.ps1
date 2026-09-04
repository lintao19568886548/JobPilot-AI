param([switch]$SkipRegression)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $env:JOBPILOT_RELEASE_VERSION = $script:Phase12ReleaseVersion
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'phase12-doctor.ps1')
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Phase 12 preflight failed'

    if (-not $SkipRegression) {
        Push-Location (Join-Path $projectRoot 'backend')
        try { & .\mvnw.cmd clean test; Assert-Phase12 ($LASTEXITCODE -eq 0) 'Backend tests failed'; & .\mvnw.cmd package; Assert-Phase12 ($LASTEXITCODE -eq 0) 'Backend package failed' } finally { Pop-Location }
        Push-Location (Join-Path $projectRoot 'ai-service')
        try { & .\.venv\Scripts\python.exe -m pytest -q; Assert-Phase12 ($LASTEXITCODE -eq 0) 'AI tests failed'; & .\.venv\Scripts\python.exe -m ruff check .; Assert-Phase12 ($LASTEXITCODE -eq 0) 'AI Ruff failed' } finally { Pop-Location }
        Push-Location (Join-Path $projectRoot 'frontend')
        try { & npm.cmd run build; Assert-Phase12 ($LASTEXITCODE -eq 0) 'Frontend build failed' } finally { Pop-Location }
        Push-Location (Join-Path $projectRoot 'extension')
        try { & npm.cmd test; Assert-Phase12 ($LASTEXITCODE -eq 0) 'Extension tests failed'; & npm.cmd run build; Assert-Phase12 ($LASTEXITCODE -eq 0) 'Extension build failed' } finally { Pop-Location }
        Push-Location (Join-Path $projectRoot 'automation-worker')
        try { & npm.cmd test; Assert-Phase12 ($LASTEXITCODE -eq 0) 'Automation Worker tests failed' } finally { Pop-Location }
    }

    $compose = Get-Phase12ComposeArguments $projectRoot
    & docker compose @compose build backend frontend ai-service
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Release image build failed'
    foreach ($image in @("jobpilot/backend:$($script:Phase12ReleaseVersion)","jobpilot/frontend:$($script:Phase12ReleaseVersion)","jobpilot/ai-service:$($script:Phase12ReleaseVersion)")) {
        $id = (& docker image inspect --format '{{.Id}}' $image).Trim()
        Assert-Phase12 ($id -match '^sha256:[0-9a-f]{64}$') "Image is missing: $image"
    }
    [pscustomobject]@{status='PASS';releaseVersion=$script:Phase12ReleaseVersion;images=3;regression=(-not $SkipRegression)} | ConvertTo-Json -Compress
} finally { Pop-Location }
