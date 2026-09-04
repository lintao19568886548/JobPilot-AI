param([string]$Version = '0.12.0')

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    Assert-Phase12 ($Version -match '^0\.12\.\d+$') 'Phase 12 release version must match 0.12.x'
    $env:JOBPILOT_RELEASE_VERSION = $Version
    $releaseRoot = Join-Path $projectRoot "releases\jobpilot-ai-$Version"
    if (Test-Path -LiteralPath $releaseRoot) { Remove-Item -LiteralPath $releaseRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null

    $copies = @(
        @{source='docker-compose.yml';target='docker-compose.yml'},
        @{source='.env.example';target='.env.example'},
        @{source='deploy';target='deploy'},
        @{source='backend\target\jobpilot-backend-0.1.0-SNAPSHOT.jar';target='artifacts\jobpilot-backend.jar'},
        @{source='frontend\dist';target='artifacts\frontend'},
        @{source='ai-service\app';target='artifacts\ai-service\app'},
        @{source='ai-service\requirements.txt';target='artifacts\ai-service\requirements.txt'},
        @{source='docs\PHASE12_RELEASE_RUNBOOK.md';target='docs\PHASE12_RELEASE_RUNBOOK.md'},
        @{source='docs\PHASE12_TEST_REPORT.md';target='docs\PHASE12_TEST_REPORT.md'},
        @{source='docs\PHASE12_FINAL_REPORT.md';target='docs\PHASE12_FINAL_REPORT.md'}
    )
    foreach ($copy in $copies) {
        $source = Join-Path $projectRoot $copy.source
        Assert-Phase12 (Test-Path -LiteralPath $source) "Missing release input: $($copy.source)"
        $target = Join-Path $releaseRoot $copy.target
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $target -Recurse -Force
    }
    $scriptTarget = Join-Path $releaseRoot 'scripts'
    New-Item -ItemType Directory -Path $scriptTarget -Force | Out-Null
    Get-ChildItem (Join-Path $projectRoot 'scripts') -File | Where-Object Name -like 'phase12-*.ps1' | Copy-Item -Destination $scriptTarget

    $images = @()
    foreach ($name in @('backend','ai-service','frontend')) {
        $tag = "jobpilot/$name`:$Version"
        $inspect = & docker image inspect $tag | ConvertFrom-Json
        Assert-Phase12 (@($inspect).Count -eq 1) "Missing release image: $tag"
        $images += [ordered]@{name=$name;tag=$tag;imageId=$inspect[0].Id;created=$inspect[0].Created;user=$inspect[0].Config.User}
    }

    $dbFacts = ("SELECT CONCAT((SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1),',',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()));" | docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u"$MYSQL_USER" "$MYSQL_DATABASE"').Trim().Split(',')
    Assert-Phase12 ($dbFacts.Count -eq 2) 'Unable to read database release facts'

    $latestBackup = Get-ChildItem (Join-Path $projectRoot 'backups') -Filter manifest.json -Recurse -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    Assert-Phase12 ($null -ne $latestBackup) 'A successful Phase 11 backup manifest is required before release'

    $artifacts = @()
    Get-ChildItem $releaseRoot -Recurse -File | Sort-Object FullName | ForEach-Object {
        $artifacts += [ordered]@{path=$_.FullName.Substring($releaseRoot.Length+1).Replace('\','/');bytes=$_.Length;sha256=Get-Phase12Sha256 $_.FullName}
    }
    $gitLines = @(git status --short)
    $manifest = [ordered]@{
        schemaVersion='phase12-release-v1'
        releaseVersion=$Version
        createdAtUtc=[DateTime]::UtcNow.ToString('o')
        sourceGitStatus=if($gitLines.Count -eq 0){'CLEAN'}elseif((git ls-files|Measure-Object).Count-eq0){'UNTRACKED_BASELINE'}else{'DIRTY'}
        flywayVersion=[int]$dbFacts[0]
        databaseTableCount=[int]$dbFacts[1]
        images=$images
        artifacts=$artifacts
        backup=[ordered]@{manifestPath=Get-Phase12RelativePath $projectRoot $latestBackup.FullName;sha256=Get-Phase12Sha256 $latestBackup.FullName}
        safetyBoundary=[ordered]@{externalMessages=$false;externalSubmissions=$false;externalMutations=$false;automaticOfferDecisions=$false;destructiveDuplicateDeletes=$false;userConfirmationRequired=$true}
    }
    $manifestPath = Join-Path $releaseRoot 'manifest.json'
    $manifestJson = $manifest | ConvertTo-Json -Depth 12
    Assert-Phase12 (-not (Test-Phase12SecretText $manifestJson)) 'Release manifest contains a secret value'
    Set-Content -LiteralPath $manifestPath -Value $manifestJson -Encoding utf8

    foreach ($file in Get-ChildItem $releaseRoot -Recurse -File | Where-Object Extension -in @('.json','.yml','.yaml','.conf','.ps1','.txt','.example')) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        Assert-Phase12 (-not (Test-Phase12SecretText $text)) "Release text artifact contains a secret: $($file.Name)"
    }

    $archivePath = Join-Path $projectRoot "releases\jobpilot-ai-$Version.zip"
    if (Test-Path -LiteralPath $archivePath) { Remove-Item -LiteralPath $archivePath -Force }
    Compress-Archive -Path $releaseRoot -DestinationPath $archivePath -CompressionLevel Optimal
    $archiveHash = Get-Phase12Sha256 $archivePath
    Set-Content -LiteralPath "$archivePath.sha256" -Value "$archiveHash  $([IO.Path]::GetFileName($archivePath))" -Encoding ascii
    [pscustomobject]@{status='PASS';releaseVersion=$Version;manifest=Get-Phase12RelativePath $projectRoot $manifestPath;artifacts=$artifacts.Count;images=3;archive=Get-Phase12RelativePath $projectRoot $archivePath;archiveSha256=$archiveHash;secretCheck='PASS'} | ConvertTo-Json -Compress
} finally { Pop-Location }
