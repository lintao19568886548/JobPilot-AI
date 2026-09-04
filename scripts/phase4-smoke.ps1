param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$AiBaseUrl = "http://127.0.0.1:8010",
    [switch]$VerifyPersistence,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase4-smoke-state.json"
$envFile = Join-Path $projectRoot ".env"
$script:httpCount = 0

foreach ($entry in Get-Content -LiteralPath $envFile) {
    $line = $entry.Trim()
    if (-not $line -or $line.StartsWith("#")) { continue }
    $separator = $line.IndexOf("=")
    if ($separator -gt 0) {
        [Environment]::SetEnvironmentVariable($line.Substring(0, $separator), $line.Substring($separator + 1), "Process")
    }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "ASSERTION FAILED: $Message" }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body = $null,
          [string]$Token = $null, [string]$IdempotencyKey = $null)
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase4-smoke-$([guid]::NewGuid().ToString('N'))" }
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

function Assert-HttpError([string]$Path, [string]$Token, [int[]]$ExpectedStatuses) {
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "phase4-negative-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    try {
        $null = Invoke-RestMethod -Uri "$BaseUrl$Path" -Method GET -Headers $headers -TimeoutSec 30
        throw "Expected HTTP failure from $Path"
    } catch {
        $response = $_.Exception.Response
        if ($null -eq $response) { throw }
        $status = [int]$response.StatusCode
        Assert-True ($ExpectedStatuses -contains $status) "$Path returned unexpected HTTP status $status"
    }
}

function Wait-Refresh([string]$RefreshId, [string]$Token) {
    for ($attempt = 0; $attempt -lt 700; $attempt++) {
        $run = (Invoke-Api GET "/api/recommendation-refresh-runs/$RefreshId" $null $Token).data
        if ($run.status -in @("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED")) { return $run }
        Start-Sleep -Seconds 1
    }
    throw "Recommendation refresh $RefreshId did not reach a terminal state"
}

function Page([string]$View, [string]$Token, [int]$Limit = 100, [string]$Sort = "AI_RECOMMENDED", [string]$Cursor = $null) {
    $path = "/api/recommendations?view=$View&limit=$Limit&sort=$Sort"
    if ($Cursor) { $path += "&cursor=$([uri]::EscapeDataString($Cursor))" }
    return (Invoke-Api GET $path $null $Token).data
}

if ($VerifyPersistence -and $ForceFull) { throw "Use either -VerifyPersistence or -ForceFull." }
if (-not $VerifyPersistence -and -not $ForceFull -and (Test-Path -LiteralPath $statePath)) {
    Write-Output "SMOKE_MODE=AUTO_PERSISTENCE"
    Write-Output "Existing Phase 4 smoke state detected. Use -ForceFull to create a new event sequence."
    $VerifyPersistence = $true
}

$backend = Invoke-RestMethod "$BaseUrl/actuator/health" -TimeoutSec 10
$ai = Invoke-RestMethod "$AiBaseUrl/internal/v1/readiness" -TimeoutSec 10
Assert-True ($backend.status -eq "UP") "Backend is not healthy"
Assert-True ($ai.status -eq "READY" -and $ai.embeddingModel -eq "BAAI/bge-m3" -and $ai.embeddingDimension -eq 1024) "AI/BGE-M3 is not ready"

$login = (Invoke-Api POST "/api/auth/login" @{ login = $env:BOOTSTRAP_USER_USERNAME; password = $env:BOOTSTRAP_USER_PASSWORD }).data
$token = $login.accessToken
$refreshed = (Invoke-Api POST "/api/auth/refresh" @{ refreshToken = $login.refreshToken }).data
Assert-True (-not [string]::IsNullOrWhiteSpace($refreshed.accessToken)) "JWT refresh failed"

