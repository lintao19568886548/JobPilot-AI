$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'phase11-common.ps1')
Import-JobPilotEnv $projectRoot
$port=18092;$process=$null;$runtime=Join-Path $projectRoot 'runtime';New-Item -ItemType Directory -Path $runtime -Force|Out-Null
$stdout=Join-Path $runtime 'phase11-observability.json.log';$stderr=Join-Path $runtime 'phase11-observability.err.log'
$jar=Get-ChildItem (Join-Path $projectRoot 'backend\target') -Filter 'jobpilot-backend-*.jar'|Where-Object{$_.Name-notlike'*.original'}|Sort-Object LastWriteTime -Descending|Select-Object -First 1
if(!$jar){throw 'Backend JAR is missing'}
try{
  $env:BOOTSTRAP_USER_ENABLED='false';$env:AUTOMATION_CENTER_ENABLED='false';$env:OTEL_EXPORTER_OTLP_ENABLED='false'
  $process=Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',('"'+$jar.FullName+'"'),'--spring.profiles.active=observability',"--server.port=$port") -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
  $ready=$false;for($i=0;$i-lt90;$i++){Start-Sleep -Milliseconds 500;try{if((Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -Headers @{'X-Trace-Id'='phase11-json-log-check'} -TimeoutSec 2).status-eq'UP'){$ready=$true;break}}catch{};if($process.HasExited){break}}
  if(!$ready){throw "Structured logging backend did not start: $((Get-Content $stderr -Tail 20 -ErrorAction SilentlyContinue)-join' ')"}
  Start-Sleep -Milliseconds 500
  $lines=@(Get-Content -LiteralPath $stdout|Where-Object{$_.Trim().StartsWith('{')});$parsed=0;$invalid=0;$correlated=0
  foreach($line in $lines){try{$entry=$line|ConvertFrom-Json;$parsed++;if($entry.requestTraceId-eq'phase11-json-log-check'){$correlated++}}catch{$invalid++}}
  if($parsed-lt5-or$invalid-ne0){throw "ECS JSON logging validation failed: parsed=$parsed invalid=$invalid"}
  [pscustomobject]@{status='PASS';profile='observability';jsonLines=$parsed;invalidJsonLines=$invalid;requestTraceLines=$correlated;otlpEnabled=$false;log='runtime/phase11-observability.json.log'}|ConvertTo-Json -Compress
}finally{if($process-and-not$process.HasExited){Stop-Process -Id $process.Id -Force;Wait-Process -Id $process.Id -ErrorAction SilentlyContinue}}
