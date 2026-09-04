param([string]$ManifestPath)

$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'phase11-common.ps1')
Import-JobPilotEnv $projectRoot
if(!$ManifestPath){$latest=Get-ChildItem -LiteralPath (Join-Path $projectRoot 'backups') -Filter manifest.json -Recurse -File|Sort-Object LastWriteTime -Descending|Select-Object -First 1;if(!$latest){throw 'No Phase 11 backup manifest found'};$ManifestPath=$latest.FullName}
$manifestFile=(Resolve-Path -LiteralPath $ManifestPath).Path;$manifest=Get-Content -LiteralPath $manifestFile -Raw|ConvertFrom-Json
if($manifest.schemaVersion-ne'phase11-backup-v1'){throw 'Unsupported backup manifest'}
$backupRoot=Split-Path -Parent $manifestFile;$started=[DateTime]::UtcNow;$batchId="p11-restore-$([Guid]::NewGuid().ToString('N').Substring(0,12))"
$database="jobpilot_restore_$([Guid]::NewGuid().ToString('N').Substring(0,12))";if($database-eq$env:MYSQL_DATABASE){throw 'Restore drill may not target the active database'}
$runtimeRoot=Join-Path $projectRoot 'runtime\phase11-restore';New-Item -ItemType Directory -Path $runtimeRoot -Force|Out-Null;$fileTarget=Join-Path $runtimeRoot $batchId;New-Item -ItemType Directory -Path $fileTarget -Force|Out-Null
$status='FAILED';$metrics=@{};$dbCreated=$false
Push-Location $projectRoot
try{
  foreach($artifact in $manifest.artifacts){$path=Join-Path $backupRoot $artifact.path;if(!(Test-Path -LiteralPath $path)){throw "Backup artifact missing: $($artifact.path)"};if((Get-FileSha256 $path)-ne$artifact.sha256){throw "Backup artifact hash mismatch: $($artifact.path)"}}
  $dbUser=$env:MYSQL_USERNAME;if($dbUser-notmatch'^[A-Za-z0-9_]+$'){throw 'Unsafe MySQL username'}
  "CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON $database.* TO '$dbUser'@'%';"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot';if($LASTEXITCODE-ne0){throw 'Unable to create isolated restore database'};$dbCreated=$true
  Get-Content -LiteralPath (Join-Path $backupRoot 'mysql.sql') -Raw|docker compose exec -T mysql sh -lc "MYSQL_PWD=`"`$MYSQL_PASSWORD`" mysql -u`"`$MYSQL_USER`" '$database'";if($LASTEXITCODE-ne0){throw 'MySQL restore failed'}
  $dbResult=("SELECT CONCAT((SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1),',',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database'),',',(SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema='$database'));"|docker compose exec -T mysql sh -lc "MYSQL_PWD=`"`$MYSQL_PASSWORD`" mysql -N -u`"`$MYSQL_USER`" '$database'").Trim()-split','
  if([int]$dbResult[0]-ne[int]$manifest.flywayVersion-or[int]$dbResult[1]-ne[int]$manifest.tableCount-or[int]$dbResult[2]-lt1){throw 'Restored database facts do not match the manifest'}
  Expand-Archive -LiteralPath (Join-Path $backupRoot 'files.zip') -DestinationPath $fileTarget -Force
  $python=Join-Path $projectRoot 'ai-service\.venv\Scripts\python.exe';$prefix="jobpilot_restore_drill_$([Guid]::NewGuid().ToString('N').Substring(0,10))"
  $milvus=(& $python (Join-Path $PSScriptRoot 'phase11_milvus.py') restore-drill --uri $env:MILVUS_URI --file (Join-Path $backupRoot 'milvus.json') --prefix $prefix|ConvertFrom-Json)
  if([int]$milvus.rows-ne[int]$manifest.milvusRows){throw 'Restored Milvus row count does not match the manifest'}
  $finished=[DateTime]::UtcNow;$rpo=[long]([DateTime]::UtcNow-[DateTime]::Parse($manifest.createdAtUtc).ToUniversalTime()).TotalSeconds;$rto=[long]($finished-$started).TotalSeconds
  $metrics=@{flywayVersion=[int]$dbResult[0];tables=[int]$dbResult[1];foreignKeys=[int]$dbResult[2];files=[int]$manifest.fileCount;milvusCollections=[int]$milvus.collections;milvusRows=[int]$milvus.rows;hashesVerified=@($manifest.artifacts).Count}
  $null=Write-OperationalRun $projectRoot @{runType='RESTORE_DRILL';status='SUCCEEDED';batchId=$batchId;scope='SYSTEM';startedAt=$started.ToString('o');finishedAt=$finished.ToString('o');metrics=$metrics;summary='Isolated restore verified and temporary resources cleaned';artifactManifestPath=(Get-RelativeArtifactPath $projectRoot $manifestFile);artifactSha256=(Get-FileSha256 $manifestFile);rpoSeconds=$rpo;rtoSeconds=$rto;createdBy='SCRIPT'} "p11-run-$batchId"
  [pscustomobject]@{status='PASS';batchId=$batchId;rpoSeconds=$rpo;rtoSeconds=$rto;metrics=$metrics;productionDatabaseUntouched=$true}|ConvertTo-Json -Depth 8 -Compress
}catch{
  try{$finished=[DateTime]::UtcNow;$null=Write-OperationalRun $projectRoot @{runType='RESTORE_DRILL';status='FAILED';batchId=$batchId;scope='SYSTEM';startedAt=$started.ToString('o');finishedAt=$finished.ToString('o');metrics=$metrics;summary='Restore drill failed; inspect local operator output';artifactManifestPath=(Get-RelativeArtifactPath $projectRoot $manifestFile);artifactSha256=(Get-FileSha256 $manifestFile);rpoSeconds=$null;rtoSeconds=[long]($finished-$started).TotalSeconds;errorCode='RESTORE_DRILL_FAILED';createdBy='SCRIPT'} "p11-run-$batchId"}catch{}
  throw
}finally{
  if($dbCreated){"DROP DATABASE IF EXISTS $database; REVOKE IF EXISTS ALL PRIVILEGES ON $database.* FROM '$dbUser'@'%';"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'|Out-Null}
  $resolvedRuntime=(Resolve-Path -LiteralPath $runtimeRoot).Path;$resolvedTarget=(Resolve-Path -LiteralPath $fileTarget -ErrorAction SilentlyContinue).Path;if($resolvedTarget-and$resolvedTarget.StartsWith($resolvedRuntime,[StringComparison]::OrdinalIgnoreCase)){Remove-Item -LiteralPath $resolvedTarget -Recurse -Force}
  Pop-Location
}
