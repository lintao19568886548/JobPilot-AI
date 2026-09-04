$ErrorActionPreference = 'Stop'

function Import-JobPilotEnv([string]$ProjectRoot) {
  Get-Content -LiteralPath (Join-Path $ProjectRoot '.env') | ForEach-Object {
    $line=$_.Trim(); if(!$line -or $line.StartsWith('#')){return}
    $i=$line.IndexOf('='); if($i -lt 1){return}
    $name=$line.Substring(0,$i).Trim(); $value=$line.Substring($i+1).Trim()
    if(($value.StartsWith('"')-and$value.EndsWith('"'))-or($value.StartsWith("'")-and$value.EndsWith("'"))){$value=$value.Substring(1,$value.Length-2)}
    [Environment]::SetEnvironmentVariable($name,$value,'Process')
  }
}

function Get-Phase11Password([string]$ProjectRoot) {
  if(!$env:JWT_SECRET){Import-JobPilotEnv $ProjectRoot}
  $sha=[Security.Cryptography.SHA256]::Create()
  try{$hex=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes("phase10-smoke|$($env:JWT_SECRET)")))).Replace('-','');return "P10!$($hex.Substring(0,30))z"}finally{$sha.Dispose()}
}

function Get-Phase11AccessToken([string]$ProjectRoot,[string]$BaseUrl='http://127.0.0.1:8088') {
  $body=@{login='phase10_smoke';password=(Get-Phase11Password $ProjectRoot)}|ConvertTo-Json -Compress
  return (Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/v1/auth/login" -ContentType 'application/json' -Body $body -TimeoutSec 15).data.accessToken
}

function Write-OperationalRun([string]$ProjectRoot,[hashtable]$Payload,[string]$IdempotencyKey,[string]$BaseUrl='http://127.0.0.1:8088') {
  try{
    $token=Get-Phase11AccessToken $ProjectRoot $BaseUrl
    $headers=@{Authorization="Bearer $token";'Idempotency-Key'=$IdempotencyKey;'X-Trace-Id'="phase11-$([Guid]::NewGuid().ToString('N'))"}
    return (Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/v1/operations/runs" -Headers $headers -ContentType 'application/json' -Body ($Payload|ConvertTo-Json -Depth 30 -Compress) -TimeoutSec 30).data
  }finally{$token=$null;$headers=$null}
}

function Get-RelativeArtifactPath([string]$ProjectRoot,[string]$Path) {
  $rootPath=(Resolve-Path -LiteralPath $ProjectRoot).Path.TrimEnd('\')+'\'
  $targetPath=(Resolve-Path -LiteralPath $Path).Path
  $rootUri=[Uri]::new($rootPath);$targetUri=[Uri]::new($targetPath)
  return [Uri]::UnescapeDataString($rootUri.MakeRelativeUri($targetUri).ToString()).Replace('\','/')
}

function Get-FileSha256([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