if ($VerifyPersistence) {
    Assert-True (Test-Path -LiteralPath $statePath) "Phase 4 smoke state does not exist"
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $favorite = (Invoke-Api GET "/api/recommendations/$($state.favoriteRecommendationId)" $null $token).data
    $ignored = Page "IGNORED" $token
    $events = (Invoke-Api GET "/api/recommendations/$($state.favoriteRecommendationId)/events" $null $token).data
    $refreshRun = (Invoke-Api GET "/api/recommendation-refresh-runs/$($state.refreshRunId)" $null $token).data
    $dashboard = (Invoke-Api GET "/api/analytics/dashboard" $null $token).data
    Assert-True ($favorite.recommendation.favorite) "Favorite recommendation did not survive restart"
    Assert-True (@($ignored.items | Where-Object id -eq $state.ignoredRecommendationId).Count -eq 1) "Ignored recommendation did not survive restart"
    Assert-True (@($events).Count -ge $state.minimumFavoriteEvents) "Recommendation events did not survive restart"
    Assert-True ($refreshRun.status -in @("SUCCEEDED", "PARTIAL_SUCCESS")) "Refresh run did not survive restart"
    Assert-True ($dashboard.matching.totalJobs -eq $state.totalRecommendations) "Dashboard projection changed after restart"
    Write-Output "SMOKE_MODE=PERSISTENCE"
    Write-Output (@{ status = "PASS"; mode = "PERSISTENCE"; httpCount = $script:httpCount;
        recommendations = $state.totalRecommendations; favorite = $state.favoriteRecommendationId;
        ignored = $state.ignoredRecommendationId; refreshRun = $state.refreshRunId } | ConvertTo-Json -Compress)
    exit 0
}

$capabilities = (Invoke-Api GET "/api/recommendations/capabilities" $null $token).data
Assert-True ($capabilities.applicationPhase -eq "PHASE_5") "Phase 5 application boundary is not explicit"
if ($capabilities.applicationTracking) {
    Assert-True ($capabilities.reason -match "Phase 5|application") "Implemented Phase 5 capability is not described"
} else {
    Assert-True ($capabilities.reason) "Deferred Phase 5 capability has no boundary reason"
}

$initialAll = Page "ALL" $token
$initialIgnored = Page "IGNORED" $token
Assert-True (($initialAll.total + $initialIgnored.total) -ge 2) "At least two recommendation projections are required"
$projectionIds = @($initialAll.items | ForEach-Object { $_.id }) + @($initialIgnored.items | ForEach-Object { $_.id })
Assert-True (@($projectionIds | Select-Object -Unique).Count -eq $projectionIds.Count) "Recommendation projection contains duplicate jobs"

$refreshKey = "phase4-refresh-$([guid]::NewGuid().ToString('N'))"
$refreshBody = @{ onlyUnevaluated = $false; force = $false; limit = 100; idempotencyKey = $refreshKey }
$started = (Invoke-Api POST "/api/recommendation-refresh-runs" $refreshBody $token $refreshKey).data
$same = (Invoke-Api POST "/api/recommendation-refresh-runs" $refreshBody $token $refreshKey).data
Assert-True ($started.id -eq $same.id) "Batch refresh idempotency returned different run IDs"
$refreshRun = Wait-Refresh $started.id $token
Assert-True ($refreshRun.status -in @("SUCCEEDED", "PARTIAL_SUCCESS")) "Batch refresh failed"
Assert-True (($refreshRun.succeededCount + $refreshRun.failedCount) -eq $refreshRun.totalCount) "Batch refresh terminal counts do not reconcile"

$all = Page "ALL" $token
$top = Page "TOP" $token
$unevaluated = Page "UNEVALUATED" $token
$ignoredBeforeActions = Page "IGNORED" $token
$favoriteBeforeActions = Page "FAVORITE" $token
Assert-True (@($top.items | Where-Object { $_.match.level -notin @("S", "A", "B") }).Count -eq 0) "TOP view contains a non S/A/B result"
Assert-True (@($unevaluated.items | Where-Object status -ne "UNEVALUATED").Count -eq 0) "UNEVALUATED view contains evaluated data"
Assert-True (@($ignoredBeforeActions.items | Where-Object status -ne "IGNORED").Count -eq 0) "IGNORED view contains active data"
Assert-True (@($favoriteBeforeActions.items | Where-Object favorite -ne $true).Count -eq 0) "FAVORITE view contains non-favorites"

foreach ($sort in @("AI_RECOMMENDED", "MATCH_DESC", "PUBLISH_DESC", "SALARY_DESC", "COMPANY_ASC", "CITY_ASC")) {
    $sorted = Page "ALL" $token 100 $sort
    Assert-True (@($sorted.items.id | Select-Object -Unique).Count -eq @($sorted.items).Count) "$sort produced duplicate rows"
}

$pagedIds = @()
$cursor = $null
do {
    $page = Page "ALL" $token 2 "AI_RECOMMENDED" $cursor
    $pagedIds += @($page.items.id)
    $cursor = $page.nextCursor
} while ($page.hasMore)
Assert-True (@($pagedIds | Select-Object -Unique).Count -eq $pagedIds.Count) "Cursor pagination duplicated a recommendation"
Assert-True ($pagedIds.Count -eq $all.total) "Cursor pagination did not cover the entire ALL view"

