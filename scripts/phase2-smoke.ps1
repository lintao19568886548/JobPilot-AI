param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$AiBaseUrl = "http://127.0.0.1:8010",
    [switch]$VerifyPersistence,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase2-smoke-state.json"
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
    param([string]$Method, [string]$Path, [object]$Body = $null, [string]$Token = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase2-smoke-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{ Uri = "$BaseUrl$Path"; Method = $Method; Headers = $headers; TimeoutSec = 30 }
    if ($null -ne $Body) { $parameters.ContentType = "application/json"; $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress }
    $response = Invoke-RestMethod @parameters
    Assert-True ($response.code -eq 0) "$Method $Path returned code $($response.code)"
    Assert-True (-not [string]::IsNullOrWhiteSpace($response.traceId)) "$Method $Path did not return traceId"
    return $response
}

function Login {
    return (Invoke-Api POST "/api/auth/login" @{ login = $env:BOOTSTRAP_USER_USERNAME; password = $env:BOOTSTRAP_USER_PASSWORD }).data
}

try {
    $backendHealth = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 3
    $aiHealth = Invoke-RestMethod "$AiBaseUrl/internal/v1/health" -TimeoutSec 3
    $aiReadiness = Invoke-RestMethod "$AiBaseUrl/internal/v1/readiness" -TimeoutSec 3
} catch { throw "Backend and AI service must both be running before Phase 2 smoke tests." }
Assert-True ($backendHealth.status -eq "UP") "Backend is not healthy"
Assert-True ($aiHealth.status -eq "UP" -and $aiReadiness.status -eq "READY") "AI parser is not ready"
Assert-True ($aiReadiness.parserMode -eq "RULES_ONLY" -and -not $aiReadiness.llmConfigured) "Expected explicit RULES_ONLY mode"

if ($VerifyPersistence -and $ForceFull) { throw "Use either -VerifyPersistence or -ForceFull." }
if (-not $VerifyPersistence -and -not $ForceFull -and (Test-Path -LiteralPath $statePath)) { $VerifyPersistence = $true }

$auth = Login
$token = $auth.accessToken
Assert-True (-not [string]::IsNullOrWhiteSpace($token)) "Login did not return an access token"
$refresh = Invoke-Api POST "/api/auth/refresh" @{ refreshToken = $auth.refreshToken }
Assert-True (-not [string]::IsNullOrWhiteSpace($refresh.data.accessToken)) "JWT refresh failed"

if ($VerifyPersistence) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $job = Invoke-Api GET "/api/jobs/$($state.jobId)" $null $token
    Assert-True ($job.data.job.title -eq $state.jobTitle) "Job did not survive backend restart"
    Assert-True ($job.data.sources.Count -ge 3) "Merged job sources did not survive backend restart"
    Assert-True ($job.data.skills.Count -ge 4) "Parsed job skills did not survive backend restart"
    $csvTask = Invoke-Api GET "/api/job-imports/$($state.csvTaskId)" $null $token
    $xlsxTask = Invoke-Api GET "/api/job-imports/$($state.xlsxTaskId)" $null $token
    Assert-True ($csvTask.data.status -eq "PARTIAL_SUCCESS") "CSV task did not survive restart"
    Assert-True ($xlsxTask.data.status -eq "COMPLETED") "XLSX task did not survive restart"
    $dashboard = Invoke-Api GET "/api/dashboard" $null $token
    Assert-True ($dashboard.data.totalJobs -ge $state.minimumJobs) "Dashboard job totals did not survive restart"
    Write-Output "SMOKE_MODE=PERSISTENCE"
    Write-Output (@{ status = "PASS"; mode = "PERSISTENCE"; parserMode = $aiReadiness.parserMode; httpCount = $script:httpCount; jobId = $state.jobId } | ConvertTo-Json -Compress)
    exit 0
}

& (Join-Path $projectRoot "ai-service\.venv\Scripts\python.exe") (Join-Path $projectRoot "scripts\generate-phase2-fixtures.py") | Out-Null

$company = Invoke-Api POST "/api/companies" @{
    displayName = "DEMO Phase2 Catalog Company"; website = "https://example.com"; industry = "Software";
    companySize = "100-499"; financingStage = "SERIES_B"; headquartersCity = "Shanghai";
    description = "Disposable smoke-test company catalog record"; verifiedSource = "DEMO"
} $token
$companyId = $company.data.id
$companyUpdated = Invoke-Api PATCH "/api/companies/$companyId" @{
    displayName = "DEMO Phase2 Catalog Company"; website = "https://example.com"; industry = "Enterprise Software";
    companySize = "100-499"; financingStage = "SERIES_B"; headquartersCity = "Shanghai";
    description = "Updated smoke-test company catalog record"; verifiedSource = "DEMO"; version = $company.data.version
} $token
$null = Invoke-Api GET "/api/companies/$companyId" $null $token
$null = Invoke-Api GET "/api/companies?search=Catalog" $null $token
$null = Invoke-Api DELETE "/api/companies/$companyId" $null $token

