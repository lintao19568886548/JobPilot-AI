$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$database='jobpilot_phase9_migration_check'
$port=18089
function Load-Env { Get-Content -LiteralPath (Join-Path $projectRoot '.env')|ForEach-Object{$line=$_.Trim();if(!$line-or$line.StartsWith('#')){return};$i=$line.IndexOf('=');if($i-lt1){return};$name=$line.Substring(0,$i).Trim();$value=$line.Substring($i+1).Trim();if(($value.StartsWith('"')-and$value.EndsWith('"'))-or($value.StartsWith("'")-and$value.EndsWith("'"))){$value=$value.Substring(1,$value.Length-2)};[Environment]::SetEnvironmentVariable($name,$value,'Process')}}
Push-Location $projectRoot
$process=$null
try{
  Load-Env
  $dbUser=$env:MYSQL_USERNAME;if($dbUser-notmatch '^[A-Za-z0-9_]+$'){throw 'Unsafe MySQL username for isolated migration check'}
  "DROP DATABASE IF EXISTS $database; CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON $database.* TO '$dbUser'@'%';"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
  if($LASTEXITCODE-ne0){throw 'Unable to create isolated migration database'}
  $env:MYSQL_DATABASE=$database;$env:SERVER_PORT=[string]$port;$env:BOOTSTRAP_USER_ENABLED='false'
  $jar=Get-ChildItem -LiteralPath (Join-Path $projectRoot 'backend\target') -Filter 'jobpilot-backend-*.jar'|Where-Object{$_.Name-notlike'*.original'}|Sort-Object LastWriteTime -Descending|Select-Object -First 1
  if(!$jar){throw 'Backend JAR is missing'}
  $runtime=Join-Path $projectRoot 'runtime';New-Item -ItemType Directory -Path $runtime -Force|Out-Null
  $stdout=Join-Path $runtime 'phase9-migration-check.out.log';$stderr=Join-Path $runtime 'phase9-migration-check.err.log'
  $process=Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',('"'+$jar.FullName+'"')) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
  $ready=$false;for($i=0;$i-lt60;$i++){Start-Sleep -Milliseconds 500;try{if((Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2).status-eq'UP'){$ready=$true;break}}catch{};if($process.HasExited){break}}
  if(!$ready){$tail=(Get-Content $stdout -Tail 30 -ErrorAction SilentlyContinue)-join[Environment]::NewLine;throw "Empty-schema backend did not start. Safe log tail: $tail"}
  $result=("SELECT CONCAT((SELECT COUNT(*) FROM flyway_schema_history WHERE success=1),',',(SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1),',',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database'));"|docker compose exec -T mysql sh -lc "MYSQL_PWD=`"`$MYSQL_PASSWORD`" mysql -N -u`"`$MYSQL_USER`" '$database'").Trim()
  $parts=$result-split',';if($parts.Count-ne3-or$parts[0]-ne'9'-or$parts[1]-ne'9'){throw "Unexpected migration result: $result"}
  [pscustomobject]@{status='PASS';mode='EMPTY_SCHEMA';migrations=[int]$parts[0];version=[int]$parts[1];tables=[int]$parts[2]}|ConvertTo-Json -Compress
}finally{
  if($process-and-not$process.HasExited){Stop-Process -Id $process.Id -Force;Wait-Process -Id $process.Id -ErrorAction SilentlyContinue}
  if($dbUser){"REVOKE ALL PRIVILEGES ON $database.* FROM '$dbUser'@'%'; DROP DATABASE IF EXISTS $database;"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'|Out-Null}else{"DROP DATABASE IF EXISTS $database;"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'|Out-Null}
  Pop-Location
}
