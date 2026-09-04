$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $env:JOBPILOT_RELEASE_VERSION = $script:Phase12ReleaseVersion
    $compose = Get-Phase12ComposeArguments $projectRoot
    & docker compose @compose stop frontend backend ai-service
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Unable to stop release application services'
    [pscustomobject]@{status='PASS';stopped=@('frontend','backend','ai-service');dataServicesPreserved=$true;volumesPreserved=$true} | ConvertTo-Json -Compress
} finally { Pop-Location }
