param(
    [switch]$ForceFull,
    [switch]$VerifyPersistence
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase7-smoke-state.json"
$baseUrl = "http://127.0.0.1:8088/api/v1"
$script:httpCount = 0

foreach ($entry in Get-Content -LiteralPath (Join-Path $projectRoot ".env")) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith("#")) { continue }
    $separator = $line.IndexOf("=")
    if ($separator -lt 1) { continue }
    [Environment]::SetEnvironmentVariable($line.Substring(0, $separator).Trim(), $line.Substring($separator + 1).Trim(), "Process")
}

function Invoke-Api([string]$Method, [string]$Uri, $Body, $Headers) {
    $script:httpCount++
    $parameters = @{ Method = $Method; Uri = $Uri; TimeoutSec = 120 }
    if ($null -ne $Headers) { $parameters.Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Assert-Http([int]$Expected, [string]$Method, [string]$Uri, $Body, $Headers) {
    try {
        $null = Invoke-Api $Method $Uri $Body $Headers
        throw "Expected HTTP $Expected"
    } catch {
        $actual = [int]$_.Exception.Response.StatusCode
        if ($actual -ne $Expected) { throw "Expected HTTP $Expected but received $actual" }
    }
}

function Login-Web {
    return (Invoke-Api POST "$baseUrl/auth/login" @{
        login = $env:BOOTSTRAP_USER_USERNAME
        password = $env:BOOTSTRAP_USER_PASSWORD
    } $null).data
}

function Pair-Fixture($WebHeaders, [string]$Name) {
    $code = (Invoke-Api POST "$baseUrl/extension/pairing-codes" @{} $WebHeaders).data
    return (Invoke-Api POST "$baseUrl/extension/pairings" @{
        pairingCode = $code.pairingCode
        deviceName = $Name
        browserName = "Chromium"
        extensionId = "fixture-extension"
        extensionVersion = "0.1.0"
    } $null).data
}

try {
    $backend = Invoke-RestMethod -Uri "http://127.0.0.1:8088/actuator/health" -TimeoutSec 3
    $worker = Invoke-RestMethod -Uri "http://127.0.0.1:8020/internal/v1/health" -TimeoutSec 3
    if ($backend.status -ne "UP") { throw "Backend is not healthy" }
    if ($worker.status -ne "UP" -or -not $worker.enabled) { throw "Automation Worker must be intentionally enabled" }
} catch { throw "Backend and enabled Automation Worker must be running before Phase 7 smoke" }

if ($VerifyPersistence) {
    if (-not (Test-Path -LiteralPath $statePath)) { throw "No Phase 7 smoke state exists" }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $login = Login-Web
    $web = @{ Authorization = "Bearer $($login.accessToken)" }
    $pair = Pair-Fixture $web "Phase 7 Persistence Reader"
    $extension = @{ Authorization = "Bearer $($pair.accessToken)" }
    $task = (Invoke-Api GET "$baseUrl/extension/automation-tasks/$($state.taskId)" $null $extension).data
    $job = (Invoke-Api GET "$baseUrl/jobs/$($state.jobId)" $null $web).data
    if ($task.status -ne "PREPARED" -or $task.externallySubmitted -or $task.applicationCreated) { throw "Persisted task safety state failed" }
    if ($job.job.id -ne $state.jobId) { throw "Persisted job readback failed" }
    $null = Invoke-Api DELETE "$baseUrl/extension/devices/$($pair.device.id)" $null $web
    [pscustomobject]@{ status = "PASS"; mode = "PERSISTENCE"; httpCount = $script:httpCount; jobId = $state.jobId; taskId = $state.taskId; steps = @($task.steps).Count } | ConvertTo-Json -Compress
    exit 0
}

$login = Login-Web
$web = @{ Authorization = "Bearer $($login.accessToken)"; "X-Trace-Id" = "phase7-web-smoke" }
$policy = (Invoke-Api PUT "$baseUrl/platform-policies/DEMO_FIXTURE" @{
    collectionMode = "VISIBLE_PAGE_ONLY"
    applicationMode = "ASSIST_ALLOWED"
    requiresFinalConfirmation = $true
    policySourceUrl = "http://127.0.0.1:8020/fixtures/application-form.html"
} $web).data
if ($policy.applicationMode -ne "ASSIST_ALLOWED" -or -not $policy.requiresFinalConfirmation) { throw "Platform policy failed" }

$pairingCode = (Invoke-Api POST "$baseUrl/extension/pairing-codes" @{} $web).data
$pairBody = @{
    pairingCode = $pairingCode.pairingCode
    deviceName = "Phase 7 Smoke"
    browserName = "Chromium"
    extensionId = "fixture-extension"
    extensionVersion = "0.1.0"
}
$pair = (Invoke-Api POST "$baseUrl/extension/pairings" $pairBody $null).data
if (-not $pair.accessToken.StartsWith("jpe_") -or -not $pair.refreshToken.StartsWith("jpr_")) { throw "Extension token contract failed" }
Assert-Http 401 POST "$baseUrl/extension/pairings" $pairBody $null

$oldRefresh = $pair.refreshToken
$refreshed = (Invoke-Api POST "$baseUrl/extension/tokens/refresh" @{ refreshToken = $oldRefresh } $null).data
Assert-Http 401 POST "$baseUrl/extension/tokens/refresh" @{ refreshToken = $oldRefresh } $null
$extension = @{ Authorization = "Bearer $($refreshed.accessToken)"; "X-Trace-Id" = "phase7-extension-smoke" }

$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$runKey = ([Guid]::NewGuid().ToString("N")).Substring(0, 10)
$description = "Build Java 21 Spring Boot services with MySQL Redis and safe local Assist verification. Fixture $runKey."
$raw = "Phase7 Java Engineer $runKey|JobPilot Fixture $runKey|$description|$timestamp"
$sha = [System.Security.Cryptography.SHA256]::Create()
$contentHash = ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($raw)))).Replace("-", "").ToLowerInvariant()
$capture = (Invoke-Api POST "$baseUrl/extension/job-captures" @{
    platform = "DEMO_FIXTURE"
    pageUrl = "http://127.0.0.1:8020/fixtures/application-form.html?fixture=$runKey"
    capturedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fff")
    userInitiated = $true
    adapterVersion = "fixture_v1"
    visibleFields = @{ jobTitle = "Phase7 Java Engineer $runKey"; companyName = "JobPilot Fixture $runKey"; city = "Shanghai"; salaryText = "35k-50k"; descriptionText = $description }
    contentHash = $contentHash
} $extension).data
$jobId = $capture.job.job.id
if (-not $jobId) { throw "Capture did not return a job id" }

