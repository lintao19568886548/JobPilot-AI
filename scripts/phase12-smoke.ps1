param([switch]$VerifyPersistence)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
$gatewayPort = if ($env:JOBPILOT_GATEWAY_PORT) { $env:JOBPILOT_GATEWAY_PORT } else { '8180' }
$backendPort = if ($env:BACKEND_PORT) { $env:BACKEND_PORT } else { '8088' }
$aiPort = if ($env:AI_SERVICE_PORT) { $env:AI_SERVICE_PORT } else { '8010' }
$gateway = "http://127.0.0.1:$gatewayPort"
$backend = "http://127.0.0.1:$backendPort"
$statePath = Join-Path $projectRoot 'scripts\.phase12-smoke-state.json'
$script:httpCount = 0
function Web([string]$Method,[string]$Url,[hashtable]$Headers,$Body=$null){$script:httpCount++;$p=@{Method=$Method;Uri=$Url;Headers=$Headers;TimeoutSec=30;UseBasicParsing=$true};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 20 -Compress};Invoke-WebRequest @p}
function Read-WebText($Response) {
    if ($Response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Response.Content) }
    return [string]$Response.Content
}
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $gatewayPort = if ($env:JOBPILOT_GATEWAY_PORT) { $env:JOBPILOT_GATEWAY_PORT } else { '8180' }; $gateway = "http://127.0.0.1:$gatewayPort"
    $compose = Get-Phase12ComposeArguments $projectRoot
    $homeResponse = Web GET "$gateway/" @{}
    Assert-Phase12 ($homeResponse.StatusCode -eq 200 -and (Read-WebText $homeResponse) -match 'JobPilot AI') 'Release frontend is unavailable'
    foreach ($header in @('X-Content-Type-Options','X-Frame-Options','Referrer-Policy','Permissions-Policy','Content-Security-Policy')) { Assert-Phase12 (-not [string]::IsNullOrWhiteSpace($homeResponse.Headers[$header])) "Missing security header: $header" }
    $spa = Web GET "$gateway/operations" @{}; Assert-Phase12 ($spa.StatusCode -eq 200 -and (Read-WebText $spa) -match 'JobPilot AI') 'SPA fallback failed'
    $healthResponse = Web GET "$gateway/actuator/health" @{}; $health = Read-WebText $healthResponse | ConvertFrom-Json; Assert-Phase12 ($health.status -eq 'UP') 'Backend health proxy failed'
    $openapi = Web GET "$gateway/v3/api-docs" @{}; Assert-Phase12 ($openapi.StatusCode -eq 200 -and (Read-WebText $openapi) -match 'JobPilot') 'OpenAPI proxy failed'
    $swagger = Web GET "$gateway/swagger-ui.html" @{}; Assert-Phase12 ($swagger.StatusCode -eq 200 -and (Read-WebText $swagger) -match 'Swagger UI') 'Swagger proxy failed'
    $aiResponse = Web GET "http://127.0.0.1:$aiPort/internal/v1/readiness" @{}; $ai = Read-WebText $aiResponse | ConvertFrom-Json; Assert-Phase12 ($ai.status -eq 'READY') 'AI release service is unavailable'
    $loginBody = @{login='phase10_smoke';password=(Get-Phase11Password $projectRoot)}
    $loginResponse = Web POST "$gateway/api/v1/auth/login" @{} $loginBody; $login = Read-WebText $loginResponse | ConvertFrom-Json
    $headers = @{Authorization="Bearer $($login.data.accessToken)";'X-Trace-Id'="phase12-$([Guid]::NewGuid().ToString('N'))"}
    $meResponse = Web GET "$gateway/api/v1/auth/me" $headers; $me=Read-WebText $meResponse|ConvertFrom-Json
    Assert-Phase12 ($me.traceId -eq $headers['X-Trace-Id'] -and $meResponse.Headers['X-Trace-Id'] -eq $headers['X-Trace-Id']) 'Gateway trace propagation failed'
    $overviewResponse = Web GET "$gateway/api/v1/operations/overview" $headers; $overview = (Read-WebText $overviewResponse | ConvertFrom-Json).data
    foreach ($name in @('backend','mysql','redis','milvus')) { Assert-Phase12 ($overview.health.$name -eq 'UP') "$name health is not UP" }
    Assert-Phase12 ($overview.health.aiService -eq 'READY') 'AI health is not READY'
    Assert-Phase12 ($overview.safety.externalMessagesSent -eq 0 -and $overview.safety.externalSubmissions -eq 0 -and $overview.safety.externalMutations -eq 0 -and $overview.safety.automaticOfferDecisions -eq 0 -and $overview.safety.destructiveDuplicateDeletes -eq 0) 'A forbidden external action was detected'
    $budgetResponse = Web GET "$gateway/api/v1/operations/ai-budget" $headers; $budget = (Read-WebText $budgetResponse|ConvertFrom-Json).data
    $firstRun = @($overview.recentRuns)[0]
    if ($VerifyPersistence) {
        $state=Get-Content -LiteralPath $statePath -Raw|ConvertFrom-Json
        Assert-Phase12 ($budget.id -eq $state.budgetId) 'AI budget did not persist across application restart'
        Assert-Phase12 ($firstRun.id -eq $state.latestRunId) 'Latest operational run did not persist across application restart'
    } else {
        [ordered]@{budgetId=$budget.id;latestRunId=$firstRun.id}|ConvertTo-Json|Set-Content -LiteralPath $statePath -Encoding utf8
    }
    foreach ($serviceName in @('backend','ai-service','frontend')) {
        $id=(& docker compose @compose ps -q $serviceName).Trim();$inspect=& docker inspect $id|ConvertFrom-Json
        Assert-Phase12 ($inspect[0].Config.User -notmatch '^(|0|root)(:|$)') "$serviceName runs as root"
        Assert-Phase12 ($inspect[0].HostConfig.ReadonlyRootfs) "$serviceName root filesystem is writable"
        Assert-Phase12 (@($inspect[0].HostConfig.CapDrop) -contains 'ALL') "$serviceName does not drop all Linux capabilities"
        Assert-Phase12 ($inspect[0].HostConfig.SecurityOpt -contains 'no-new-privileges:true') "$serviceName lacks no-new-privileges"
    }
    [pscustomobject]@{status='PASS';mode=if($VerifyPersistence){'PERSISTENCE'}else{'FULL'};httpCount=$script:httpCount;gateway=$gateway;securityHeaders=5;trace=$true;containersHardened=3;safetyExternalActions=0}|ConvertTo-Json -Compress
} finally { Pop-Location }
