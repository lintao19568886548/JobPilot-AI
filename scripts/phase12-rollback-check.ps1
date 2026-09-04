param([string]$ManifestPath)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    if ([string]::IsNullOrWhiteSpace($ManifestPath)) { $ManifestPath = Join-Path $projectRoot "releases\jobpilot-ai-$($script:Phase12ReleaseVersion)\manifest.json" }
    $resolved = (Resolve-Path -LiteralPath $ManifestPath).Path
    Assert-Phase12 ($resolved.StartsWith((Join-Path $projectRoot 'releases'), [StringComparison]::OrdinalIgnoreCase)) 'Rollback manifest must be inside releases/'
    $manifest = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
    Assert-Phase12 ($manifest.schemaVersion -eq 'phase12-release-v1') 'Unsupported release manifest schema'
    Assert-Phase12 ($manifest.releaseVersion -match '^0\.12\.\d+$') 'Invalid release version'
    foreach ($artifact in $manifest.artifacts) {
        $path = Join-Path (Split-Path -Parent $resolved) $artifact.path
        Assert-Phase12 (Test-Path -LiteralPath $path -PathType Leaf) "Rollback artifact is missing: $($artifact.path)"
        Assert-Phase12 ((Get-Phase12Sha256 $path) -eq $artifact.sha256) "Rollback artifact hash mismatch: $($artifact.path)"
    }
    foreach ($image in $manifest.images) {
        $actual = (& docker image inspect --format '{{.Id}}' $image.tag).Trim()
        Assert-Phase12 ($actual -eq $image.imageId) "Rollback image ID mismatch: $($image.tag)"
        Assert-Phase12 ($image.user -notmatch '^(|0|root)(:|$)') "Rollback image is configured as root: $($image.tag)"
    }
    $backupPath = Join-Path $projectRoot $manifest.backup.manifestPath
    Assert-Phase12 (Test-Path -LiteralPath $backupPath -PathType Leaf) 'Rollback backup manifest is missing'
    Assert-Phase12 ((Get-Phase12Sha256 $backupPath) -eq $manifest.backup.sha256) 'Rollback backup manifest hash mismatch'
    $currentFlyway = [int]("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1;" | docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -u"$MYSQL_USER" "$MYSQL_DATABASE"').Trim()
    Assert-Phase12 ($currentFlyway -eq [int]$manifest.flywayVersion) 'Database schema differs from the release manifest; automatic application rollback is blocked'
    [pscustomobject]@{status='PASS';mode='DRY_RUN';releaseVersion=$manifest.releaseVersion;artifacts=@($manifest.artifacts).Count;images=@($manifest.images).Count;flywayCompatible=$true;databaseChanged=$false;containersChanged=$false} | ConvertTo-Json -Compress
} finally { Pop-Location }