$description = "Responsible for Java and Spring Boot services. Must be proficient in Java, MySQL8 and Redis Cluster. Kafka preferred. K8s nice to have. 3-5 years required."
$jobPayload = @{
    title = "Phase2 Java Backend Engineer"; companyName = "DEMO Phase2 Product Ltd."; city = "Shanghai";
    salaryText = "20-30K"; description = $description; platform = "DEMO_A";
    platformJobId = "phase2-main-exact"; sourceType = "MANUAL"; userInitiated = $true; parseAfterCreate = $true
}
$created = Invoke-Api POST "/api/jobs" $jobPayload $token
Assert-True ($created.data.created -or $created.data.dedupDecision -eq "EXACT_DUPLICATE") "Initial job create/idempotent lookup failed"
$jobId = $created.data.job.job.id
$initialParse = Invoke-Api POST "/api/jobs/$jobId/parse-runs" $null $token
Assert-True ($initialParse.data.job.parseStatus -eq "SUCCESS") "RULES_ONLY parser did not succeed"
Assert-True ($initialParse.data.job.parserMode -eq "RULES_ONLY") "Parser mode was not recorded"
Assert-True (($initialParse.data.skills.requirementType -contains "MUST_HAVE") -and ($initialParse.data.skills.requirementType -contains "NICE_TO_HAVE")) "Must/nice skill classification failed"

$exact = Invoke-Api POST "/api/jobs" $jobPayload $token
Assert-True (-not $exact.data.created -and $exact.data.dedupDecision -eq "EXACT_DUPLICATE") "Exact dedup failed"
$rulePayload = $jobPayload.Clone(); $rulePayload.platform = "DEMO_B"; $rulePayload.platformJobId = "phase2-main-rule"
$rule = Invoke-Api POST "/api/jobs" $rulePayload $token
Assert-True (-not $rule.data.created -and $rule.data.dedupDecision -in @("RULE_MERGED", "EXACT_DUPLICATE")) "Rule dedup/idempotent source lookup failed"
$null = Invoke-Api POST "/api/jobs/$jobId/sources" @{
    platform = "DEMO_C"; platformJobId = "phase2-main-third"; sourceType = "MANUAL";
    sourceTitle = "Phase2 Java Backend Engineer"; sourceCompanyName = "DEMO Phase2 Product Ltd.";
    rawSnapshot = @{ visible = "source-three" }; collectorVersion = "smoke-v1"; userInitiated = $true
} $token
$sources = Invoke-Api GET "/api/jobs/$jobId/sources" $null $token
Assert-True ($sources.data.Count -ge 3) "Multi-source merge was not persisted"

$current = Invoke-Api GET "/api/jobs/$jobId" $null $token
$updated = Invoke-Api PATCH "/api/jobs/$jobId" @{
    title = "Phase2 Senior Java Backend Engineer"; companyName = "DEMO Phase2 Product Ltd."; city = "Shanghai";
    remoteType = "HYBRID"; salaryMin = 25000; salaryMax = 35000; salaryMonths = 14; currency = "CNY";
    salaryText = "MANUAL 25-35K"; education = "MASTER"; experienceMinYears = 8; experienceMaxYears = 10;
    jobType = "FULL_TIME"; description = $description; reason = "Smoke manual override"; version = $current.data.job.version
} $token
$parsedAgain = Invoke-Api POST "/api/jobs/$jobId/parse-runs" $null $token
Assert-True ($parsedAgain.data.job.education -eq "MASTER") "Parser overwrote manually edited education"
Assert-True ($parsedAgain.data.job.experienceMinYears -eq 8) "Parser overwrote manually edited experience"

$filtered = Invoke-Api GET "/api/jobs?city=Shanghai&skill=Java&sort=updated_desc&limit=1" $null $token
Assert-True ($filtered.data.items.Count -eq 1) "Filter/sort/page did not return a page"
$ignored = Invoke-Api POST "/api/jobs/$jobId`:ignore" $null $token
Assert-True ($ignored.data.job.status -eq "IGNORED") "Ignore failed"
$restored = Invoke-Api POST "/api/jobs/$jobId`:restore" $null $token
Assert-True ($restored.data.job.status -eq "ACTIVE") "Restore failed"

