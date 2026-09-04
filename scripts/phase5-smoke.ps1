param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$AiBaseUrl = "http://127.0.0.1:8010",
    [switch]$VerifyPersistence,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase5-smoke-state.json"
$envFile = Join-Path $projectRoot ".env"
$script:httpCount = 0

foreach ($entry in Get-Content -LiteralPath $envFile) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith("#")) { continue }
    $separator = $line.IndexOf("=")
    if ($separator -gt 0) {
        $value = $line.Substring($separator + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($line.Substring(0, $separator).Trim(), $value, "Process")
    }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "ASSERTION FAILED: $Message" }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null,
          [string]$Token = $null, [string]$IdempotencyKey = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase5-smoke-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 700 }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    $response = Invoke-RestMethod @parameters
    Assert-True ($response.code -eq 0) "$Method $Path returned code $($response.code)"
    Assert-True (-not [string]::IsNullOrWhiteSpace($response.traceId)) "$Method $Path did not return traceId"
    return $response
}

function Assert-ApiError {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token,
          [int[]]$ExpectedStatuses, [string]$IdempotencyKey = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase5-negative-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    try {
        $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 30 }
        if ($null -ne $Body) {
            $parameters.ContentType = "application/json"
            $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
        }
        $null = Invoke-RestMethod @parameters
        throw "Expected HTTP failure from $Method $Path"
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $status = [int]$response.StatusCode
        Assert-True ($ExpectedStatuses -contains $status) "$Method $Path returned unexpected HTTP status $status"
    }
}

function Login([string]$LoginName, [string]$Password) {
    return (Invoke-Api POST "/api/auth/login" @{ login = $LoginName; password = $Password }).data
}

if ($VerifyPersistence -and $ForceFull) { throw "Use either -VerifyPersistence or -ForceFull." }
if (-not $VerifyPersistence -and -not $ForceFull -and (Test-Path -LiteralPath $statePath)) {
    Write-Output "SMOKE_MODE=AUTO_PERSISTENCE"
    Write-Output "Existing Phase 5 smoke state detected. Use -ForceFull only when enough unused jobs remain."
    $VerifyPersistence = $true
}

$backend = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 10
$ai = Invoke-RestMethod "$AiBaseUrl/internal/v1/readiness" -TimeoutSec 10
Assert-True ($backend.status -eq "UP") "Backend is not healthy"
Assert-True ($ai.status -eq "READY" -and $ai.embeddingModel -eq "BAAI/bge-m3" -and $ai.embeddingDimension -eq 1024) "AI/BGE-M3 regression is not ready"

$login = Login $env:BOOTSTRAP_USER_USERNAME $env:BOOTSTRAP_USER_PASSWORD
$token = $login.accessToken
$refreshed = (Invoke-Api POST "/api/auth/refresh" @{ refreshToken = $login.refreshToken }).data
Assert-True (-not [string]::IsNullOrWhiteSpace($refreshed.accessToken)) "JWT refresh failed"

if ($VerifyPersistence) {
    Assert-True (Test-Path -LiteralPath $statePath) "Phase 5 smoke state does not exist"
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $application = (Invoke-Api GET "/api/v1/applications/$($state.applicationId)" $null $token).data
    $logs = (Invoke-Api GET "/api/v1/applications/$($state.applicationId)/logs" $null $token).data
    $recruiter = (Invoke-Api GET "/api/v1/recruiters/$($state.recruiterId)" $null $token).data
    $queue = (Invoke-Api GET "/api/v1/application-queue" $null $token).data
    $dashboard = (Invoke-Api GET "/api/v1/analytics/dashboard" $null $token).data
    Assert-True ($application.status -eq $state.applicationStatus) "Application status did not survive restart"
    Assert-True (@($logs).Count -ge $state.minimumLogCount) "Immutable Application Logs did not survive restart"
    Assert-True (@($recruiter.interactions).Count -ge 1) "Recruiter interaction did not survive restart"
    Assert-True (@($queue | Where-Object id -eq $state.queueItemId).Count -eq 1) "Queue history did not survive restart"
    Assert-True ($dashboard.applications.applications -ge 1) "Application dashboard KPI did not survive restart"
    Assert-True ($dashboard.applications.replied -ge 1) "Reached REPLIED stage was lost after a later transition"
    Assert-True ($dashboard.applications.writtenTest -ge 1) "Reached WRITTEN_TEST stage did not survive restart"
    Write-Output "SMOKE_MODE=PERSISTENCE"
    Write-Output (@{ status = "PASS"; mode = "PERSISTENCE"; httpCount = $script:httpCount;
        application = $state.applicationId; statusValue = $application.status;
        logs = @($logs).Count; recruiter = $state.recruiterId;
        repliedReached = $dashboard.applications.replied;
        writtenTestReached = $dashboard.applications.writtenTest } | ConvertTo-Json -Compress)
    exit 0
}

