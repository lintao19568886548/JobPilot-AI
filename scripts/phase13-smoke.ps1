param([switch]$VerifyPersistence)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
$statePath = Join-Path $projectRoot 'scripts\.phase13-smoke-state.json'
$script:httpCount = 0

function Invoke-Phase13Web([string]$Method, [string]$Url, [hashtable]$Headers, $Body = $null) {
    $script:httpCount++
    $parameters = @{ Method = $Method; Uri = $Url; Headers = $Headers; TimeoutSec = 30; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    return Invoke-WebRequest @parameters
}

function Read-Phase13Text($Response) {
    if ($Response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Response.Content) }
    return [string]$Response.Content
}

Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $gatewayPort = if ($env:JOBPILOT_GATEWAY_PORT) { $env:JOBPILOT_GATEWAY_PORT } else { '8180' }
    $gateway = "http://127.0.0.1:$gatewayPort"

    $spa = Invoke-Phase13Web GET "$gateway/setup" @{}
    Assert-Phase12 ($spa.StatusCode -eq 200 -and (Read-Phase13Text $spa) -match 'JobPilot AI') 'Setup SPA route is unavailable'

    $login = Invoke-Phase13Web POST "$gateway/api/v1/auth/login" @{} @{ login = 'phase10_smoke'; password = (Get-Phase11Password $projectRoot) }
    $loginBody = Read-Phase13Text $login | ConvertFrom-Json
    Assert-Phase12 (-not [string]::IsNullOrWhiteSpace($loginBody.data.accessToken)) 'Login did not return an access token'
    $traceId = "phase13-$([Guid]::NewGuid().ToString('N'))"
    $headers = @{ Authorization = "Bearer $($loginBody.data.accessToken)"; 'X-Trace-Id' = $traceId }

    $overviewResponse = Invoke-Phase13Web GET "$gateway/api/v1/onboarding/overview" $headers
    $overviewEnvelope = Read-Phase13Text $overviewResponse | ConvertFrom-Json
    $overview = $overviewEnvelope.data
    Assert-Phase12 ($overviewEnvelope.code -eq 0) 'Onboarding overview envelope failed'
    Assert-Phase12 ($overviewEnvelope.traceId -eq $traceId -and $overviewResponse.Headers['X-Trace-Id'] -eq $traceId) 'Onboarding trace propagation failed'
    Assert-Phase12 ($overview.score -ge 0 -and $overview.score -le 100) 'Readiness score is outside 0..100'
    Assert-Phase12 (@($overview.steps).Count -eq 9) 'Onboarding must return nine deterministic steps'
    Assert-Phase12 ((@($overview.steps | Measure-Object -Property weight -Sum).Sum) -eq 100) 'Readiness weights must total 100'
    Assert-Phase12 (@($overview.steps | Select-Object -ExpandProperty key -Unique).Count -eq 9) 'Readiness step keys must be unique'
    Assert-Phase12 (@('READY', 'NEEDS_WORK') -contains $overview.status) 'Unknown onboarding status'
    Assert-Phase12 ($overview.readyForMatching -eq ($overview.status -eq 'READY')) 'Ready flag and status disagree'

    $qualityResponse = Invoke-Phase13Web GET "$gateway/api/v1/onboarding/data-quality" $headers
    $qualityEnvelope = Read-Phase13Text $qualityResponse | ConvertFrom-Json
    $quality = $qualityEnvelope.data
    Assert-Phase12 ($qualityEnvelope.code -eq 0) 'Data quality envelope failed'
    Assert-Phase12 (($quality.summary.blockers + $quality.summary.warnings + $quality.summary.info) -eq $quality.summary.total) 'Quality summary is inconsistent'
    Assert-Phase12 (@($quality.issues).Count -eq $quality.summary.total) 'Quality issue count is inconsistent'
    Assert-Phase12 (@($quality.issues | Select-Object -ExpandProperty code -Unique).Count -eq @($quality.issues).Count) 'Quality issue codes must be unique'
    $severity = @{ BLOCKER = 0; WARNING = 1; INFO = 2 }
    $lastOrder = -1
    foreach ($issue in @($quality.issues)) {
        Assert-Phase12 ($severity.ContainsKey($issue.severity)) "Unknown quality severity: $($issue.severity)"
        $currentOrder = $severity[$issue.severity]
        Assert-Phase12 ($currentOrder -ge $lastOrder) 'Quality issues are not sorted by severity'
        Assert-Phase12 ($issue.actionPath -in @('/candidate', '/resumes')) 'Quality action path is outside the approved local UI'
        $lastOrder = $currentOrder
    }
    Assert-Phase12 ($overview.qualitySummary.total -eq $quality.summary.total) 'Overview and quality summary disagree'

    $dashboardResponse = Invoke-Phase13Web GET "$gateway/api/analytics/dashboard" $headers
    $dashboard = (Read-Phase13Text $dashboardResponse | ConvertFrom-Json).data
    Assert-Phase12 ($null -ne $dashboard.foundation -and $null -ne $dashboard.matching) 'Dashboard integration data is unavailable'

    $operationsResponse = Invoke-Phase13Web GET "$gateway/api/v1/operations/overview" $headers
    $operations = (Read-Phase13Text $operationsResponse | ConvertFrom-Json).data
    Assert-Phase12 ($operations.safety.externalMessagesSent -eq 0 -and $operations.safety.externalSubmissions -eq 0 -and $operations.safety.externalMutations -eq 0 -and $operations.safety.automaticOfferDecisions -eq 0 -and $operations.safety.destructiveDuplicateDeletes -eq 0) 'A forbidden external action was detected'

    $fingerprint = [ordered]@{
        score = $overview.score
        status = $overview.status
        completedSteps = $overview.completedSteps
        issueCodes = @($quality.issues | Select-Object -ExpandProperty code)
    }
    if ($VerifyPersistence) {
        Assert-Phase12 (Test-Path -LiteralPath $statePath -PathType Leaf) 'Phase 13 persistence state is missing; run full smoke first'
        $previous = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        Assert-Phase12 ($previous.score -eq $fingerprint.score) 'Readiness score changed across application restart'
        Assert-Phase12 ($previous.status -eq $fingerprint.status) 'Readiness status changed across application restart'
        Assert-Phase12 ($previous.completedSteps -eq $fingerprint.completedSteps) 'Completed steps changed across application restart'
        Assert-Phase12 ((@($previous.issueCodes) -join '|') -eq (@($fingerprint.issueCodes) -join '|')) 'Quality issues changed across application restart'
    } else {
        $fingerprint | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $statePath -Encoding utf8
    }

    [pscustomobject]@{
        status = 'PASS'
        mode = if ($VerifyPersistence) { 'PERSISTENCE' } else { 'FULL' }
        httpCount = $script:httpCount
        readiness = $overview.score
        onboardingStatus = $overview.status
        steps = @($overview.steps).Count
        qualityIssues = $quality.summary.total
        trace = $true
        externalActions = 0
    } | ConvertTo-Json -Compress
} finally {
    Pop-Location
}
