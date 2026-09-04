param([switch]$Runtime)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $missing = @()
    foreach ($name in (Get-Phase12RequiredEnvironmentNames)) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) { $missing += $name }
    }
    Assert-Phase12 ($missing.Count -eq 0) ("Missing required environment variables: " + ($missing -join ', '))
    Assert-Phase12 ($env:JWT_SECRET.Length -ge 32) 'JWT_SECRET must contain at least 32 characters'

    $tools = [ordered]@{}
    foreach ($name in @('docker','git','java','node','npm')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        Assert-Phase12 ($null -ne $command) "$name is unavailable"
        $tools[$name] = $command.Source
    }
    docker info --format '{{.ServerVersion}}' | Out-Null
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Docker Engine is unavailable'

    $compose = Get-Phase12ComposeArguments $projectRoot
    & docker compose @compose config --quiet
    Assert-Phase12 ($LASTEXITCODE -eq 0) 'Release Compose configuration is invalid'

    $drive = Get-PSDrive -Name ([IO.Path]::GetPathRoot($projectRoot).Substring(0,1))
    Assert-Phase12 ($drive.Free -gt 10GB) 'At least 10 GiB free disk space is required for the release build'

    $bindings = & docker compose @compose config --format json | ConvertFrom-Json
    foreach ($serviceName in @('mysql','redis','milvus','backend','ai-service','frontend')) {
        $service = $bindings.services.$serviceName
        Assert-Phase12 ($null -ne $service) "Missing release service: $serviceName"
        foreach ($port in @($service.ports)) {
            Assert-Phase12 ($port.host_ip -eq '127.0.0.1') "$serviceName must bind only to 127.0.0.1"
        }
    }
    foreach ($serviceName in @('etcd','minio')) {
        Assert-Phase12 ($null -eq $bindings.services.$serviceName.ports) "$serviceName must not publish host ports"
    }

    if ($Runtime) {
        foreach ($serviceName in @('backend','ai-service','frontend')) {
            $containerId = (& docker compose @compose ps -q $serviceName).Trim()
            Assert-Phase12 (-not [string]::IsNullOrWhiteSpace($containerId)) "$serviceName container is not running"
            $inspect = & docker inspect $containerId | ConvertFrom-Json
            Assert-Phase12 ($inspect[0].State.Running) "$serviceName container is not running"
            Assert-Phase12 ($inspect[0].Config.User -notmatch '^(|0|root)(:|$)') "$serviceName is running as root"
            Assert-Phase12 ($inspect[0].HostConfig.ReadonlyRootfs) "$serviceName root filesystem is not read-only"
        }
    }

    [pscustomobject]@{
        status = 'PASS'
        mode = if ($Runtime) { 'RUNTIME' } else { 'PREFLIGHT' }
        releaseVersion = $script:Phase12ReleaseVersion
        requiredEnvironment = 'PRESENT'
        compose = 'VALID'
        loopbackBindings = $true
        diskFreeGiB = [math]::Round($drive.Free / 1GB, 1)
    } | ConvertTo-Json -Compress
} finally { Pop-Location }
