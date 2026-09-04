param([switch]$VerifyPersistence)

$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'phase11-common.ps1')
$baseUrl='http://127.0.0.1:8088'
$statePath=Join-Path $projectRoot 'scripts\.phase11-smoke-state.json'
$script:httpCount=0
function Api([string]$Method,[string]$Path,[hashtable]$Headers,$Body=$null){$script:httpCount++;$p=@{Method=$Method;Uri="$baseUrl$Path";Headers=$Headers;TimeoutSec=60};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 30 -Compress};Invoke-RestMethod @p}
function Assert([bool]$Condition,[string]$Message){if(!$Condition){throw $Message}}
function Expect([int]$Status,[scriptblock]$Action,[string]$Label){try{$null=&$Action;throw "$Label unexpectedly succeeded"}catch{$actual=[int]$_.Exception.Response.StatusCode;if($actual-ne$Status){throw "$Label expected HTTP $Status but received $actual"}}}
Push-Location $projectRoot
try{
  Import-JobPilotEnv $projectRoot
  Assert((Invoke-RestMethod "$baseUrl/actuator/health" -TimeoutSec 8).status-eq'UP') 'Backend is unavailable'
  Assert((docker compose exec -T redis redis-cli ping)-match'PONG') 'Redis is unavailable'
  $login=Api 'POST' '/api/v1/auth/login' @{} @{login='phase10_smoke';password=(Get-Phase11Password $projectRoot)}
  $intruder=Api 'POST' '/api/v1/auth/login' @{} @{login='phase10_intruder';password=(&{ $sha=[Security.Cryptography.SHA256]::Create();try{$hex=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes("phase10-intruder|$($env:JWT_SECRET)")))).Replace('-','');"P10!$($hex.Substring(0,30))z"}finally{$sha.Dispose()} })}
  $headers=@{Authorization="Bearer $($login.data.accessToken)"};$intruderHeaders=@{Authorization="Bearer $($intruder.data.accessToken)"}
  if($VerifyPersistence){
    $state=Get-Content -LiteralPath $statePath -Raw|ConvertFrom-Json
    $budget=Api 'GET' '/api/v1/operations/ai-budget' $headers;$run=Api 'GET' "/api/v1/operations/runs/$($state.runId)" $headers
    Assert($budget.data.id-eq$state.budgetId) 'Budget did not persist';Assert($run.data.id-eq$state.runId) 'Operational run did not persist'
    [pscustomobject]@{status='PASS';mode='PERSISTENCE';httpCount=$script:httpCount;budgetId=$budget.data.id;runId=$run.data.id}|ConvertTo-Json -Compress;exit 0
  }
  $trace="phase11-$([Guid]::NewGuid().ToString('N'))";$traceHeaders=$headers.Clone();$traceHeaders['X-Trace-Id']=$trace
  $script:httpCount++;$request=[Net.HttpWebRequest]::Create("$baseUrl/api/v1/auth/me");$request.Method='GET';$request.Timeout=15000;$request.Headers['Authorization']=$traceHeaders.Authorization;$request.Headers['X-Trace-Id']=$trace
  $traceResponse=$request.GetResponse();try{$reader=[IO.StreamReader]::new($traceResponse.GetResponseStream());try{$traceBody=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()};$traceHeader=$traceResponse.Headers['X-Trace-Id']}finally{$traceResponse.Dispose()}
  Assert($traceHeader-eq$trace) 'Trace response header mismatch';Assert($traceBody.traceId-eq$trace) 'Trace response body mismatch'
  $script:httpCount++;$prom=Invoke-RestMethod "$baseUrl/actuator/prometheus" -TimeoutSec 15
  Assert($prom-match'http_server_requests_seconds') 'HTTP Prometheus metrics missing';Assert($prom-match'jobpilot_ai_calls') 'JobPilot AI metric missing';Assert($prom-match'jobpilot_operational_runs') 'Operational run metric missing'
  $overview=Api 'GET' '/api/v1/operations/overview' $headers
  Assert($overview.data.health.backend-eq'UP') 'Backend health mismatch';Assert($overview.data.health.mysql-eq'UP') 'MySQL health mismatch';Assert($overview.data.health.redis-eq'UP') 'Redis health mismatch';Assert($overview.data.health.milvus-eq'UP') 'Milvus health mismatch'
  Assert((-not$overview.data.observability.otlpEnabled)) 'OTLP must default disabled';Assert($overview.data.safety.externalMessagesSent-eq0) 'External messages were detected';Assert($overview.data.safety.externalSubmissions-eq0) 'External submissions were detected';Assert($overview.data.safety.automaticOfferDecisions-eq0) 'Automatic offer decisions were detected'
  $before=Api 'GET' '/api/v1/operations/ai-budget' $headers
  $budget=Api 'PUT' '/api/v1/operations/ai-budget' $headers @{version=$before.data.version;dailyTokenLimit=100000;monthlyTokenLimit=2000000;dailyCostLimit=10;monthlyCostLimit=200;currency='USD';warningThresholdPercent=80;enabled=$true}
  Assert($budget.data.enabled) 'AI budget was not enabled';Assert($budget.data.status-in@('OK','WARNING','EXCEEDED')) 'Budget state is invalid'
  $budgetCurrent=Api 'PUT' '/api/v1/operations/ai-budget' $headers @{version=$budget.data.version;dailyTokenLimit=100000;monthlyTokenLimit=2000000;dailyCostLimit=10;monthlyCostLimit=200;currency='USD';warningThresholdPercent=80;enabled=$true}
  Expect 409 {Api 'PUT' '/api/v1/operations/ai-budget' $headers @{version=$budget.data.version;dailyTokenLimit=1;currency='USD';warningThresholdPercent=80;enabled=$true}} 'Budget optimistic locking'
  Expect 400 {Api 'PUT' '/api/v1/operations/ai-budget' $headers @{version=$budgetCurrent.data.version;currency='USD';warningThresholdPercent=80;enabled=$true}} 'Budget missing limit validation'
  $suffix=[Guid]::NewGuid().ToString('N');$key="phase11-smoke-$suffix";$runPayload=@{runType='LOAD_TEST';status='SUCCEEDED';batchId="smoke-$suffix";scope='USER';startedAt=(Get-Date).ToUniversalTime().AddSeconds(-2).ToString('yyyy-MM-ddTHH:mm:ss');finishedAt=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss');metrics=@{requests=3;errors=0};summary='Phase 11 API smoke record';artifactManifestPath='reports/phase11/smoke.json';artifactSha256=('a'*64);createdBy='SCRIPT'}
  $runHeaders=$headers.Clone();$runHeaders['Idempotency-Key']=$key;$run=Api 'POST' '/api/v1/operations/runs' $runHeaders $runPayload;$replay=Api 'POST' '/api/v1/operations/runs' $runHeaders $runPayload
  Assert($run.data.id-eq$replay.data.id) 'Operational run idempotency failed'
  $different=$runPayload.Clone();$different.summary='Different safe summary';Expect 409 {Api 'POST' '/api/v1/operations/runs' $runHeaders $different} 'Operational run idempotency conflict'
  Expect 404 {Api 'GET' "/api/v1/operations/runs/$($run.data.id)" $intruderHeaders} 'Operational run ownership'
  $badHeaders=$headers.Clone();$badHeaders['Idempotency-Key']="phase11-bad-$suffix";$bad=$runPayload.Clone();$bad.batchId="bad-$suffix";$bad.artifactManifestPath='../outside.json';Expect 400 {Api 'POST' '/api/v1/operations/runs' $badHeaders $bad} 'Artifact traversal validation'
  $usage=Api 'GET' '/api/v1/operations/ai-usage' $headers;Assert($null-ne$usage.data.providerStatus.circuitBreaker) 'Provider circuit breaker state missing'
  $allRuns=Api 'GET' '/api/v1/operations/runs?type=LOAD_TEST' $headers;Assert(@($allRuns.data|Where-Object{$_.id-eq$run.data.id}).Count-eq1) 'Operational run list missing record'
  [ordered]@{budgetId=$budgetCurrent.data.id;runId=$run.data.id}|ConvertTo-Json|Set-Content -LiteralPath $statePath -Encoding utf8
  [pscustomobject]@{status='PASS';mode='FULL';httpCount=$script:httpCount;trace=$true;prometheus=$true;budget=$budgetCurrent.data.status;operations=1;safetyExternalActions=0}|ConvertTo-Json -Compress
}finally{Pop-Location;$login=$null;$intruder=$null}