$actionCandidates = @($all.items | Where-Object status -ne "IGNORED")
Assert-True ($actionCandidates.Count -ge 2) "Two active recommendations are required for action validation"
$favoriteTarget = $actionCandidates | Where-Object { -not $_.favorite } | Select-Object -First 1
$ignoreTarget = $actionCandidates | Where-Object { $_.id -ne $favoriteTarget.id } | Select-Object -First 1
Assert-True ($null -ne $favoriteTarget -and $null -ne $ignoreTarget) "Independent favorite and ignore targets are required"

$favoriteEvents0 = @((Invoke-Api GET "/api/recommendations/$($favoriteTarget.id)/events" $null $token).data).Count
$favorite1 = (Invoke-Api POST "/api/recommendations/$($favoriteTarget.id):favorite" @{ version = $favoriteTarget.version } $token).data
$favoriteDuplicate = (Invoke-Api POST "/api/recommendations/$($favoriteTarget.id):favorite" @{ version = $favoriteTarget.version } $token).data
$favoriteEvents1 = @((Invoke-Api GET "/api/recommendations/$($favoriteTarget.id)/events" $null $token).data).Count
Assert-True ($favorite1.favorite -and $favoriteDuplicate.favorite) "Favorite transition failed"
Assert-True ($favoriteEvents1 -eq ($favoriteEvents0 + 1)) "Idempotent favorite created a duplicate event"
$unfavorite1 = (Invoke-Api POST "/api/recommendations/$($favoriteTarget.id):unfavorite" @{ version = $favorite1.version } $token).data
$unfavoriteDuplicate = (Invoke-Api POST "/api/recommendations/$($favoriteTarget.id):unfavorite" @{ version = $unfavorite1.version } $token).data
Assert-True (-not $unfavorite1.favorite -and -not $unfavoriteDuplicate.favorite) "Unfavorite transition failed"
$favoriteFinal = (Invoke-Api POST "/api/recommendations/$($favoriteTarget.id):favorite" @{ version = $unfavoriteDuplicate.version } $token).data
Assert-True ($favoriteFinal.favorite) "Final favorite state was not persisted"

$ignoreEvents0 = @((Invoke-Api GET "/api/recommendations/$($ignoreTarget.id)/events" $null $token).data).Count
$ignored1 = (Invoke-Api POST "/api/recommendations/$($ignoreTarget.id):ignore" @{ version = $ignoreTarget.version; reason = "Phase 4 smoke: direction mismatch" } $token).data
$ignoredDuplicate = (Invoke-Api POST "/api/recommendations/$($ignoreTarget.id):ignore" @{ version = $ignoreTarget.version; reason = "duplicate must be ignored" } $token).data
$ignoreEvents1 = @((Invoke-Api GET "/api/recommendations/$($ignoreTarget.id)/events" $null $token).data).Count
Assert-True ($ignored1.status -eq "IGNORED" -and $ignoredDuplicate.status -eq "IGNORED") "Ignore transition failed"
Assert-True ($ignoreEvents1 -eq ($ignoreEvents0 + 1)) "Idempotent ignore created a duplicate event"
$restored = (Invoke-Api POST "/api/recommendations/$($ignoreTarget.id):restore" @{ version = $ignored1.version } $token).data
Assert-True ($restored.status -ne "IGNORED") "Restore transition failed"
$ignoredFinal = (Invoke-Api POST "/api/recommendations/$($ignoreTarget.id):ignore" @{ version = $restored.version; reason = "Phase 4 persistence marker" } $token).data
Assert-True ($ignoredFinal.status -eq "IGNORED") "Final ignored state was not persisted"

$detail = (Invoke-Api GET "/api/recommendations/$($favoriteTarget.id)" $null $token).data
Assert-True ($detail.recommendation.id -eq $favoriteTarget.id) "Recommendation detail identity mismatch"
if ($null -ne $detail.recommendation.match) {
    Assert-True ($null -ne $detail.matchAnalysis -and $detail.matchAnalysis.id -eq $detail.recommendation.match.id) "Latest immutable match projection mismatch"
}

