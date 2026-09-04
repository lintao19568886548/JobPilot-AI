param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [switch]$VerifyPersistence,
    [switch]$ForceFull
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$statePath = Join-Path $projectRoot "runtime\phase1-smoke-state.json"
$envFile = Join-Path $projectRoot ".env"
$script:httpCount = 0

foreach ($line in Get-Content -LiteralPath $envFile) {
    $value = $line.Trim()
    if (-not $value -or $value.StartsWith("#")) { continue }
    $parts = $value.Split("=", 2)
    if ($parts.Count -eq 2) { [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process") }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "ASSERTION FAILED: $Message" }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = $null,
        [switch]$Raw
    )
    $script:httpCount++
    $headers = @{ "X-Trace-Id" = "smoke-$([guid]::NewGuid().ToString('N'))" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $parameters = @{
        Uri = "$BaseUrl$Path"
        Method = $Method
        Headers = $headers
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    $response = Invoke-RestMethod @parameters
    if (-not $Raw) {
        Assert-True ($response.code -eq 0) "$Method $Path returned API code $($response.code)"
        Assert-True (-not [string]::IsNullOrWhiteSpace($response.traceId)) "$Method $Path has no traceId"
    }
    return $response
}

function Login {
    $response = Invoke-Api POST "/api/auth/login" @{
        login = $env:BOOTSTRAP_USER_USERNAME
        password = $env:BOOTSTRAP_USER_PASSWORD
    }
    Assert-True (-not [string]::IsNullOrWhiteSpace($response.data.accessToken)) "Login has no access token"
    return $response.data
}

if ($VerifyPersistence -and $ForceFull) {
    throw "Use either -VerifyPersistence or -ForceFull, not both."
}

if (-not $VerifyPersistence -and -not $ForceFull -and (Test-Path -LiteralPath $statePath)) {
    Write-Output "SMOKE_MODE=AUTO_PERSISTENCE"
    Write-Output "Existing smoke state detected. Use -ForceFull only with a clean database."
    $VerifyPersistence = $true
}

try {
    $preflightHealth = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 3
    if ($preflightHealth.status -ne "UP") {
        throw "Backend health status is $($preflightHealth.status)."
    }
} catch {
    throw "Backend is unavailable at $BaseUrl. In another PowerShell window run: Set-Location '$projectRoot'; powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1"
}

if ($VerifyPersistence) {
    if (-not (Test-Path -LiteralPath $statePath)) { throw "Smoke state not found: $statePath" }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $auth = Login
    $token = $auth.accessToken
    $profile = Invoke-Api GET "/api/candidate/profile" $null $token
    Assert-True ($profile.data.headline -eq $state.headline) "Profile did not survive backend restart"
    $skills = Invoke-Api GET "/api/candidate/skills" $null $token
    Assert-True ($skills.data.id -contains $state.candidateSkillId) "Candidate skill did not survive restart"
    $educations = Invoke-Api GET "/api/candidate/educations" $null $token
    Assert-True ($educations.data.id -contains $state.educationId) "Education did not survive restart"
    $experiences = Invoke-Api GET "/api/candidate/experiences" $null $token
    Assert-True ($experiences.data.id -contains $state.experienceId) "Experience did not survive restart"
    $projects = Invoke-Api GET "/api/candidate/projects" $null $token
    Assert-True ($projects.data.id -contains $state.projectId) "Project did not survive restart"
    $resumes = Invoke-Api GET "/api/resumes" $null $token
    $savedResume = $resumes.data | Where-Object id -eq $state.resumeId
    Assert-True ($null -ne $savedResume) "Resume did not survive restart"
    Assert-True ($savedResume.defaultResume -and $savedResume.master) "Resume flags did not survive restart"
    $version1 = Invoke-Api GET "/api/resume-versions/$($state.version1Id)" $null $token
    $version2 = Invoke-Api GET "/api/resume-versions/$($state.version2Id)" $null $token
    Assert-True ($version1.data.versionNumber -eq 1) "Immutable v1 is unavailable"
    Assert-True ($version2.data.versionNumber -eq 2) "Current v2 is unavailable"
    $dashboard = Invoke-Api GET "/api/dashboard" $null $token
    Assert-True ($dashboard.data.resumeVersions -ge 2) "Dashboard did not read persisted version counts"
    [pscustomobject]@{ status = "PASS"; mode = "PERSISTENCE"; httpCount = $script:httpCount } | ConvertTo-Json -Compress
    exit 0
}

$health = Invoke-Api GET "/actuator/health" $null $null -Raw
Assert-True ($health.status -eq "UP") "Actuator health is not UP"
$openApi = Invoke-Api GET "/v3/api-docs" $null $null -Raw
Assert-True ($openApi.openapi -like "3.*") "OpenAPI document is unavailable"

$auth = Login
$token = $auth.accessToken
$oldRefresh = $auth.refreshToken
$me = Invoke-Api GET "/api/auth/me" $null $token
Assert-True ($me.data.username -eq $env:BOOTSTRAP_USER_USERNAME) "Authenticated user mismatch"
$refreshed = Invoke-Api POST "/api/auth/refresh" @{ refreshToken = $oldRefresh }
$token = $refreshed.data.accessToken
$refresh = $refreshed.data.refreshToken
$script:httpCount++
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/auth/refresh" -Method POST -ContentType "application/json" `
        -Body (@{ refreshToken = $oldRefresh } | ConvertTo-Json -Compress) -TimeoutSec 15 | Out-Null
    throw "Rotated refresh token was accepted twice"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -ne 401) { throw }
}

$profileBefore = Invoke-Api GET "/api/candidate/profile" $null $token
$marker = "Phase 1 persisted $([guid]::NewGuid().ToString('N').Substring(0,8))"
$profile = Invoke-Api PUT "/api/candidate/profile" @{
    fullName = "JobPilot Candidate"; headline = $marker; phone = "+8613800000000"
    email = "candidate@example.local"; currentCity = "Shanghai"; targetCities = @("Shanghai", "Hangzhou")
    graduationYear = 2026; highestEducation = "BACHELOR"; school = "JobPilot University"; major = "Software Engineering"
    yearsOfExperience = 1.5; jobStatus = "OPEN_TO_WORK"; githubUrl = "https://github.com/example"
    personalWebsite = "https://example.com"; summary = "Java backend and AI agent engineer."
    targetRoles = @("Java Backend Engineer", "AI Application Engineer"); targetIndustries = @("Software")
    targetCompanyTypes = @("Technology"); targetSalaryMin = 20000; targetSalaryMax = 35000
    salaryCurrency = "CNY"; acceptRemote = $true; acceptRelocation = $false
} $token
$patched = Invoke-Api PATCH "/api/candidate/profile" @{ summary = "Java backend, distributed systems, and AI agent engineer." } $token
Assert-True ($patched.data.headline -eq $marker) "PATCH overwrote omitted fields"

$catalog = Invoke-Api GET "/api/skills?search=Java&category=LANGUAGE" $null $token
$javaSkill = $catalog.data | Where-Object canonicalName -eq "java" | Select-Object -First 1
Assert-True ($null -ne $javaSkill) "Seeded Java skill missing"
$candidateSkill = Invoke-Api POST "/api/candidate/skills" @{
    skillId = $javaSkill.id; proficiency = 95; years = 3.0; lastUsedAt = "2026-08-01"; source = "USER"; primary = $true
} $token
$candidateSkill = Invoke-Api PUT "/api/candidate/skills/$($candidateSkill.data.id)" @{
    proficiency = 96; years = 3.2; lastUsedAt = "2026-08-15"; source = "USER"; primary = $true
} $token
$skillCatalog = Invoke-Api GET "/api/skills" $null $token
foreach ($skillName in @("spring_boot", "redis", "python", "rag")) {
    $catalogSkill = $skillCatalog.data | Where-Object canonicalName -eq $skillName | Select-Object -First 1
    Assert-True ($null -ne $catalogSkill) "Seeded skill missing: $skillName"
    Invoke-Api POST "/api/candidate/skills" @{
        skillId = $catalogSkill.id; proficiency = 88; years = 2.0; lastUsedAt = "2026-08-15"; source = "USER"; primary = $false
    } $token | Out-Null
}
$gitSkill = $skillCatalog.data | Where-Object canonicalName -eq "git" | Select-Object -First 1
$tempSkill = Invoke-Api POST "/api/candidate/skills" @{
    skillId = $gitSkill.id; proficiency = 80; years = 2.0; lastUsedAt = "2026-08-15"; source = "USER"; primary = $false
} $token
Invoke-Api DELETE "/api/candidate/skills/$($tempSkill.data.id)" $null $token | Out-Null

$educationPayload = @{ school = "JobPilot University"; degree = "Bachelor"; major = "Software Engineering"; startDate = "2022-09-01"; endDate = "2026-06-30"; graduationYear = 2026; description = "Distributed systems"; sortOrder = 0 }
$education = Invoke-Api POST "/api/candidate/educations" $educationPayload $token
$educationPayload.description = "Distributed systems and databases"
$education = Invoke-Api PUT "/api/candidate/educations/$($education.data.id)" $educationPayload $token
$tempEducation = Invoke-Api POST "/api/candidate/educations" @{ school = "Temporary"; degree = "Course"; major = "Test"; startDate = "2021-01-01"; endDate = "2021-02-01"; graduationYear = 2021; sortOrder = 99 } $token
Invoke-Api DELETE "/api/candidate/educations/$($tempEducation.data.id)" $null $token | Out-Null

$experiencePayload = @{ companyName = "JobPilot Labs"; role = "Backend Intern"; employmentType = "INTERNSHIP"; location = "Shanghai"; startDate = "2025-01-01"; endDate = "2025-08-31"; currentlyWorking = $false; description = "Backend services"; responsibilities = "Designed APIs"; achievements = "Improved reliability"; technologies = @("Java", "Spring Boot", "Redis"); sortOrder = 0 }
$experience = Invoke-Api POST "/api/candidate/experiences" $experiencePayload $token
$experiencePayload.achievements = "Improved reliability and latency"
$experience = Invoke-Api PUT "/api/candidate/experiences/$($experience.data.id)" $experiencePayload $token
$tempExperience = Invoke-Api POST "/api/candidate/experiences" @{ companyName = "Temporary"; role = "Part-time"; employmentType = "PART_TIME"; startDate = "2024-01-01"; endDate = "2024-02-01"; currentlyWorking = $false; technologies = @("Java"); sortOrder = 99 } $token
Invoke-Api DELETE "/api/candidate/experiences/$($tempExperience.data.id)" $null $token | Out-Null

$projectPayload = @{ name = "JobPilot AI"; role = "Architect"; startDate = "2026-01-01"; description = "Local-first career workspace"; background = "Phase 1 foundation"; responsibilities = "Architecture and implementation"; achievements = "Real persistence"; technologies = @("Java", "Spring Boot", "Vue 3", "MySQL", "Redis"); repoUrl = "https://github.com/example/jobpilot"; demoUrl = "https://example.com/jobpilot"; featured = $true; sortOrder = 0 }
$project = Invoke-Api POST "/api/candidate/projects" $projectPayload $token
$projectPayload.achievements = "Real persistence and immutable resume versions"
$project = Invoke-Api PUT "/api/candidate/projects/$($project.data.id)" $projectPayload $token
$tempProject = Invoke-Api POST "/api/candidate/projects" @{ name = "Temporary Project"; description = "Delete strategy verification"; technologies = @("Java"); featured = $false; sortOrder = 99 } $token
Invoke-Api DELETE "/api/candidate/projects/$($tempProject.data.id)" $null $token | Out-Null

$resume = Invoke-Api POST "/api/resumes" @{ name = "Master Resume"; targetRole = "Java Backend Engineer"; master = $true; defaultResume = $true; description = "Verified candidate fact source"; status = "ACTIVE" } $token
$resumeId = $resume.data.resume.id
$resume = Invoke-Api PUT "/api/resumes/$resumeId" @{ name = "Master Resume"; targetRole = "Java Backend / AI Engineer"; description = "Verified candidate fact source"; status = "ACTIVE" } $token
$version1 = Invoke-Api POST "/api/resumes/$resumeId/versions" @{
    versionName = "Master Resume v1"; content = @{ fullName = "JobPilot Candidate"; targetRole = "Java Backend Engineer" }
    renderedText = "JobPilot Candidate - Java Backend Engineer"; sourceType = "MANUAL"; createdBy = "USER"
    sections = @(
        @{ sectionType = "BASIC_INFO"; content = @{ fullName = "JobPilot Candidate"; email = "candidate@example.local" }; sortOrder = 0 },
        @{ sectionType = "SKILLS"; content = @("Java", "Spring Boot", "Redis"); sortOrder = 1 }
    )
} $token
$version2 = Invoke-Api POST "/api/resumes/$resumeId/versions" @{
    versionName = "Master Resume v2"; content = @{ fullName = "JobPilot Candidate"; targetRole = "Java Backend / AI Engineer" }
    renderedText = "JobPilot Candidate - Java Backend / AI Engineer"; sourceType = "MANUAL"; createdBy = "USER"
    sections = @(
        @{ sectionType = "BASIC_INFO"; content = @{ fullName = "JobPilot Candidate"; email = "candidate@example.local" }; sortOrder = 0 },
        @{ sectionType = "PROJECTS"; content = @(@{ name = "JobPilot AI"; technologies = @("Java", "Vue 3") }); sortOrder = 1 }
    )
} $token
$v1Again = Invoke-Api GET "/api/resume-versions/$($version1.data.id)" $null $token
Assert-True ($v1Again.data.versionNumber -eq 1) "Creating v2 overwrote v1"
$versions = Invoke-Api GET "/api/resumes/$resumeId/versions" $null $token
Assert-True ($versions.data.Count -eq 2) "Expected exactly two immutable resume versions"

$tempResume = Invoke-Api POST "/api/resumes" @{ name = "Temporary Resume"; targetRole = "Test"; master = $false; defaultResume = $false; status = "ACTIVE" } $token
$tempResumeId = $tempResume.data.resume.id
Invoke-Api POST "/api/resumes/$tempResumeId/set-default" $null $token | Out-Null
Invoke-Api POST "/api/resumes/$tempResumeId/set-master" $null $token | Out-Null
Invoke-Api POST "/api/resumes/$resumeId/set-default" $null $token | Out-Null
Invoke-Api POST "/api/resumes/$resumeId/set-master" $null $token | Out-Null
Invoke-Api DELETE "/api/resumes/$tempResumeId" $null $token | Out-Null

$educationList = Invoke-Api GET "/api/candidate/educations" $null $token
$experienceList = Invoke-Api GET "/api/candidate/experiences" $null $token
$projectList = Invoke-Api GET "/api/candidate/projects" $null $token
$skillList = Invoke-Api GET "/api/candidate/skills" $null $token
$resumeList = Invoke-Api GET "/api/resumes" $null $token
$completeness = Invoke-Api GET "/api/candidate/profile/completeness" $null $token
$dashboard = Invoke-Api GET "/api/dashboard" $null $token
Assert-True ($educationList.data.Count -eq 1) "Education logical delete failed"
Assert-True ($experienceList.data.Count -eq 1) "Experience logical delete failed"
Assert-True ($projectList.data.Count -eq 1) "Project logical delete failed"
Assert-True ($skillList.data.Count -eq 5) "Candidate skill count mismatch"
Assert-True ($resumeList.data.Count -eq 1) "Resume logical delete failed"
Assert-True ($completeness.data.score -eq 100) "Expected complete candidate profile"
Assert-True ($dashboard.data.resumeVersions -eq 2) "Dashboard version count mismatch"

$state = [ordered]@{
    marker = $marker
    headline = $marker
    profileId = $profile.data.id
    candidateSkillId = $candidateSkill.data.id
    educationId = $education.data.id
    experienceId = $experience.data.id
    projectId = $project.data.id
    resumeId = $resumeId
    version1Id = $version1.data.id
    version2Id = $version2.data.id
    initialHttpCount = $script:httpCount
}
New-Item -ItemType Directory -Path (Split-Path -Parent $statePath) -Force | Out-Null
$state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $statePath -Encoding utf8
[pscustomobject]@{ status = "PASS"; mode = "FULL"; httpCount = $script:httpCount; state = $state } | ConvertTo-Json -Depth 10 -Compress