$script:httpCount++
try {
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/extension/job-captures" -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body (@{
        platform = "DEMO_EXTENSION"; pageUrl = "https://example.com/jobs/forbidden"; userInitiated = $true;
        adapterVersion = "demo-v1"; visibleFields = @{ jobTitle = "Blocked"; companyName = "Blocked"; descriptionText = "Blocked" };
        contentHash = ("a" * 64); cookies = "must-not-be-accepted"
    } | ConvertTo-Json -Depth 10) | Out-Null
    throw "Extension forbidden-field request unexpectedly succeeded"
} catch {
    Assert-True ($_.Exception.Response.StatusCode.value__ -eq 400) "Forbidden extension field did not return HTTP 400"
}
$extension = Invoke-Api POST "/api/extension/job-captures" @{
    platform = "DEMO_EXTENSION"; pageUrl = "https://example.com/jobs/visible"; userInitiated = $true; adapterVersion = "demo-v1";
    visibleFields = @{ jobTitle = "Extension Java Engineer"; companyName = "DEMO Extension Company"; city = "Beijing";
        salaryText = "16-24K"; descriptionText = "Must know Java and Redis. Kafka preferred."; platformJobId = "extension-visible-1" };
    contentHash = ("b" * 64)
} $token
Assert-True ($extension.data.job.job.parseStatus -eq "SUCCESS") "Valid extension capture did not parse"

$urlTask = Invoke-Api POST "/api/job-imports/url" @{
    url = "https://example.com/"; platform = "DEMO_WEB"; platformJobId = "example-domain-phase2";
    userInitiated = $true; idempotencyKey = "phase2-url-example-v1"
} $token
Assert-True ($urlTask.data.status -eq "COMPLETED") "URL import did not complete"

function Import-File([string]$Path, [string]$Key) {
    $script:httpCount++
    $traceId = "phase2-file-$([guid]::NewGuid().ToString('N'))"
    $jsonResponse = & curl.exe --silent --show-error --max-time 60 --request POST `
        --header "Authorization: Bearer $token" --header "Idempotency-Key: $Key" --header "X-Trace-Id: $traceId" `
        --form "file=@$Path" "$BaseUrl/api/job-imports/files"
    if ($LASTEXITCODE -ne 0) { throw "curl file upload failed with exit code $LASTEXITCODE" }
    $response = $jsonResponse | ConvertFrom-Json
    Assert-True ($response.code -eq 0) "File import returned API error"
    return $response
}

$csv = Import-File (Join-Path $projectRoot "scripts\fixtures\phase2-jobs.csv") "phase2-csv-v1"
Assert-True ($csv.data.status -eq "PARTIAL_SUCCESS" -and $csv.data.successCount -eq 2 -and $csv.data.failureCount -eq 1) "CSV partial import result is wrong"
$csvErrors = Invoke-Api GET "/api/job-imports/$($csv.data.id)/errors" $null $token
Assert-True ($csvErrors.data.Count -eq 1 -and $csvErrors.data[0].rowNumber -eq 3) "CSV row error was not recorded"
$xlsx = Import-File (Join-Path $projectRoot "scripts\fixtures\phase2-jobs.xlsx") "phase2-xlsx-v1"
Assert-True ($xlsx.data.status -eq "COMPLETED" -and $xlsx.data.successCount -eq 1) "XLSX import failed"

$dashboard = Invoke-Api GET "/api/dashboard" $null $token
Assert-True ($dashboard.data.totalJobs -ge 6 -and $dashboard.data.parsedJobs -ge 5) "Dashboard real job totals were not updated"
Assert-True ($dashboard.data.importTasks -ge 3) "Dashboard import task count is wrong"

$state = @{
    jobId = $jobId; jobTitle = "Phase2 Senior Java Backend Engineer"; csvTaskId = $csv.data.id;
    xlsxTaskId = $xlsx.data.id; urlTaskId = $urlTask.data.id; minimumJobs = [int]$dashboard.data.totalJobs;
    recordedAt = (Get-Date).ToUniversalTime().ToString("o")
}
$state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
Write-Output "SMOKE_MODE=FULL"
Write-Output (@{ status = "PASS"; mode = "FULL"; parserMode = $aiReadiness.parserMode; httpCount = $script:httpCount; jobs = $dashboard.data.totalJobs; imports = $dashboard.data.importTasks; jobId = $jobId } | ConvertTo-Json -Compress)
