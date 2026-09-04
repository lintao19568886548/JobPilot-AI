param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$AiBaseUrl = "http://127.0.0.1:8010",
    [switch]$VerifyPersistence,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase6-smoke-state.json"
$envFile = Join-Path $projectRoot ".env"
$script:httpCount = 0

foreach ($entry in Get-Content -LiteralPath $envFile) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith("#")) { continue }
    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) { [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1], "Process") }
}

function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null,
          [string]$Token = $null, [string]$IdempotencyKey = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase6-smoke-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    Write-Host ("HTTP {0} {1}" -f $Method, $Path)
    $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 90 }
    if ($null -ne $Body) { $parameters.ContentType = "application/json"; $parameters.Body = ($Body | ConvertTo-Json -Depth 30 -Compress) }
    Invoke-RestMethod @parameters
}
function Assert-ApiError {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token,
          [int[]]$ExpectedStatuses, [string]$IdempotencyKey = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase6-negative-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    Write-Host ("HTTP {0} {1} (expect {2})" -f $Method, $Path, ($ExpectedStatuses -join ","))
    try {
        $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 90 }
        if ($null -ne $Body) { $parameters.ContentType = "application/json"; $parameters.Body = ($Body | ConvertTo-Json -Depth 30 -Compress) }
        $null = Invoke-RestMethod @parameters
        throw "Expected HTTP error for $Method $Path"
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -notin $ExpectedStatuses) { throw }
    }
}
function Login([string]$Name, [string]$Password) {
    (Invoke-Api POST "/api/auth/login" @{ login = $Name; password = $Password }).data
}

if ($VerifyPersistence -and $ForceFull) { throw "Use either -VerifyPersistence or -ForceFull." }
if (-not $VerifyPersistence -and -not $ForceFull -and (Test-Path -LiteralPath $statePath)) { $VerifyPersistence = $true }

$health = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 10
$readiness = Invoke-RestMethod "$AiBaseUrl/internal/v1/readiness" -TimeoutSec 10
Assert-True ($health.status -eq "UP") "Backend is not UP"
Assert-True ($readiness.status -eq "READY") "AI Service is not READY"

$login = Login $env:BOOTSTRAP_USER_USERNAME $env:BOOTSTRAP_USER_PASSWORD
$token = $login.accessToken
$refresh = (Invoke-Api POST "/api/auth/refresh" @{ refreshToken = $login.refreshToken }).data
Assert-True (-not [string]::IsNullOrWhiteSpace($refresh.accessToken)) "JWT refresh failed"

if ($VerifyPersistence) {
    Assert-True (Test-Path -LiteralPath $statePath) "Phase 6 state is missing"
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $run = (Invoke-Api GET "/api/v1/resume-tailor-runs/$($state.tailorRunId)" $null $token).data
    $draft = (Invoke-Api GET "/api/v1/communication-drafts/$($state.draftId)" $null $token).data
    $version = (Invoke-Api GET "/api/resume-versions/$($state.resumeVersionId)" $null $token).data
    $metrics = (Invoke-Api GET "/api/v1/resume-versions/$($state.resumeVersionId)/metrics" $null $token).data
    $application = (Invoke-Api GET "/api/v1/applications/$($state.applicationId)" $null $token).data
    $ledger = (Invoke-Api GET "/api/v1/evidence-ledger" $null $token).data
    Assert-True ($run.status -eq "APPROVED" -and $run.approvedResumeVersionId -eq $state.resumeVersionId) "Tailor approval did not persist"
    Assert-True ($draft.status -eq "USED" -and -not $draft.externallySent) "Draft state/safety boundary did not persist"
    Assert-True ($version.truthCheckStatus -eq "VERIFIED" -and $version.parentVersionId -eq $state.masterVersionId) "Tailored Resume Version did not persist"
    Assert-True ($metrics.applicationCount -ge 1 -and $metrics.replyCount -ge 1) "Resume metrics did not persist"
    Assert-True ($application.status -eq "REPLIED" -and $ledger.count -ge 1) "Daily-use facts did not persist"
    Write-Output "SMOKE_MODE=PERSISTENCE"
    Write-Output (@{ status = "PASS"; mode = "PERSISTENCE"; httpCount = $script:httpCount;
        tailorRun = $run.id; draft = $draft.id; resumeVersion = $version.id;
        application = $application.id; evidence = $ledger.count; replies = $metrics.replyCount } | ConvertTo-Json -Compress)
    exit 0
}

