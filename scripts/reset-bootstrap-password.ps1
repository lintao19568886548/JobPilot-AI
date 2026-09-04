param()

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$envPath = Join-Path $projectRoot ".env"

function Read-LocalEnvValue([string]$Name) {
    $line = Get-Content -LiteralPath $envPath |
        Where-Object { $_ -match ("^\s*" + [regex]::Escape($Name) + "\s*=") } |
        Select-Object -Last 1
    if (-not $line) {
        throw "Missing required local configuration: $Name"
    }
    $value = ($line -split "=", 2)[1].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    return $value
}

$loginName = Read-LocalEnvValue "BOOTSTRAP_USER_USERNAME"
$loginEmail = Read-LocalEnvValue "BOOTSTRAP_USER_EMAIL"
$newPassword = Read-LocalEnvValue "BOOTSTRAP_USER_PASSWORD"
if ($loginName -notmatch "^[A-Za-z0-9_.@+-]{1,254}$") {
    throw "Bootstrap username contains unsupported characters"
}
if ($loginEmail -notmatch "^[A-Za-z0-9_.@+-]{1,254}$") {
    throw "Bootstrap email contains unsupported characters"
}
if ($newPassword.Length -lt 10) {
    throw "Bootstrap password must contain at least 10 characters"
}

$cryptoJar = Get-ChildItem -LiteralPath "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto" `
    -Recurse -Filter "spring-security-crypto-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$jclJar = Get-ChildItem -LiteralPath "$env:USERPROFILE\.m2\repository\org\springframework\spring-jcl" `
    -Recurse -Filter "spring-jcl-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $cryptoJar -or -not $jclJar) {
    throw "Required Spring Security runtime is unavailable"
}

$env:JOBPILOT_RESET_PASSWORD = $newPassword
try {
    $bcryptHash = ((& java --class-path ($cryptoJar.FullName + ";" + $jclJar.FullName) `
            (Join-Path $PSScriptRoot "GenerateBcrypt.java")) -join "").Trim()
} finally {
    Remove-Item Env:JOBPILOT_RESET_PASSWORD -ErrorAction SilentlyContinue
}
if ($LASTEXITCODE -ne 0 -or $bcryptHash -notmatch '^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$') {
    throw "BCrypt hash generation failed"
}

Push-Location $projectRoot
try {
    $identitySql = "SELECT id,username FROM users WHERE deleted_at IS NULL AND status='ACTIVE' " +
        "AND (username='$loginName' OR email='$loginEmail') ORDER BY (username='$loginName') DESC;"
    $identityRows = @($identitySql | docker compose exec -T mysql sh -lc `
            'MYSQL_PWD="$MYSQL_PASSWORD" mysql -B -N -u"$MYSQL_USER" "$MYSQL_DATABASE"')
    if ($identityRows.Count -ne 1) {
        throw "Expected exactly one active user matching the configured username or email, found $($identityRows.Count)"
    }
    $identityParts = $identityRows[0] -split "`t", 2
    $targetUserId = $identityParts[0]
    $resolvedLoginName = $identityParts[1]
    if ($targetUserId -notmatch "^\d+$" -or -not $resolvedLoginName) {
        throw "Resolved user identity is invalid"
    }

    $updateSql = "UPDATE users SET password_hash='$bcryptHash', updated_at=UTC_TIMESTAMP(3) " +
        "WHERE id=$targetUserId AND deleted_at IS NULL; " +
        "UPDATE refresh_tokens rt JOIN users u ON u.id=rt.user_id " +
        "SET rt.revoked_at=COALESCE(rt.revoked_at,UTC_TIMESTAMP(3)), rt.updated_at=UTC_TIMESTAMP(3) " +
        "WHERE u.id=$targetUserId AND rt.deleted_at IS NULL;"
    $null = $updateSql | docker compose exec -T mysql sh -lc `
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) {
        throw "Database password update failed"
    }

    $loginResponse = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8088/api/v1/auth/login" `
        -ContentType "application/json" `
        -Body (@{ login = $resolvedLoginName; password = $newPassword } | ConvertTo-Json -Compress) `
        -TimeoutSec 20
    if (-not $loginResponse.data.accessToken -or -not $loginResponse.data.refreshToken) {
        throw "Local login verification failed"
    }
    [pscustomobject]@{
        passwordReset = "PASS"
        matchedUsers = 1
        loginIdentifier = "BOOTSTRAP_USER_EMAIL"
        oldRefreshTokensRevoked = $true
        loginVerification = "PASS"
    } | ConvertTo-Json -Compress
} finally {
    Pop-Location
    $newPassword = $null
    $bcryptHash = $null
}
