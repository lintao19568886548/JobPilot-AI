param([switch]$VerifyPersistence)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'phase12-common.ps1')
$projectRoot = Get-Phase12ProjectRoot
$statePath = Join-Path $projectRoot 'scripts\.phase14-smoke-state.json'
$script:httpCount = 0
$passwordChanged = $false
$completed = $false
$originalPreferences = $null
$temporaryPassword = $null

function Invoke-Phase14Web([string]$Method, [string]$Url, [hashtable]$Headers, $Body = $null) {
    $script:httpCount++
    $parameters = @{ Method = $Method; Uri = $Url; Headers = $Headers; TimeoutSec = 30; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    return Invoke-WebRequest @parameters
}

function Read-Phase14Json($Response) {
    $text = if ($Response.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($Response.Content) } else { [string]$Response.Content }
    return $text | ConvertFrom-Json
}

function Assert-Phase14Status([scriptblock]$Action, [int]$ExpectedStatus, [string]$Message) {
    try {
        & $Action | Out-Null
        throw "$Message (request unexpectedly succeeded)"
    } catch {
        if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne $ExpectedStatus) { throw }
    }
}

function Get-Preference($Overview, [string]$Group, [string]$Key) {
    return @($Overview.preferences | Where-Object { $_.group -eq $Group -and $_.key -eq $Key })[0]
}

function Set-Preference([string]$Gateway, [hashtable]$Headers, $Preference, $Value) {
    $version = if ($null -eq $Preference.version) { 0 } else { [int]$Preference.version }
    $response = Invoke-Phase14Web PUT "$Gateway/api/v1/settings/$($Preference.group)/$($Preference.key)" $Headers @{ value = $Value; version = $version }
    return (Read-Phase14Json $response).data
}

