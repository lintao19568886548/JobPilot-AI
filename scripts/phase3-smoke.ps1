param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$AiBaseUrl = "http://127.0.0.1:8010",
    [switch]$VerifyPersistence,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase3-smoke-state.json"
$envFile = Join-Path $projectRoot ".env"
$script:httpCount = 0

foreach ($entry in Get-Content -LiteralPath $envFile) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith("#")) { continue }
    $separator = $line.IndexOf("=")
    if ($separator -gt 0) { [Environment]::SetEnvironmentVariable($line.Substring(0, $separator), $line.Substring($separator + 1), "Process") }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "ASSERTION FAILED: $Message" }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null, [string]$Token = $null, [string]$IdempotencyKey = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase3-smoke-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }
    $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 700 }
    if ($null -ne $Body) { $parameters.ContentType = "application/json"; $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress }
    $response = Invoke-RestMethod @parameters
    Assert-True ($response.code -eq 0) "$Method $Path returned code $($response.code)"
    Assert-True (-not [string]::IsNullOrWhiteSpace($response.traceId)) "$Method $Path did not return traceId"
    return $response
}

function Wait-MatchRun([string]$RunId, [string]$Token) {
    for ($attempt = 0; $attempt -lt 700; $attempt++) {
        $run = (Invoke-Api GET "/api/match-runs/$RunId" $null $Token).data
        if ($run.status -in @("SUCCEEDED", "FAILED", "DEAD")) { return $run }
        Start-Sleep -Seconds 1
    }
    throw "Match run $RunId did not reach a terminal state"
}

function Start-Match([string]$JobId, [string]$Token, [bool]$Force, [string]$Key) {
    $started = (Invoke-Api POST "/api/jobs/$JobId/match-runs" @{ force = $Force; idempotencyKey = $Key } $Token $Key).data
    return Wait-MatchRun $started.id $Token
}

function Rule-Body([string]$Action, [decimal]$Penalty, [bool]$Active) {
    return @{
        ruleKey = "phase3_experience_gate"; name = "Phase 3 experience gate";
        ruleType = "EXPERIENCE_YEARS"; operator = "GTE"; operand = @{ source = "JOB_REQUIREMENT" };
        resultAction = $Action; penalty = $Penalty; priority = 10; active = $Active
    }
}

$backend = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 5
$ai = Invoke-RestMethod "$AiBaseUrl/internal/v1/readiness" -TimeoutSec 5
Assert-True ($backend.status -eq "UP") "Backend is not healthy"
Assert-True ($ai.status -eq "READY" -and $ai.embeddingModel -eq "BAAI/bge-m3" -and $ai.embeddingDimension -eq 1024) "AI/BGE-M3 is not ready"

$login = (Invoke-Api POST "/api/auth/login" @{ login = $env:BOOTSTRAP_USER_USERNAME; password = $env:BOOTSTRAP_USER_PASSWORD }).data
$token = $login.accessToken
$refresh = Invoke-Api POST "/api/auth/refresh" @{ refreshToken = $login.refreshToken }
Assert-True (-not [string]::IsNullOrWhiteSpace($refresh.data.accessToken)) "JWT refresh failed"

if ($VerifyPersistence -and $ForceFull) { throw "Use either -VerifyPersistence or -ForceFull." }
if (-not $VerifyPersistence -and -not $ForceFull -and (Test-Path -LiteralPath $statePath)) { $VerifyPersistence = $true }

if ($VerifyPersistence) {
    Assert-True (Test-Path -LiteralPath $statePath) "Phase 3 smoke state does not exist"
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $match = (Invoke-Api GET "/api/job-matches/$($state.passMatchId)" $null $token).data
    $history = (Invoke-Api GET "/api/jobs/$($state.jobId)/matches" $null $token).data
    $configs = (Invoke-Api GET "/api/match-configs" $null $token).data
    $rules = (Invoke-Api GET "/api/hard-filter-rules" $null $token).data
    Assert-True ($match.hardFilterResult -eq "PASS" -and $match.embeddingModel -eq "BAAI/bge-m3") "Persisted match is invalid"
    Assert-True ($match.llmStatus -eq "SKIPPED_NOT_CONFIGURED" -and $null -eq $match.scores.llm) "No-provider LLM behavior was not persisted"
    Assert-True ($history.Count -ge 3) "Match history did not survive restart"
    Assert-True (@($configs | Where-Object active).Count -eq 1) "Exactly one active match config is required"
    Assert-True (@($rules | Where-Object { $_.ruleKey -eq "phase3_experience_gate" }).Count -ge 3) "Rule versions did not survive restart"
    Write-Output "SMOKE_MODE=PERSISTENCE"
    Write-Output (@{ status = "PASS"; mode = "PERSISTENCE"; httpCount = $script:httpCount; jobId = $state.jobId; matchId = $state.passMatchId } | ConvertTo-Json -Compress)
    exit 0
}