$resumes = (Invoke-Api GET "/api/resumes" $null $token).data
$master = @($resumes | Where-Object master)[0]
Assert-True ($null -ne $master -and $null -ne $master.currentVersionId) "Active Master Resume is required"
$masterBefore = (Invoke-Api GET "/api/resume-versions/$($master.currentVersionId)" $null $token).data
$runNonce = [guid]::NewGuid().ToString("N")

$jobResult = (Invoke-Api POST "/api/jobs" @{
    title = "Phase6 Evidence-bound Java AI Engineer"; companyName = "DEMO Phase6 Studio $runNonce"; city = "Shanghai";
    salaryText = "30-45K"; description = "Build Java, Spring Boot, Redis and RAG systems with traceable facts and engineering quality. Test batch $runNonce.";
    platform = "MANUAL"; platformJobId = "phase6-daily-mvp-$runNonce"; sourceType = "MANUAL";
    userInitiated = $true; parseAfterCreate = $false
} $token).data
$job = $jobResult.job.job
$parsedJob = (Invoke-Api POST "/api/v1/jobs/$($job.id)/parse-runs" $null $token).data
Assert-True ($parsedJob.job.status -eq "ACTIVE") "Phase 6 smoke job did not become ACTIVE after parsing"

$ledger = (Invoke-Api POST "/api/v1/evidence-ledger`:refresh" $null $token).data
$ledgerAgain = (Invoke-Api GET "/api/v1/evidence-ledger" $null $token).data
Assert-True ($ledger.count -ge 8 -and $ledgerAgain.count -eq $ledger.count -and $ledger.snapshotHash -eq $ledgerAgain.snapshotHash) "Evidence Ledger is incomplete or unstable"

$tailorKey = "phase6-tailor-$runNonce"
$tailorBody = @{ baseResumeVersionId = $master.currentVersionId; targetResumeId = $null; strategy = "ATS" }
$tailor = (Invoke-Api POST "/api/v1/jobs/$($job.id)/resume-tailor-runs" $tailorBody $token $tailorKey).data
$tailorReplay = (Invoke-Api POST "/api/v1/jobs/$($job.id)/resume-tailor-runs" $tailorBody $token $tailorKey).data
Assert-True ($tailor.id -eq $tailorReplay.id -and $tailor.status -eq "READY" -and $tailor.truthCheckStatus -eq "VERIFIED") "Tailor idempotency or truth check failed"
Assert-True (@($tailor.changes).Count -ge 1 -and @($tailor.changes | Where-Object { @($_.evidenceRefs).Count -eq 0 }).Count -eq 0) "Tailor changes are missing evidence"
Assert-ApiError POST "/api/v1/jobs/$($job.id)/resume-tailor-runs" @{
    baseResumeVersionId = $master.currentVersionId; strategy = "CONCISE"
} $token @(409) $tailorKey
$tailorDetail = (Invoke-Api GET "/api/v1/resume-tailor-runs/$($tailor.id)" $null $token).data
$approved = (Invoke-Api POST "/api/v1/resume-tailor-runs/$($tailor.id)`:approve" @{
    versionName = "Phase6 Java AI - Evidence Verified"
} $token).data
$approvedAgain = (Invoke-Api POST "/api/v1/resume-tailor-runs/$($tailor.id)`:approve" @{
    versionName = "Ignored duplicate approval name"
} $token).data
Assert-True ($approved.approvedResumeVersionId -eq $approvedAgain.approvedResumeVersionId) "Tailor approval is not idempotent"
$tailoredVersion = (Invoke-Api GET "/api/resume-versions/$($approved.approvedResumeVersionId)" $null $token).data
$masterAfter = (Invoke-Api GET "/api/resume-versions/$($master.currentVersionId)" $null $token).data
Assert-True ($tailoredVersion.sourceType -eq "AI" -and $tailoredVersion.truthCheckStatus -eq "VERIFIED") "Tailored Resume Version metadata is invalid"
Assert-True ($tailoredVersion.parentVersionId -eq $master.currentVersionId -and $tailoredVersion.tailoredForJobId -eq $job.id) "Tailored version provenance is missing"
Assert-True ($masterBefore.contentHash -eq $masterAfter.contentHash) "Master Resume was modified by Tailor"
$runList = (Invoke-Api GET "/api/v1/resume-tailor-runs" $null $token).data
Assert-True (@($runList | Where-Object id -eq $tailor.id).Count -eq 1) "Tailor Run history is missing"