$capabilities = (Invoke-Api GET "/api/recommendations/capabilities" $null $token).data
Assert-True ($capabilities.applicationTracking -and $capabilities.applicationPhase -eq "PHASE_5") "Phase 5 Application capability is not enabled"

$unknownPolicy = (Invoke-Api GET "/api/v1/platform-policies/unknown-smoke-platform" $null $token).data
Assert-True ($unknownPolicy.applicationMode -eq "MANUAL_ONLY" -and -not $unknownPolicy.configured) "Unknown platform did not safely default to Manual"

$existingQueue = @((Invoke-Api GET "/api/v1/application-queue" $null $token).data)
$existingApplications = @((Invoke-Api GET "/api/v1/applications" $null $token).data.items)
$usedJobs = @($existingQueue.job.id) + @($existingApplications.job.id)
$recommendations = @((Invoke-Api GET "/api/recommendations?view=ALL&limit=100" $null $token).data.items |
    Where-Object { $_.job.id -notin $usedJobs -and $null -ne $_.match })
Assert-True ($recommendations.Count -ge 3) "Phase 5 full smoke requires three evaluated jobs without an existing queue/application"
$primary = $recommendations[0]
$batchCandidates = @($recommendations[1], $recommendations[2])

$policy = (Invoke-Api PUT "/api/v1/platform-policies/$([uri]::EscapeDataString($primary.job.sourcePlatform))" @{
    collectionMode = "VISIBLE_PAGE_ONLY"; applicationMode = "ASSIST_ALLOWED";
    requiresFinalConfirmation = $true; policySourceUrl = $null
} $token).data
Assert-True ($policy.applicationMode -eq "ASSIST_ALLOWED" -and $policy.requiresFinalConfirmation) "Assist policy configuration failed"

$queueKey = "phase5-queue-$([guid]::NewGuid().ToString('N'))"
$queueBody = @{ jobId = $primary.job.id; jobMatchId = $primary.match.id; mode = "ASSIST";
    priority = 82; allowUnevaluated = $false; greetingReference = "Manual greeting reference only" }
$queueItem = (Invoke-Api POST "/api/v1/application-queue/items" $queueBody $token $queueKey).data
$queueDuplicate = (Invoke-Api POST "/api/v1/application-queue/items" $queueBody $token $queueKey).data
$activeDuplicate = (Invoke-Api POST "/api/v1/application-queue/items" $queueBody $token "phase5-active-$([guid]::NewGuid().ToString('N'))").data
Assert-True ($queueItem.id -eq $queueDuplicate.id -and $queueItem.id -eq $activeDuplicate.id) "Queue idempotency/unique active item failed"
Assert-True ($queueItem.status -eq "READY" -and $queueItem.mode -eq "ASSIST") "Assist queue item was not ready"

$updatedQueue = (Invoke-Api PATCH "/api/v1/application-queue/items/$($queueItem.id)" @{
    priority = 91; greetingReference = "Reviewed manual greeting reference"; version = $queueItem.version
} $token).data
Assert-True ($updatedQueue.priority -eq 91) "Queue priority update failed"

$batchKey = "phase5-batch-$([guid]::NewGuid().ToString('N'))"
$batchBody = @{ items = @($batchCandidates | ForEach-Object {
    @{ jobId = $_.job.id; jobMatchId = $_.match.id; mode = "MANUAL"; priority = 40; allowUnevaluated = $false }
}) }
$batch = (Invoke-Api POST "/api/v1/application-queue/items/batch" $batchBody $token $batchKey).data
$batchAgain = (Invoke-Api POST "/api/v1/application-queue/items/batch" $batchBody $token $batchKey).data
Assert-True ($batch.uniqueResultCount -eq 2 -and $batchAgain.reusedCount -eq 2) "Batch queue idempotency failed"

Assert-ApiError POST "/api/v1/application-queue/items" @{
    jobId = $batchCandidates[0].job.id; jobMatchId = $batchCandidates[0].match.id;
    mode = "AUTHORIZED_AUTOMATION"; priority = 50; allowUnevaluated = $false
} $token @(403) "phase5-auto-reject-$([guid]::NewGuid().ToString('N'))"

