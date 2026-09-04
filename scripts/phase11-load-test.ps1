param([int]$Concurrency=5,[int]$RequestsPerEndpoint=30,[int]$Warmup=3)

$ErrorActionPreference='Stop';$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'phase11-common.ps1');Import-JobPilotEnv $projectRoot
$started=[DateTime]::UtcNow;$batchId="p11-load-$([Guid]::NewGuid().ToString('N').Substring(0,12))";$reportPath=Join-Path $projectRoot "reports\phase11\$batchId.json"
try{
  $env:JOBPILOT_LOAD_TOKEN=Get-Phase11AccessToken $projectRoot;$env:JOBPILOT_LOAD_BASE_URL='http://127.0.0.1:8088'
  $json=& node (Join-Path $PSScriptRoot 'phase11-load-test.mjs') $Concurrency $RequestsPerEndpoint $Warmup $reportPath;if($LASTEXITCODE-ne0){throw 'Load test thresholds failed'};$report=$json|ConvertFrom-Json
  $finished=[DateTime]::UtcNow;$hash=Get-FileSha256 $reportPath;$metrics=@{concurrency=$Concurrency;requestsPerEndpoint=$RequestsPerEndpoint;warmup=$Warmup;results=$report.results}
  $null=Write-OperationalRun $projectRoot @{runType='LOAD_TEST';status='SUCCEEDED';batchId=$batchId;scope='SYSTEM';startedAt=$started.ToString('o');finishedAt=$finished.ToString('o');metrics=$metrics;summary='Read-only API load baseline passed with zero errors and P95 below 1000 ms';artifactManifestPath=(Get-RelativeArtifactPath $projectRoot $reportPath);artifactSha256=$hash;rpoSeconds=$null;rtoSeconds=[long]($finished-$started).TotalSeconds;createdBy='SCRIPT'} "p11-run-$batchId"
  [pscustomobject]@{status='PASS';batchId=$batchId;report=(Get-RelativeArtifactPath $projectRoot $reportPath);results=$report.results}|ConvertTo-Json -Depth 10 -Compress
}finally{Remove-Item Env:JOBPILOT_LOAD_TOKEN -ErrorAction SilentlyContinue}
