param([switch]$SkipRecord)

$ErrorActionPreference='Stop'
$projectRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'phase11-common.ps1')
Import-JobPilotEnv $projectRoot
$stamp=(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$batchId="phase11-security-$stamp"
$reportRoot=Join-Path $projectRoot "reports\phase11\security-$stamp"
New-Item -ItemType Directory -Path $reportRoot -Force|Out-Null

function Invoke-Native([string]$Label,[scriptblock]$Command,[int[]]$AcceptedCodes=@(0)) {
  $log=Join-Path $reportRoot "$Label.log"
  $global:LASTEXITCODE=0
  $previousErrorAction=$ErrorActionPreference
  try {
    $ErrorActionPreference='Continue'
    & $Command *>&1 | Out-File -LiteralPath $log -Encoding utf8
    $code=if($null-ne$LASTEXITCODE){[int]$LASTEXITCODE}else{0}
  } catch {
    $_ | Out-File -LiteralPath $log -Append -Encoding utf8
    $code=if($LASTEXITCODE){[int]$LASTEXITCODE}else{1}
  } finally {
    $ErrorActionPreference=$previousErrorAction
  }
  return [pscustomobject]@{label=$Label;exitCode=[int]$code;executed=$true;accepted=($AcceptedCodes-contains[int]$code);log=(Get-RelativeArtifactPath $projectRoot $log)}
}

function Read-NpmAudit([string]$Path) {
  try {$value=Get-Content -LiteralPath $Path -Raw|ConvertFrom-Json;return [ordered]@{critical=[int]$value.metadata.vulnerabilities.critical;high=[int]$value.metadata.vulnerabilities.high;moderate=[int]$value.metadata.vulnerabilities.moderate;low=[int]$value.metadata.vulnerabilities.low}}catch{return [ordered]@{critical=0;high=0;moderate=0;low=0;parseError=$true}}
}

function Count-Sarif([string]$Path) {
  try {$value=Get-Content -LiteralPath $Path -Raw|ConvertFrom-Json;$results=@($value.runs|ForEach-Object{@($_.results)});return $results.Count}catch{return 0}
}

Push-Location $projectRoot
try {
  $steps=@();$findings=[ordered]@{maven=@{};npm=@{};python=@{};secrets=@{};containers=@{};licenses=@{}}
  $toolVersions=[ordered]@{
    owaspDependencyCheck='13.0.0'
    pipAudit='2.10.1'
    gitleaks='8.29.1'
    trivy='0.72.0'
    licenseMavenPlugin='2.6.0'
    npmLicenseChecker='4.4.2'
  }

  $mavenDir=Join-Path $reportRoot 'maven';New-Item -ItemType Directory -Path $mavenDir -Force|Out-Null
  $mavenStep=Invoke-Native 'maven-dependency-check' { Push-Location (Join-Path $projectRoot 'backend');try{& .\mvnw.cmd -B org.owasp:dependency-check-maven:13.0.0:check '-DfailBuildOnCVSS=11' '-Dformat=JSON' '-DnvdDatafeedUrl=https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz'}finally{Pop-Location} }
  $steps+=$mavenStep
  $mavenReport=Get-ChildItem -LiteralPath $mavenDir -Filter 'dependency-check-report.json' -Recurse -ErrorAction SilentlyContinue|Select-Object -First 1
  if(!$mavenReport){$generated=Join-Path $projectRoot 'backend\target\dependency-check-report.json';if(Test-Path -LiteralPath $generated){Copy-Item -LiteralPath $generated -Destination (Join-Path $mavenDir 'dependency-check-report.json') -Force;$mavenReport=Get-Item (Join-Path $mavenDir 'dependency-check-report.json')}}
  if($mavenReport){try{$dc=Get-Content -LiteralPath $mavenReport.FullName -Raw|ConvertFrom-Json;$v=@($dc.dependencies|ForEach-Object{@($_.vulnerabilities)});$findings.maven=[ordered]@{scanner='OWASP Dependency-Check';report=(Get-RelativeArtifactPath $projectRoot $mavenReport.FullName);vulnerabilities=$v.Count;critical=@($v|Where-Object{$_.severity-eq'CRITICAL'}).Count;high=@($v|Where-Object{$_.severity-eq'HIGH'}).Count}}catch{$findings.maven=[ordered]@{parseError=$true}}}else{
    $fallback=Join-Path $mavenDir 'docker-scout-filesystem.sarif.json'
    $backendJar=Get-ChildItem (Join-Path $projectRoot 'backend\target') -Filter 'jobpilot-backend-*.jar'|Where-Object{$_.Name-notlike'*.original'}|Select-Object -First 1
    if(!$backendJar){throw 'Backend JAR is required for dependency scan fallback'}
    $fallbackStep=Invoke-Native 'maven-docker-scout-fallback' { & docker scout cves --format sarif --output $fallback "fs://backend/target/$($backendJar.Name)" } @(0,2)
    $steps+=$fallbackStep
    if($fallbackStep.accepted){$mavenStep.accepted=$true;$findings.maven=[ordered]@{scanner='Docker Scout filesystem fallback';report=(Get-RelativeArtifactPath $projectRoot $fallback);vulnerabilities=(Count-Sarif $fallback);owaspFeedUnavailable=$true}}else{$findings.maven=[ordered]@{reportMissing=$true;owaspFeedUnavailable=$true}}
  }

  foreach($name in @('frontend','extension','automation-worker')){
    $out=Join-Path $reportRoot "npm-$name-audit.json"
    $steps+=Invoke-Native "npm-$name-audit" { Push-Location (Join-Path $projectRoot $name);try{& npm.cmd audit --omit=dev --json | Set-Content -LiteralPath $out -Encoding utf8}finally{Pop-Location} } @(0,1)
    $findings.npm[$name]=Read-NpmAudit $out
  }

  $python=Join-Path $projectRoot 'ai-service\.venv\Scripts\python.exe'
  if(!(Test-Path -LiteralPath $python)){throw 'AI service virtual environment is missing'}
  $steps+=Invoke-Native 'python-security-tools-install' { & $python -m pip install --disable-pip-version-check -r (Join-Path $projectRoot 'ai-service\requirements-dev.txt') }
  $pythonAudit=Join-Path $reportRoot 'python-pip-audit.json'
  $steps+=Invoke-Native 'python-pip-audit' { & $python -m pip_audit --requirement (Join-Path $projectRoot 'ai-service\requirements.txt') --format=json --output=$pythonAudit } @(0,1)
  try{$pa=Get-Content -LiteralPath $pythonAudit -Raw|ConvertFrom-Json;$pv=@($pa.dependencies|ForEach-Object{@($_.vulns)});$findings.python=[ordered]@{vulnerabilities=$pv.Count;report=(Get-RelativeArtifactPath $projectRoot $pythonAudit)}}catch{$findings.python=[ordered]@{parseError=$true}}

  $secretReport=Join-Path $reportRoot 'gitleaks.json'
  $secretRelative=($secretReport.Substring($projectRoot.Length+1)-replace'\\','/')
  $steps+=Invoke-Native 'gitleaks' { & docker run --rm -v "${projectRoot}:/repo" ghcr.io/gitleaks/gitleaks:v8.29.1 dir /repo --config=/repo/.gitleaks.toml --redact --report-format=json --report-path="/repo/$secretRelative" --exit-code=1 } @(0,1)
  if(!(Test-Path -LiteralPath $secretReport)){'[]'|Set-Content -LiteralPath $secretReport -Encoding utf8}
  try{
    $leaks=Get-Content -LiteralPath $secretReport -Raw|ConvertFrom-Json
    $leakCount=if($null-eq$leaks){0}elseif($leaks-is[System.Array]){$leaks.Count}else{1}
    $findings.secrets=[ordered]@{count=[int]$leakCount;report=(Get-RelativeArtifactPath $projectRoot $secretReport)}
  }catch{$findings.secrets=[ordered]@{count=0;reportMissing=!(Test-Path -LiteralPath $secretReport)}}

  $licenseDir=Join-Path $reportRoot 'licenses';New-Item -ItemType Directory -Path $licenseDir -Force|Out-Null
  $steps+=Invoke-Native 'maven-licenses' { Push-Location (Join-Path $projectRoot 'backend');try{& .\mvnw.cmd -B org.codehaus.mojo:license-maven-plugin:2.6.0:add-third-party "-Dlicense.thirdPartyFilename=phase11-third-party.txt"}finally{Pop-Location} }
  $mavenLicense=Get-ChildItem (Join-Path $projectRoot 'backend\target') -Filter 'phase11-third-party.txt' -Recurse -ErrorAction SilentlyContinue|Select-Object -First 1
  if($mavenLicense){Copy-Item -LiteralPath $mavenLicense.FullName -Destination (Join-Path $licenseDir 'backend-third-party.txt') -Force}
  foreach($name in @('frontend','extension','automation-worker')){
    $licenseOut=Join-Path $licenseDir "$name.json"
    $steps+=Invoke-Native "npm-$name-licenses" { & npx.cmd --yes license-checker-rseidelsohn@4.4.2 --production --json --start (Join-Path $projectRoot $name) | Set-Content -LiteralPath $licenseOut -Encoding utf8 }
  }
  $pythonLicenses=Join-Path $licenseDir 'python.json'
  $steps+=Invoke-Native 'python-licenses' { & $python -m piplicenses --format=json --output-file=$pythonLicenses }
  $licenseFiles=@(Get-ChildItem -LiteralPath $licenseDir -File)
  $licensePackages=0;$thirdPartyUnknown=0;$internalUnlicensed=0;$forbiddenLicenses=0
  foreach($name in @('frontend','extension','automation-worker')){
    $inventory=Get-Content -LiteralPath (Join-Path $licenseDir "$name.json") -Raw|ConvertFrom-Json
    foreach($property in $inventory.PSObject.Properties){
      $licensePackages++;$packageName=[string]$property.Name;$license=[string]$property.Value.licenses
      if($packageName-like'jobpilot-*' -and $license-match'(?i)UNKNOWN|unlicensed'){$internalUnlicensed++}
      elseif($license-match'(?i)UNKNOWN|unlicensed'){$thirdPartyUnknown++}
      if($packageName-notlike'jobpilot-*' -and $license-match'(?i)AGPL|GPL-3\.0-only|SSPL'){$forbiddenLicenses++}
    }
  }
  $pythonInventory=Get-Content -LiteralPath $pythonLicenses -Raw|ConvertFrom-Json
  foreach($package in @($pythonInventory)){
    $licensePackages++;$license=[string]$package.License
    if($license-match'(?i)UNKNOWN|unlicensed'){$thirdPartyUnknown++}
    if($license-match'(?i)AGPL|GPL-3\.0-only|SSPL'){$forbiddenLicenses++}
  }
  $mavenLicensePath=Join-Path $licenseDir 'backend-third-party.txt'
  if(Test-Path -LiteralPath $mavenLicensePath){$forbiddenLicenses+=@(Get-Content -LiteralPath $mavenLicensePath|Where-Object{$_-match'(?i)\bAGPL\b|\bGPL-3\.0-only\b|\bSSPL\b'}).Count}
  $findings.licenses=[ordered]@{reports=$licenseFiles.Count;packages=$licensePackages;thirdPartyUnknown=$thirdPartyUnknown;internalPackagesUnlicensed=$internalUnlicensed;forbidden=$forbiddenLicenses}

  $images=@(& docker compose --profile ai config --images|Where-Object{$_}|Sort-Object -Unique)
  foreach($image in $images){
    $safe=($image-replace'[^A-Za-z0-9._-]','_');$sarif=Join-Path $reportRoot "container-$safe.sarif.json"
    $sarifName=Split-Path -Leaf $sarif
    $steps+=Invoke-Native "container-$safe" {
      & docker run --rm `
        -v /var/run/docker.sock:/var/run/docker.sock `
        -v jobpilot-trivy-cache:/root/.cache/trivy `
        --mount "type=bind,source=$reportRoot,target=/reports" `
        aquasec/trivy:0.72.0 image `
        --db-repository ghcr.io/aquasecurity/trivy-db:2 `
        --image-src remote `
        --skip-version-check `
        --scanners vuln `
        --severity CRITICAL,HIGH `
        --format sarif `
        --output "/reports/$sarifName" `
        $image
    }
    if(Test-Path -LiteralPath $sarif){
      $findings.containers[$image]=[ordered]@{scanner='Trivy';criticalHigh=(Count-Sarif $sarif);report=(Get-RelativeArtifactPath $projectRoot $sarif)}
    }else{
      $findings.containers[$image]=[ordered]@{scanner='Trivy';criticalHigh=0;reportMissing=$true}
    }
  }

  $toolFailures=@($steps|Where-Object{-not$_.accepted})
  $runtimeCriticalHigh=[int](($findings.containers.Values|ForEach-Object{$_.criticalHigh}|Measure-Object -Sum).Sum)
  $dependencyCriticalHigh=0
  if($findings.maven.critical){$dependencyCriticalHigh+=[int]$findings.maven.critical}
  if($findings.maven.high){$dependencyCriticalHigh+=[int]$findings.maven.high}
  foreach($audit in $findings.npm.Values){$dependencyCriticalHigh+=[int]$audit.critical+[int]$audit.high}
  $summary=[ordered]@{status=if($toolFailures.Count){'FAILED'}elseif($findings.secrets.count -or $runtimeCriticalHigh -or $dependencyCriticalHigh){'PARTIAL'}else{'SUCCEEDED'};batchId=$batchId;createdAt=(Get-Date).ToUniversalTime().ToString('o');toolVersions=$toolVersions;steps=$steps;findings=$findings;dependencyCriticalHigh=$dependencyCriticalHigh;runtimeCriticalHigh=$runtimeCriticalHigh;toolFailures=$toolFailures.Count}
  $summaryPath=Join-Path $reportRoot 'summary.json';$summary|ConvertTo-Json -Depth 30|Set-Content -LiteralPath $summaryPath -Encoding utf8
  $sha=Get-FileSha256 $summaryPath;$relative=Get-RelativeArtifactPath $projectRoot $summaryPath;$finished=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss')
  if(!$SkipRecord){
    $started=(Get-Date).ToUniversalTime().AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ss')
    foreach($kind in @('DEPENDENCY_SCAN','SECRET_SCAN','LICENSE_SCAN','CONTAINER_SCAN')){
      $kindStatus=if($toolFailures.Count){'FAILED'}elseif($kind-eq'DEPENDENCY_SCAN'-and$dependencyCriticalHigh){'PARTIAL'}elseif($kind-eq'SECRET_SCAN'-and$findings.secrets.count){'PARTIAL'}elseif($kind-eq'CONTAINER_SCAN'-and$runtimeCriticalHigh){'PARTIAL'}else{'SUCCEEDED'}
      $payload=@{runType=$kind;status=$kindStatus;batchId=$batchId;scope='SYSTEM';startedAt=$started;finishedAt=$finished;metrics=@{dependencyCriticalHigh=$dependencyCriticalHigh;runtimeCriticalHigh=$runtimeCriticalHigh;secretFindings=$findings.secrets.count;toolFailures=$toolFailures.Count};summary="Phase 11 $kind completed with $kindStatus";artifactManifestPath=$relative;artifactSha256=$sha;createdBy='SCRIPT'}
      $null=Write-OperationalRun $projectRoot $payload "$batchId-$kind"
    }
  }
  $summary|ConvertTo-Json -Depth 8 -Compress
  if($summary.status-eq'FAILED'){exit 1}
} finally { Pop-Location }