$profile = (Invoke-Api GET "/api/candidate/profile" $null $token).data
$jobs = (Invoke-Api GET "/api/jobs?limit=50&sort=publish_desc" $null $token).data.items
$job = $jobs | Where-Object { $null -ne $_.experienceMinYears -and [decimal]$_.experienceMinYears -gt [decimal]$profile.yearsOfExperience -and $_.parseStatus -eq "SUCCESS" } | Select-Object -First 1
Assert-True ($null -ne $job) "A parsed job with a higher experience requirement is required"

$configs = (Invoke-Api GET "/api/match-configs" $null $token).data
Assert-True (@($configs | Where-Object active).Count -eq 1) "Expected one active immutable match config"
$rules = (Invoke-Api GET "/api/hard-filter-rules" $null $token).data
$activeSmokeRule = $rules | Where-Object { $_.ruleKey -eq "phase3_experience_gate" -and $_.active } | Select-Object -First 1
if ($activeSmokeRule) { $null = Invoke-Api PATCH "/api/hard-filter-rules/$($activeSmokeRule.id)" (Rule-Body "REJECT" 0 $false) $token }

$passRun = Start-Match $job.id $token $true "phase3-pass-$([guid]::NewGuid().ToString('N'))"
Assert-True ($passRun.status -eq "SUCCEEDED") "PASS match run failed"
$passMatch = (Invoke-Api GET "/api/job-matches/$($passRun.resultMatchId)" $null $token).data
Assert-True ($passMatch.hardFilterResult -eq "PASS") "Hard Filter PASS was not produced"
Assert-True ($passMatch.embeddingModel -eq "BAAI/bge-m3" -and $passMatch.embeddingVersion -eq "bge-m3-v1") "Real BGE-M3 metadata is missing"
Assert-True ($passMatch.llmStatus -eq "SKIPPED_NOT_CONFIGURED" -and $null -eq $passMatch.scores.llm -and [decimal]$passMatch.effectiveWeights.llm -eq 0) "LLM skip/re-normalization is invalid"
Assert-True ([decimal]$passMatch.scores.overall -ge 0 -and [decimal]$passMatch.scores.overall -le 100) "Final score is outside 0-100"
Assert-True ($passMatch.level -in @("S", "A", "B", "C", "D")) "Match level is invalid"

$downRule = (Invoke-Api POST "/api/hard-filter-rules" (Rule-Body "DOWNGRADE" 7 $true) $token).data
$downRun = Start-Match $job.id $token $true "phase3-down-$([guid]::NewGuid().ToString('N'))"
$downMatch = (Invoke-Api GET "/api/job-matches/$($downRun.resultMatchId)" $null $token).data
Assert-True ($downMatch.hardFilterResult -eq "DOWNGRADE" -and [decimal]$downMatch.scores.penalty -eq 7) "Hard Filter DOWNGRADE was not produced"

$rejectRule = (Invoke-Api PATCH "/api/hard-filter-rules/$($downRule.id)" (Rule-Body "REJECT" 0 $true) $token).data
$rejectRun = Start-Match $job.id $token $true "phase3-reject-$([guid]::NewGuid().ToString('N'))"
$rejectMatch = (Invoke-Api GET "/api/job-matches/$($rejectRun.resultMatchId)" $null $token).data
Assert-True ($rejectMatch.hardFilterResult -eq "REJECT" -and $rejectMatch.status -eq "REJECTED" -and $null -eq $rejectMatch.scores.overall) "Hard Filter REJECT was not produced"
Assert-True ($rejectMatch.llmStatus -eq "SKIPPED_NOT_CONFIGURED" -and [decimal]$rejectMatch.effectiveWeights.llm -eq 0) "REJECT path did not preserve LLM skip/re-normalization metadata"
$disabledRule = (Invoke-Api PATCH "/api/hard-filter-rules/$($rejectRule.id)" (Rule-Body "REJECT" 0 $false) $token).data
Assert-True (-not $disabledRule.active -and $disabledRule.versionNo -gt $rejectRule.versionNo) "Rule version was not deactivated immutably"

$stableKey = "phase3-idempotent-$([guid]::NewGuid().ToString('N'))"
$first = Start-Match $job.id $token $false $stableKey
$second = Start-Match $job.id $token $false $stableKey
Assert-True ($first.id -eq $second.id) "Idempotent match request returned different run IDs"

$state = @{
    jobId = $job.id; passRunId = $passRun.id; passMatchId = $passMatch.id;
    downgradeMatchId = $downMatch.id; rejectMatchId = $rejectMatch.id;
    configId = ($configs | Where-Object active | Select-Object -First 1).id;
    ruleVersion = $disabledRule.versionNo; minimumHistory = 3; createdAt = [DateTime]::UtcNow.ToString("o")
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $statePath) | Out-Null
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8

Write-Output "SMOKE_MODE=FULL"
Write-Output (@{
    status = "PASS"; mode = "FULL"; httpCount = $script:httpCount; jobId = $job.id;
    pass = $passMatch.hardFilterResult; downgrade = $downMatch.hardFilterResult; reject = $rejectMatch.hardFilterResult;
    overall = $passMatch.scores.overall; level = $passMatch.level; llmStatus = $passMatch.llmStatus
} | ConvertTo-Json -Compress)