$approved = (Invoke-Api POST "/api/v1/application-queue/items/$($queueItem.id):approve" @{ version = $updatedQueue.version } $token).data
$applicationCountBeforePrepare = @((Invoke-Api GET "/api/v1/applications" $null $token).data.items).Count
$prepared = (Invoke-Api POST "/api/v1/application-queue/items/$($queueItem.id):prepare" @{ version = $approved.version } $token).data
$applicationCountAfterPrepare = @((Invoke-Api GET "/api/v1/applications" $null $token).data.items).Count
Assert-True ($prepared.item.status -eq "PREPARED") "Assist Prepare did not reach PREPARED"
Assert-True (-not $prepared.externallySubmitted -and -not $prepared.applicationCreated) "Assist Prepare crossed the external submission boundary"
Assert-True ($applicationCountBeforePrepare -eq $applicationCountAfterPrepare) "Assist Prepare created an Application"

Assert-ApiError POST "/api/v1/applications" @{
    queueItemId = $queueItem.id; mode = "ASSIST"; confirmedExternalSubmission = $false
} $token @(400) "phase5-unconfirmed-$([guid]::NewGuid().ToString('N'))"

$applicationKey = "phase5-application-$([guid]::NewGuid().ToString('N'))"
$applicationBody = @{ queueItemId = $queueItem.id; mode = "ASSIST";
    confirmedExternalSubmission = $true; notes = "Explicitly confirmed after manual external submission" }
$application = (Invoke-Api POST "/api/v1/applications" $applicationBody $token $applicationKey).data
$applicationDuplicate = (Invoke-Api POST "/api/v1/applications" $applicationBody $token $applicationKey).data
Assert-True ($application.id -eq $applicationDuplicate.id -and $application.status -eq "APPLIED") "Application confirmation idempotency failed"

$logsBeforeIllegal = @((Invoke-Api GET "/api/v1/applications/$($application.id)/logs" $null $token).data)
Assert-ApiError POST "/api/v1/applications/$($application.id)/transitions" @{
    toStatus = "OFFER"; source = "USER"; note = "Illegal jump must be rejected";
    evidence = @{ test = "illegal-transition" }; version = $application.version
} $token @(409)
$afterIllegal = (Invoke-Api GET "/api/v1/applications/$($application.id)" $null $token).data
$logsAfterIllegal = @((Invoke-Api GET "/api/v1/applications/$($application.id)/logs" $null $token).data)
Assert-True ($afterIllegal.status -eq "APPLIED" -and $logsAfterIllegal.Count -eq $logsBeforeIllegal.Count) "Illegal transition changed Application or logs"

$viewed = (Invoke-Api POST "/api/v1/applications/$($application.id)/transitions" @{
    toStatus = "VIEWED"; source = "USER"; note = "Observed recruiter view";
    evidence = @{ channel = "platform" }; version = $application.version
} $token).data
$replied = (Invoke-Api POST "/api/v1/applications/$($application.id)/transitions" @{
    toStatus = "REPLIED"; source = "USER"; note = "Recruiter replied";
    evidence = @{ channel = "platform" }; version = $viewed.version
} $token).data
$writtenTest = (Invoke-Api POST "/api/v1/applications/$($application.id)/transitions" @{
    toStatus = "WRITTEN_TEST"; source = "USER"; note = "Written test received";
    evidence = @{ enteredBy = "user" }; version = $replied.version
} $token).data
Assert-True ($writtenTest.status -eq "WRITTEN_TEST" -and @($writtenTest.timeline).Count -eq 4) "Legal CRM transitions/timeline failed"

$skipped = (Invoke-Api POST "/api/v1/application-queue/items/$($batch.items[0].id):skip" @{ version = $batch.items[0].version } $token).data
Assert-True ($skipped.status -eq "SKIPPED") "Queue skip failed"
$null = Invoke-Api DELETE "/api/v1/application-queue/items/$($batch.items[1].id)" $null $token
$queueAfterDelete = @((Invoke-Api GET "/api/v1/application-queue" $null $token).data)
Assert-True (@($queueAfterDelete | Where-Object id -eq $batch.items[1].id).Count -eq 0) "Logical queue delete remained visible"