$workspace = (Invoke-Api GET "$baseUrl/extension/jobs/$jobId/workspace" $null $extension).data
if ($workspace.externallySubmitted -or $workspace.messageSent) { throw "Extension workspace safety contract failed" }
Assert-Http 403 GET "$baseUrl/extension/jobs/$jobId/workspace" $null $web
Assert-Http 401 GET "$baseUrl/auth/me" $null $extension
Assert-Http 400 POST "$baseUrl/extension/job-captures" @{
    platform = "DEMO_FIXTURE"
    pageUrl = "http://127.0.0.1:8020/fixtures/application-form.html?forbidden=$runKey"
    capturedAt = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss.fff")
    userInitiated = $true
    adapterVersion = "fixture_v1"
    visibleFields = @{ jobTitle = "Forbidden"; companyName = "Fixture"; descriptionText = "Must not persist" }
    contentHash = $contentHash
    cookie = "must-not-be-accepted"
} $extension
$null = Invoke-Api POST "$baseUrl/extension/jobs/${jobId}:analyze" @{ force = $false } ($extension + @{ "Idempotency-Key" = "p7-analysis-$timestamp" })
$draft = (Invoke-Api POST "$baseUrl/extension/jobs/$jobId/communication-drafts" @{ channel = "BOSS" } ($extension + @{ "Idempotency-Key" = "p7-draft-$timestamp" })).data
$queue = (Invoke-Api POST "$baseUrl/extension/jobs/$jobId/queue" @{ draftId = $draft.id; priority = 70 } ($extension + @{ "Idempotency-Key" = "p7-queue-$timestamp" })).data
$approved = (Invoke-Api POST "$baseUrl/application-queue/items/$($queue.id):approve" @{ version = $queue.version } $web).data
if ($approved.status -ne "APPROVED") { throw "Queue approval failed" }

$prepareKey = "p7-prepare-$timestamp"
$task = (Invoke-Api POST "$baseUrl/extension/queue/$($queue.id):prepare" $null ($extension + @{ "Idempotency-Key" = $prepareKey })).data
if ($task.status -ne "PREPARED" -or $task.externallySubmitted -or $task.applicationCreated -or -not $task.finalConfirmationRequired) { throw "Assist safety contract failed" }
if (@($task.steps | Where-Object { $_.type -eq "FILL" }).Count -lt 1 -or $task.steps[-1].type -ne "HANDOFF") { throw "Task audit steps failed" }
$idempotent = (Invoke-Api POST "$baseUrl/extension/queue/$($queue.id):prepare" $null ($extension + @{ "Idempotency-Key" = $prepareKey })).data
if ($idempotent.id -ne $task.id) { throw "Prepare idempotency failed" }

Assert-Http 401 POST "http://127.0.0.1:8020/internal/v1/assist/prepare" @{
    schemaVersion = "assist-prepare-request-v1"; taskId = "01M1INVALID000000000000000"; taskToken = ("x" * 32)
    targetUrl = "http://127.0.0.1:8020/fixtures/application-form.html"; queueApprovalId = "01M1QUEUE0000000000000000"
    policyMode = "ASSIST_ALLOWED"; finalConfirmationRequired = $true; fields = @()
} @{ Authorization = "Bearer $env:AI_SERVICE_INTERNAL_TOKEN" }

$null = Invoke-Api DELETE "$baseUrl/extension/devices/$($refreshed.device.id)" $null $web
Assert-Http 401 GET "$baseUrl/extension/jobs/$jobId/workspace" $null $extension
Assert-Http 401 POST "$baseUrl/extension/tokens/refresh" @{ refreshToken = $refreshed.refreshToken } $null

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $statePath) | Out-Null
[pscustomobject]@{ jobId = $jobId; queueId = $queue.id; taskId = $task.id; createdAt = (Get-Date).ToString("o") } |
    ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
[pscustomobject]@{
    status = "PASS"; mode = "FULL"; httpCount = $script:httpCount; jobId = $jobId; queueId = $queue.id; taskId = $task.id
    automationStatus = $task.status; steps = @($task.steps).Count; fillSteps = @($task.steps | Where-Object { $_.type -eq "FILL" }).Count
    externallySubmitted = $task.externallySubmitted; applicationCreated = $task.applicationCreated; finalConfirmationRequired = $task.finalConfirmationRequired
} | ConvertTo-Json -Compress
