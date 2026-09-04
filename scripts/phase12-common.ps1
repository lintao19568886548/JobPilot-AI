$script:Phase12ReleaseVersion = '0.14.0'

. (Join-Path $PSScriptRoot 'phase11-common.ps1')

function Get-Phase12ProjectRoot {
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
}

function Get-Phase12ComposeArguments([string]$ProjectRoot) {
    return @(
        '-f', (Join-Path $ProjectRoot 'docker-compose.yml'),
        '-f', (Join-Path $ProjectRoot 'deploy\docker-compose.release.yml'),
        '--profile', 'ai'
    )
}

function Assert-Phase12([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Get-Phase12Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Get-Phase12RelativePath([string]$ProjectRoot, [string]$Path) {
    $root = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\') + '\'
    $full = [IO.Path]::GetFullPath($Path)
    Assert-Phase12 ($full.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) 'Artifact must remain inside the project directory'
    return $full.Substring($root.Length).Replace('\', '/')
}

function Get-Phase12RequiredEnvironmentNames {
    return @('MYSQL_DATABASE','MYSQL_USERNAME','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD','JWT_SECRET','MINIO_ROOT_USER','MINIO_ROOT_PASSWORD')
}

function Get-Phase12SecretEnvironmentNames {
    return @(
        'MYSQL_PASSWORD',
        'MYSQL_ROOT_PASSWORD',
        'JWT_SECRET',
        'BOOTSTRAP_USER_PASSWORD',
        'AI_SERVICE_INTERNAL_TOKEN',
        'LLM_API_KEY',
        'MINIO_ROOT_PASSWORD',
        'AUTOMATION_WORKER_TOKEN'
    )
}

function Test-Phase12SecretText([string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return $false }
    foreach ($name in (Get-Phase12SecretEnvironmentNames)) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value.Length -ge 6 -and $Text.Contains($value)) { return $true }
    }
    return $false
}

function Wait-Phase12Url([string]$Url, [int]$TimeoutSeconds = 180) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return }
        } catch { }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Url"
}