$recruiter = (Invoke-Api POST "/api/v1/recruiters" @{
    name = "Phase 5 Recruiter $([guid]::NewGuid().ToString('N').Substring(0,8))";
    position = "Senior Recruiter"; platform = $primary.job.sourcePlatform;
    contactMasked = "manual***"; communicationStatus = "NEW";
    notes = "Created by Phase 5 smoke"
} $token).data
$linkedApplication = (Invoke-Api PATCH "/api/v1/applications/$($application.id)" @{
    recruiterId = $recruiter.id; notes = "Recruiter linked by user"; version = $writtenTest.version
} $token).data
$interaction = (Invoke-Api POST "/api/v1/recruiters/$($recruiter.id)/interactions" @{
    applicationId = $application.id; channel = "PLATFORM"; direction = "INBOUND";
    summary = "Manual communication fact; API did not send a message"
} $token).data
$recruiterDetail = (Invoke-Api GET "/api/v1/recruiters/$($recruiter.id)" $null $token).data
Assert-True ($linkedApplication.recruiterId -eq $recruiter.id) "Recruiter was not linked to Application"
Assert-True ($interaction.applicationId -eq $application.id -and @($recruiterDetail.interactions).Count -eq 1) "Recruiter interaction was not recorded"

$dashboard = (Invoke-Api GET "/api/v1/analytics/dashboard" $null $token).data
Assert-True ($dashboard.applications.queueTotal -ge 3 -and $dashboard.applications.applications -ge 1) "Dashboard Application KPIs are incomplete"
Assert-True ($dashboard.applications.replied -ge 1 -and $dashboard.applications.writtenTest -ge 1) "Application reached-stage KPIs are incomplete"
Assert-True (@($dashboard.funnel | Where-Object key -eq "QUEUED").Count -eq 1) "Dashboard Queue funnel stage is missing"
Assert-True (@($dashboard.funnel | Where-Object key -eq "APPLIED").Count -eq 1) "Dashboard Applied funnel stage is missing"
$today = (Get-Date).ToString("yyyy-MM-dd")
$rebuild = (Invoke-Api POST "/api/v1/analytics:rebuild" @{ from = $today; to = $today } $token).data
Assert-True ($rebuild.dayCount -eq 1 -and $rebuild.rowCount -ge 1) "Application Analytics Daily rebuild failed"

$escapedBootstrap = $env:BOOTSTRAP_USER_USERNAME.Replace("'", "''")
$sql = "INSERT INTO users (public_id,username,email,password_hash,display_name,status,created_at,updated_at,version) SELECT '01K5ZZZZZZZZZZZZZZZZZZZZZZ','phase5_other','phase5-other@local.invalid',password_hash,'Phase 5 Other','ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0 FROM users WHERE username='$escapedBootstrap' LIMIT 1 ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),status='ACTIVE',deleted_at=NULL,updated_at=UTC_TIMESTAMP(3);"
Push-Location $projectRoot
try {
    & docker compose exec -T mysql mysql "-u$env:MYSQL_USERNAME" "-p$env:MYSQL_PASSWORD" $env:MYSQL_DATABASE "-e" $sql | Out-Null
    Assert-True ($LASTEXITCODE -eq 0) "Unable to create ownership test user"
} finally { Pop-Location }
$otherLogin = Login "phase5_other" $env:BOOTSTRAP_USER_PASSWORD
$otherToken = $otherLogin.accessToken
Assert-ApiError GET "/api/v1/applications/$($application.id)" $null $otherToken @(404)
Assert-ApiError GET "/api/v1/recruiters/$($recruiter.id)" $null $otherToken @(404)
$otherApplications = (Invoke-Api GET "/api/v1/applications" $null $otherToken).data
Assert-True (@($otherApplications.items).Count -eq 0) "Second user can see another user's Applications"

$finalLogs = @((Invoke-Api GET "/api/v1/applications/$($application.id)/logs" $null $token).data)
$state = @{
    applicationId = $application.id
    applicationStatus = $writtenTest.status
    queueItemId = $queueItem.id
    recruiterId = $recruiter.id
    minimumLogCount = $finalLogs.Count
    analyticsDate = $today
    createdAt = [DateTime]::UtcNow.ToString("o")
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $statePath) | Out-Null
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8

Write-Output "SMOKE_MODE=FULL"
Write-Output (@{ status = "PASS"; mode = "FULL"; httpCount = $script:httpCount;
    queueItems = $queueAfterDelete.Count; application = $application.id;
    applicationStatus = $writtenTest.status; logs = $finalLogs.Count;
    recruiter = $recruiter.id; interaction = $interaction.id;
    analyticsRows = $rebuild.rowCount; ownership = "PASS";
    assistExternalSubmitted = $prepared.externallySubmitted } | ConvertTo-Json -Compress)