$allAfter = Page "ALL" $token
$ignoredAfter = Page "IGNORED" $token
$favoriteAfter = Page "FAVORITE" $token
$dashboard = (Invoke-Api GET "/api/analytics/dashboard" $null $token).data
Assert-True ($dashboard.matching.totalJobs -eq ($allAfter.total + $ignoredAfter.total)) "Dashboard total does not reconcile with recommendation views"
Assert-True ($dashboard.matching.favoriteJobs -eq $favoriteAfter.total) "Dashboard favorite count does not reconcile"
Assert-True ($dashboard.matching.ignoredJobs -eq $ignoredAfter.total) "Dashboard ignored count does not reconcile"
Assert-True ((@($dashboard.levels | Measure-Object -Property value -Sum).Sum) -eq $dashboard.matching.evaluatedJobs) "Match level distribution does not reconcile"
Assert-True ((@($dashboard.sources | Measure-Object -Property value -Sum).Sum) -eq $dashboard.matching.totalJobs) "Source distribution does not reconcile"
Assert-True ($dashboard.funnel[0].value -eq $dashboard.matching.totalJobs) "Funnel discovery stage does not reconcile"
if ($capabilities.applicationTracking) {
    Assert-True (@($dashboard.funnel | Where-Object { $_.key -eq "APPLIED" }).Count -eq 1) "Implemented Phase 5 Applied funnel stage is missing"
    Assert-True (@($dashboard.unavailableCapabilities | Where-Object { $_.key -eq "APPLIED" }).Count -eq 0) "Implemented Phase 5 capability is still marked unavailable"
} else {
    Assert-True (@($dashboard.unavailableCapabilities | Where-Object { $_.key -eq "APPLIED" -and -not $_.available -and $_.phase -eq "PHASE_5" }).Count -eq 1) "Dashboard exposes a fake Phase 5 KPI"
}

$levels = (Invoke-Api GET "/api/analytics/levels" $null $token).data
$sources = (Invoke-Api GET "/api/analytics/sources" $null $token).data
$funnel = (Invoke-Api GET "/api/analytics/funnel" $null $token).data
Assert-True (@($levels).Count -eq 6 -and @($sources).Count -ge 1 -and @($funnel).Count -ge 5) "Analytics breakdown endpoints are incomplete"
$today = (Get-Date).ToString("yyyy-MM-dd")
$rebuild = (Invoke-Api POST "/api/analytics:rebuild" @{ from = $today; to = $today } $token).data
Assert-True ($rebuild.dayCount -eq 1 -and $rebuild.rowCount -ge 1 -and $rebuild.timezone -eq "Asia/Shanghai") "Analytics daily rebuild failed"

$jobSearch = (Invoke-Api GET "/api/search?q=Java&types=JOB,SKILL&limit=20" $null $token).data
$statusSearch = (Invoke-Api GET "/api/search?q=FAVORITE&types=STATUS&limit=20" $null $token).data
$literalSearch = (Invoke-Api GET "/api/search?q=$([uri]::EscapeDataString('%_\'))&types=JOB,COMPANY,SKILL&limit=20" $null $token).data
Assert-True (@($jobSearch.groups).Count -eq 2) "Global search did not return requested groups"
Assert-True ($statusSearch.total -ge 1) "Global status search returned no result"
Assert-True ($literalSearch.total -ge 0) "Literal wildcard search failed"
Assert-HttpError "/api/search?q=java&types=USER" $token @(400)
Assert-HttpError "/api/search?q=java" $null @(401, 403)
Assert-HttpError "/api/recommendations/01AAAAAAAAAAAAAAAAAAAAAAAA" $token @(404)

$favoriteEventsFinal = @((Invoke-Api GET "/api/recommendations/$($favoriteTarget.id)/events" $null $token).data).Count
$ignoreEventsFinal = @((Invoke-Api GET "/api/recommendations/$($ignoreTarget.id)/events" $null $token).data).Count
$state = @{
    favoriteRecommendationId = $favoriteTarget.id
    ignoredRecommendationId = $ignoreTarget.id
    refreshRunId = $refreshRun.id
    minimumFavoriteEvents = $favoriteEventsFinal
    minimumIgnoreEvents = $ignoreEventsFinal
    totalRecommendations = $dashboard.matching.totalJobs
    analyticsDate = $today
    createdAt = [DateTime]::UtcNow.ToString("o")
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $statePath) | Out-Null
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8

Write-Output "SMOKE_MODE=FULL"
Write-Output (@{ status = "PASS"; mode = "FULL"; httpCount = $script:httpCount;
    recommendations = $dashboard.matching.totalJobs; evaluated = $dashboard.matching.evaluatedJobs;
    top = $top.total; unevaluated = $unevaluated.total; favorite = $favoriteAfter.total;
    ignored = $ignoredAfter.total; events = ($favoriteEventsFinal + $ignoreEventsFinal);
    refreshStatus = $refreshRun.status; analyticsRows = $rebuild.rowCount; searchResults = $jobSearch.total } | ConvertTo-Json -Compress)
