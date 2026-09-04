param([switch]$SkipApiRecord)

$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'phase11-common.ps1')
Import-JobPilotEnv $projectRoot
$started=[DateTime]::UtcNow
$batchId="p11-backup-$($started.ToString('yyyyMMddTHHmmssZ'))-$([Guid]::NewGuid().ToString('N').Substring(0,8))"
$backupRoot=Join-Path $projectRoot "backups\$batchId"
New-Item -ItemType Directory -Path $backupRoot -Force|Out-Null
$status='FAILED';$summary='Backup did not complete';$metrics=@{};$manifestPath=Join-Path $backupRoot 'manifest.json';$artifactHash=$null
Push-Location $projectRoot
try{
  $mysql=("SELECT 1;"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u"$MYSQL_USER" "$MYSQL_DATABASE"').Trim();if($mysql-ne'1'){throw 'MySQL preflight failed'}
  if(-not((docker compose exec -T redis redis-cli ping).Trim()-eq'PONG')){throw 'Redis preflight failed'}
  docker compose exec -T milvus curl -fsS http://127.0.0.1:9091/healthz|Out-Null;if($LASTEXITCODE-ne0){throw 'Milvus preflight failed'}

  $dumpPath=Join-Path $backupRoot 'mysql.sql'
  docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u"$MYSQL_USER" --single-transaction --quick --routines --triggers --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"' | Set-Content -LiteralPath $dumpPath -Encoding utf8
  if($LASTEXITCODE-ne0-or(Get-Item $dumpPath).Length-lt1024){throw 'MySQL logical backup failed'}
  $dbFacts=("SELECT CONCAT((SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1),',',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()));"|docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u"$MYSQL_USER" "$MYSQL_DATABASE"').Trim()-split','

  $filesPath=Join-Path $backupRoot 'files.zip';$uploads=Join-Path $projectRoot 'uploads'
  if(Test-Path -LiteralPath $uploads){Compress-Archive -Path (Join-Path $uploads '*') -DestinationPath $filesPath -CompressionLevel Optimal -Force}else{Compress-Archive -Path (Join-Path $projectRoot '.editorconfig') -DestinationPath $filesPath -CompressionLevel Optimal -Force}
  $fileCount=if(Test-Path -LiteralPath $uploads){@(Get-ChildItem -LiteralPath $uploads -Recurse -File).Count}else{0}

  $milvusPath=Join-Path $backupRoot 'milvus.json';$python=Join-Path $projectRoot 'ai-service\.venv\Scripts\python.exe'
  $milvusResult=(& $python (Join-Path $PSScriptRoot 'phase11_milvus.py') export --uri $env:MILVUS_URI --file $milvusPath|ConvertFrom-Json)
  $artifacts=@();foreach($file in @($dumpPath,$filesPath,$milvusPath)){$artifacts+=[ordered]@{path=[IO.Path]::GetFileName($file);bytes=(Get-Item $file).Length;sha256=Get-FileSha256 $file}}
  $manifest=[ordered]@{schemaVersion='phase11-backup-v1';batchId=$batchId;createdAtUtc=$started.ToString('o');sourceDatabase=$env:MYSQL_DATABASE;flywayVersion=[int]$dbFacts[0];tableCount=[int]$dbFacts[1];fileCount=$fileCount;milvusCollections=[int]$milvusResult.collections;milvusRows=[int]$milvusResult.rows;artifacts=$artifacts}
  $manifest|ConvertTo-Json -Depth 10|Set-Content -LiteralPath $manifestPath -Encoding utf8
  $artifactHash=Get-FileSha256 $manifestPath;$metrics=@{flywayVersion=[int]$dbFacts[0];tables=[int]$dbFacts[1];files=$fileCount;milvusCollections=[int]$milvusResult.collections;milvusRows=[int]$milvusResult.rows;artifactCount=$artifacts.Count}
  $status='SUCCEEDED';$summary="Consistent logical backup created with $($artifacts.Count) integrity-checked artifacts"
  if(!$SkipApiRecord){$finished=[DateTime]::UtcNow;$null=Write-OperationalRun $projectRoot @{runType='BACKUP';status=$status;batchId=$batchId;scope='SYSTEM';startedAt=$started.ToString('o');finishedAt=$finished.ToString('o');metrics=$metrics;summary=$summary;artifactManifestPath=(Get-RelativeArtifactPath $projectRoot $manifestPath);artifactSha256=$artifactHash;rpoSeconds=0;rtoSeconds=[long]($finished-$started).TotalSeconds;createdBy='SCRIPT'} "p11-run-$batchId"}
  [pscustomobject]@{status='PASS';batchId=$batchId;manifest=(Get-RelativeArtifactPath $projectRoot $manifestPath);sha256=$artifactHash;metrics=$metrics}|ConvertTo-Json -Depth 8 -Compress
}catch{
  if(Test-Path -LiteralPath $manifestPath){$artifactHash=Get-FileSha256 $manifestPath}
  if(!$SkipApiRecord){try{$finished=[DateTime]::UtcNow;$null=Write-OperationalRun $projectRoot @{runType='BACKUP';status='FAILED';batchId=$batchId;scope='SYSTEM';startedAt=$started.ToString('o');finishedAt=$finished.ToString('o');metrics=$metrics;summary='Backup failed; inspect local operator output';artifactManifestPath=$null;artifactSha256=$null;rpoSeconds=$null;rtoSeconds=[long]($finished-$started).TotalSeconds;errorCode='BACKUP_FAILED';createdBy='SCRIPT'} "p11-run-$batchId"}catch{}}
  throw
}finally{Pop-Location}