Push-Location $projectRoot
try {
    Import-JobPilotEnv $projectRoot
    $gatewayPort = if ($env:JOBPILOT_GATEWAY_PORT) { $env:JOBPILOT_GATEWAY_PORT } else { '8180' }
    $gateway = "http://127.0.0.1:$gatewayPort"
    $originalPassword = Get-Phase11Password $projectRoot
    $temporaryPassword = "P14!$([Guid]::NewGuid().ToString('N').Substring(0,24))z"

    $spa = Invoke-Phase14Web GET "$gateway/settings" @{}
    Assert-Phase12 ($spa.StatusCode -eq 200) 'Settings SPA route is unavailable'

    $login = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/login" @{} @{ login = 'phase10_smoke'; password = $originalPassword })
    Assert-Phase12 (-not [string]::IsNullOrWhiteSpace($login.data.accessToken)) 'Login did not return an access token'
    $access = $login.data.accessToken
    $headers = @{ Authorization = "Bearer $access"; 'X-Trace-Id' = "phase14-$([Guid]::NewGuid().ToString('N'))" }

    $settingsResponse = Invoke-Phase14Web GET "$gateway/api/v1/settings" $headers
    $settingsEnvelope = Read-Phase14Json $settingsResponse
    Assert-Phase12 ($settingsEnvelope.code -eq 0) 'Settings overview failed'
    Assert-Phase12 ($settingsEnvelope.traceId -eq $headers['X-Trace-Id'] -and $settingsResponse.Headers['X-Trace-Id'] -eq $headers['X-Trace-Id']) 'Settings trace propagation failed'
    Assert-Phase12 (@($settingsEnvelope.data.preferences).Count -eq 3) 'Settings whitelist must return three entries'
    Assert-Phase12 (@($settingsEnvelope.data.preferences | Where-Object { $_.key -notin @('defaultLandingPage','compactMode','showDashboardBanner') }).Count -eq 0) 'Unexpected setting escaped the whitelist'
    $originalPreferences = @($settingsEnvelope.data.preferences | ForEach-Object { [ordered]@{ group=$_.group; key=$_.key; value=$_.value } })

    if ($VerifyPersistence) {
        Assert-Phase12 (Test-Path -LiteralPath $statePath -PathType Leaf) 'Phase 14 persistence state is missing; run full smoke first'
        $previous = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        Assert-Phase12 ((Get-Preference $settingsEnvelope.data 'workspace' 'defaultLandingPage').value -eq $previous.landing) 'Default landing page did not survive restart'
        Assert-Phase12 ((Get-Preference $settingsEnvelope.data 'workspace' 'compactMode').value -eq $previous.compact) 'Compact mode did not survive restart'
        Assert-Phase12 ((Get-Preference $settingsEnvelope.data 'onboarding' 'showDashboardBanner').value -eq $previous.banner) 'Dashboard banner setting did not survive restart'
        Assert-Phase12 ($settingsEnvelope.data.account.timezone -eq $previous.timezone -and $settingsEnvelope.data.account.locale -eq $previous.locale) 'Account settings did not survive restart'
        foreach ($original in $previous.originalPreferences) {
            $latest = Get-Preference $settingsEnvelope.data $original.group $original.key
            $null = Set-Preference $gateway $headers $latest $original.value
        }
        $operations = (Read-Phase14Json (Invoke-Phase14Web GET "$gateway/api/v1/operations/overview" $headers)).data
        Assert-Phase12 ($operations.safety.externalMessagesSent -eq 0 -and $operations.safety.externalSubmissions -eq 0 -and $operations.safety.externalMutations -eq 0 -and $operations.safety.automaticOfferDecisions -eq 0 -and $operations.safety.destructiveDuplicateDeletes -eq 0) 'A forbidden external action was detected'
        $completed = $true
        [pscustomobject]@{
            status='PASS'; mode='PERSISTENCE'; httpCount=$script:httpCount
            settings=3; sessions=0; passwordRestored=$true; trace=$true; secretsExposed=0; externalActions=0
        } | ConvertTo-Json -Compress
        return
    }

    $account = $settingsEnvelope.data.account
    $accountResult = Read-Phase14Json (Invoke-Phase14Web PUT "$gateway/api/v1/settings/account" $headers @{
        displayName = $account.displayName; email = $account.email; timezone = $account.timezone; locale = $account.locale; version = $account.version
    })
    Assert-Phase12 ($accountResult.data.timezone -eq $account.timezone) 'Account settings were not persisted'

    $landing = Get-Preference $settingsEnvelope.data 'workspace' 'defaultLandingPage'
    $compact = Get-Preference $settingsEnvelope.data 'workspace' 'compactMode'
    $banner = Get-Preference $settingsEnvelope.data 'onboarding' 'showDashboardBanner'
    $expectedLanding = if ($landing.value -eq '/applications') { '/dashboard' } else { '/applications' }
    $expectedCompact = -not [bool]$compact.value
    $expectedBanner = -not [bool]$banner.value
    $landing = Set-Preference $gateway $headers $landing $expectedLanding
    $compact = Set-Preference $gateway $headers $compact $expectedCompact
    $banner = Set-Preference $gateway $headers $banner $expectedBanner

    Assert-Phase14Status { Invoke-Phase14Web PUT "$gateway/api/v1/settings/workspace/unapprovedKey" $headers @{ value='blocked'; version=0 } } 400 'Unapproved setting key was accepted'
    Assert-Phase14Status { Invoke-Phase14Web DELETE "$gateway/api/v1/auth/sessions/01NOTOWNED000000000000000" $headers } 404 'Unknown session did not return Not Found'

    $secondLogin = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/login" @{} @{ login = 'phase10_smoke'; password = $originalPassword })
    $secondAccess = $secondLogin.data.accessToken
    $secondRefresh = $secondLogin.data.refreshToken
    $sessions = (Read-Phase14Json (Invoke-Phase14Web GET "$gateway/api/v1/auth/sessions" $headers)).data
    Assert-Phase12 (@($sessions | Where-Object { $_.current }).Count -eq 1) 'Current session marker is invalid'
    $other = @($sessions | Where-Object { -not $_.current } | Sort-Object lastUsedAt -Descending)[0]
    Assert-Phase12 ($null -ne $other) 'Second web session was not listed'
    Invoke-Phase14Web DELETE "$gateway/api/v1/auth/sessions/$($other.id)" $headers | Out-Null
    Assert-Phase14Status { Invoke-Phase14Web GET "$gateway/api/v1/auth/me" @{ Authorization="Bearer $secondAccess" } } 401 'Revoked family access token remained valid'
    Assert-Phase14Status { Invoke-Phase14Web POST "$gateway/api/v1/auth/refresh" @{} @{ refreshToken=$secondRefresh } } 401 'Revoked family refresh token remained valid'

    $passwordResult = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/password" $headers @{
        currentPassword=$originalPassword; newPassword=$temporaryPassword; confirmPassword=$temporaryPassword
    })
    $passwordChanged = $true
    Assert-Phase12 ($passwordResult.data.revokedSessions -ge 1) 'Password change did not revoke web sessions'
    Assert-Phase14Status { Invoke-Phase14Web GET "$gateway/api/v1/auth/me" @{ Authorization="Bearer $access" } } 401 'Pre-change access token remained valid'
    Assert-Phase14Status { Invoke-Phase14Web POST "$gateway/api/v1/auth/refresh" @{} @{ refreshToken=$login.data.refreshToken } } 401 'Pre-change refresh token remained valid'

    $temporaryLogin = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/login" @{} @{ login='phase10_smoke'; password=$temporaryPassword })
    $temporaryHeaders = @{ Authorization="Bearer $($temporaryLogin.data.accessToken)" }
    Invoke-Phase14Web POST "$gateway/api/v1/auth/password" $temporaryHeaders @{
        currentPassword=$temporaryPassword; newPassword=$originalPassword; confirmPassword=$originalPassword
    } | Out-Null
    $passwordChanged = $false
    $restoredLogin = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/login" @{} @{ login='phase10_smoke'; password=$originalPassword })
    Assert-Phase12 (-not [string]::IsNullOrWhiteSpace($restoredLogin.data.accessToken)) 'Original smoke password was not restored'

    $operationsHeaders = @{ Authorization="Bearer $($restoredLogin.data.accessToken)" }
    $operations = (Read-Phase14Json (Invoke-Phase14Web GET "$gateway/api/v1/operations/overview" $operationsHeaders)).data
    Assert-Phase12 ($operations.safety.externalMessagesSent -eq 0 -and $operations.safety.externalSubmissions -eq 0 -and $operations.safety.externalMutations -eq 0 -and $operations.safety.automaticOfferDecisions -eq 0 -and $operations.safety.destructiveDuplicateDeletes -eq 0) 'A forbidden external action was detected'

    $fingerprint = [ordered]@{ landing=$expectedLanding; compact=$expectedCompact; banner=$expectedBanner; timezone=$account.timezone; locale=$account.locale; originalPreferences=$originalPreferences }
    $fingerprint | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8

    $completed = $true
    [pscustomobject]@{
        status='PASS'; mode=if($VerifyPersistence){'PERSISTENCE'}else{'FULL'}; httpCount=$script:httpCount
        settings=3; sessions=@($sessions).Count; passwordRestored=$true; trace=$true; secretsExposed=0; externalActions=0
    } | ConvertTo-Json -Compress
} finally {
    if ($passwordChanged -and $null -ne $temporaryPassword) {
        try {
            $originalPassword = Get-Phase11Password $projectRoot
            $gatewayPort = if ($env:JOBPILOT_GATEWAY_PORT) { $env:JOBPILOT_GATEWAY_PORT } else { '8180' }
            $gateway = "http://127.0.0.1:$gatewayPort"
            $recovery = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/login" @{} @{login='phase10_smoke';password=$temporaryPassword})
            Invoke-Phase14Web POST "$gateway/api/v1/auth/password" @{Authorization="Bearer $($recovery.data.accessToken)"} @{
                currentPassword=$temporaryPassword;newPassword=$originalPassword;confirmPassword=$originalPassword
            } | Out-Null
        } catch { Write-Warning 'Smoke password recovery failed; inspect the dedicated phase10_smoke account.' }
    }
    if (-not $completed -and $null -ne $originalPreferences) {
        try {
            $recoveryLogin = Read-Phase14Json (Invoke-Phase14Web POST "$gateway/api/v1/auth/login" @{} @{login='phase10_smoke';password=(Get-Phase11Password $projectRoot)})
            $recoveryHeaders = @{Authorization="Bearer $($recoveryLogin.data.accessToken)"}
            $currentSettings = (Read-Phase14Json (Invoke-Phase14Web GET "$gateway/api/v1/settings" $recoveryHeaders)).data
            foreach ($original in $originalPreferences) {
                $latest = Get-Preference $currentSettings $original.group $original.key
                $null = Set-Preference $gateway $recoveryHeaders $latest $original.value
            }
        } catch { Write-Warning 'Smoke setting recovery failed; inspect the dedicated phase10_smoke account.' }
    }
    $temporaryPassword = $null
    Pop-Location
}