$draftKey = "phase6-draft-boss-$runNonce"
$draftBody = @{ channel = "BOSS"; purpose = "APPLICATION"; resumeVersionId = $tailoredVersion.id }
$draft = (Invoke-Api POST "/api/v1/jobs/$($job.id)/communication-drafts" $draftBody $token $draftKey).data
$draftReplay = (Invoke-Api POST "/api/v1/jobs/$($job.id)/communication-drafts" $draftBody $token $draftKey).data
Assert-True ($draft.id -eq $draftReplay.id -and $draft.status -eq "DRAFT" -and -not $draft.externallySent) "Draft idempotency/safety boundary failed"
Assert-True ($draft.charCount -ge 60 -and $draft.charCount -le 100 -and @($draft.evidenceRefs).Count -ge 1) "BOSS Draft length/evidence failed"
$approvedDraft = (Invoke-Api POST "/api/v1/communication-drafts/$($draft.id)`:approve" @{ version = $draft.version } $token).data
$usedDraft = (Invoke-Api POST "/api/v1/communication-drafts/$($draft.id)`:mark-used" @{ version = $approvedDraft.version } $token).data
Assert-True ($usedDraft.status -eq "USED" -and -not $usedDraft.externallySent) "Draft lifecycle or send boundary failed"
$draftDetail = (Invoke-Api GET "/api/v1/communication-drafts/$($draft.id)" $null $token).data
Assert-True ($draftDetail.status -eq "USED") "Draft detail failed"

$queue = (Invoke-Api POST "/api/v1/application-queue/items" @{
    jobId = $job.id; resumeVersionId = $tailoredVersion.id; greetingReference = $draft.id;
    mode = "MANUAL"; priority = 95; allowUnevaluated = $true
} $token "phase6-queue-$runNonce").data
$queueApproved = (Invoke-Api POST "/api/v1/application-queue/items/$($queue.id)`:approve" @{ version = $queue.version } $token).data
Assert-ApiError POST "/api/v1/applications" @{
    queueItemId = $queue.id; mode = "MANUAL"; confirmedExternalSubmission = $false
} $token @(400) "phase6-application-false-$runNonce"
$application = (Invoke-Api POST "/api/v1/applications" @{
    queueItemId = $queue.id; mode = "MANUAL"; confirmedExternalSubmission = $true;
    externalApplicationId = "phase6-user-confirmed"; notes = "User confirmed external submission after reviewing Tailor and Draft"
} $token "phase6-application-$runNonce").data
$viewed = (Invoke-Api POST "/api/v1/applications/$($application.id)/transitions" @{
    toStatus = "VIEWED"; source = "USER"; note = "Viewed"; version = $application.version
} $token).data
$replied = (Invoke-Api POST "/api/v1/applications/$($application.id)/transitions" @{
    toStatus = "REPLIED"; source = "USER"; note = "Reply received"; version = $viewed.version
} $token).data
Assert-True ($replied.status -eq "REPLIED") "CRM reply transition failed"
$metrics = (Invoke-Api GET "/api/v1/resume-versions/$($tailoredVersion.id)/metrics" $null $token).data
Assert-True ($metrics.queueUseCount -eq 1 -and $metrics.applicationCount -eq 1 -and $metrics.replyCount -eq 1) "Resume Version metrics are incorrect"
$dashboard = (Invoke-Api GET "/api/v1/analytics/dashboard" $null $token).data
Assert-True ($dashboard.applications.replied -ge 1) "Dashboard did not retain Phase 6 application facts"

$other = Login "phase5_other" $env:BOOTSTRAP_USER_PASSWORD
Assert-ApiError GET "/api/v1/resume-tailor-runs/$($tailor.id)" $null $other.accessToken @(404)
Assert-ApiError GET "/api/v1/communication-drafts/$($draft.id)" $null $other.accessToken @(404)
Assert-ApiError GET "/api/v1/resume-versions/$($tailoredVersion.id)/metrics" $null $other.accessToken @(404)

$state = @{
    tailorRunId = $tailor.id; draftId = $draft.id; resumeVersionId = $tailoredVersion.id;
    masterVersionId = $master.currentVersionId; applicationId = $application.id; jobId = $job.id
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $statePath) | Out-Null
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8

Write-Output "SMOKE_MODE=FULL"
Write-Output (@{ status = "PASS"; mode = "FULL"; httpCount = $script:httpCount;
    evidence = $ledger.count; changes = @($tailorDetail.changes).Count; tailorRun = $tailor.id;
    resumeVersion = $tailoredVersion.id; draft = $draft.id; draftChars = $draft.charCount;
    queue = $queueApproved.id; application = $application.id; replyMetric = $metrics.replyCount;
    ownership = "PASS"; externalMessagesSent = 0 } | ConvertTo-Json -Compress)
