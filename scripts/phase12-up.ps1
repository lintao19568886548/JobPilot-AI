param([switch]$SkipBackup, [switch]$Build)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $env:JOBPILOT_RELEASE_VERSION = $script:Phase12ReleaseVersion
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'phase12-doctor.ps1')
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Phase 12 preflight failed'
    if (-not $SkipBackup) {
        # The release backup runs before the new Backend starts, so it must not
        # depend on the operational API being available.
        & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'phase11-backup.ps1') -SkipApiRecord
        Assert-Phase12 ($LASTEXITCODE -eq 0) 'Pre-release backup failed'
    }
    $compose = Get-Phase12ComposeArguments $projectRoot
    $upArgs = @('up','-d')
    if ($Build) { $upArgs += '--build' }
    $upArgs += @('mysql','redis','etcd','minio','milvus','ai-service','backend','frontend')
    & docker compose @compose @upArgs
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Release stack startup failed'
    $gatewayPort = if ($env:JOBPILOT_GATEWAY_PORT) { $env:JOBPILOT_GATEWAY_PORT } else { '8180' }
    $aiPort = if ($env:AI_SERVICE_PORT) { $env:AI_SERVICE_PORT } else { '8010' }
    Wait-Phase12Url "http://127.0.0.1:$gatewayPort/actuator/health" 300
    Wait-Phase12Url "http://127.0.0.1:$aiPort/internal/v1/readiness" 300
    & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'phase12-doctor.ps1') -Runtime
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Runtime hardening checks failed'
    [pscustomobject]@{status='PASS';releaseVersion=$script:Phase12ReleaseVersion;gateway="http://127.0.0.1:$gatewayPort";backup=(-not $SkipBackup)} | ConvertTo-Json -Compress
} finally { Pop-Location }
