param(
    [switch]$VerifyPersistence
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$baseUrl = 'http://127.0.0.1:8088'
$aiUrl = 'http://127.0.0.1:8010'
$statePath = Join-Path $projectRoot 'scripts\.phase8-smoke-state.json'

function Read-Env([string]$Name) {
    $line = Get-Content -LiteralPath (Join-Path $projectRoot '.env') |
        Where-Object { $_ -match ('^\s*' + [regex]::Escape($Name) + '\s*=') } |
        Select-Object -Last 1
    if (-not $line) { throw "Missing local environment value: $Name" }
    $value = ($line -split '=', 2)[1].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    return $value
}

function New-DerivedPassword([string]$Scope) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes("$Scope|$(Read-Env 'JWT_SECRET')")
        $hex = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '')
        return "P8!$($hex.Substring(0, 30))z"
    } finally { $sha.Dispose() }
}

function New-Bcrypt([string]$Password) {
    $crypto = Get-ChildItem -LiteralPath "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto" -Recurse -Filter 'spring-security-crypto-*.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $jcl = Get-ChildItem -LiteralPath "$env:USERPROFILE\.m2\repository\org\springframework\spring-jcl" -Recurse -Filter 'spring-jcl-*.jar' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $crypto -or -not $jcl) { throw 'Spring Security crypto dependencies are unavailable' }
    $env:JOBPILOT_RESET_PASSWORD = $Password
    try {
        $value = ((& java --class-path ($crypto.FullName + ';' + $jcl.FullName) (Join-Path $PSScriptRoot 'GenerateBcrypt.java')) -join '').Trim()
    } finally { Remove-Item Env:JOBPILOT_RESET_PASSWORD -ErrorAction SilentlyContinue }
    if ($value -notmatch '^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$') { throw 'BCrypt generation failed' }
    return $value
}

function Invoke-Api([string]$Method, [string]$Path, [hashtable]$Headers, $Body = $null) {
    $parameters = @{ Method = $Method; Uri = "$baseUrl$Path"; Headers = $Headers; TimeoutSec = 30 }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Expect-Http([int]$Status, [scriptblock]$Action, [string]$Label) {
    try {
        $null = & $Action
        throw "$Label unexpectedly succeeded"
    } catch {
        $actual = [int]$_.Exception.Response.StatusCode
        if ($actual -ne $Status) { throw "$Label expected HTTP $Status but received $actual" }
    }
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Ensure-SmokeUsers {
    $smokeHash = New-Bcrypt $script:smokePassword
    $intruderHash = New-Bcrypt $script:intruderPassword
    $smokePublic = ('P8S' + [Guid]::NewGuid().ToString('N')).Substring(0, 26).ToUpperInvariant()
    $intruderPublic = ('P8I' + [Guid]::NewGuid().ToString('N')).Substring(0, 26).ToUpperInvariant()
    $sql = @"
INSERT INTO users(public_id,username,email,password_hash,display_name,status,created_at,updated_at)
VALUES('$smokePublic','phase8_smoke','phase8-smoke@local.invalid','$smokeHash','Phase 8 Smoke','ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),status='ACTIVE',deleted_at=NULL,updated_at=UTC_TIMESTAMP(3);
INSERT INTO users(public_id,username,email,password_hash,display_name,status,created_at,updated_at)
VALUES('$intruderPublic','phase8_intruder','phase8-intruder@local.invalid','$intruderHash','Phase 8 Intruder','ACTIVE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),status='ACTIVE',deleted_at=NULL,updated_at=UTC_TIMESTAMP(3);
UPDATE refresh_tokens rt JOIN users u ON u.id=rt.user_id SET rt.revoked_at=COALESCE(rt.revoked_at,UTC_TIMESTAMP(3)),rt.updated_at=UTC_TIMESTAMP(3)
WHERE u.username IN ('phase8_smoke','phase8_intruder') AND rt.deleted_at IS NULL;
"@
    $sql | docker compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw 'Smoke user initialization failed' }
}

function Login([string]$Name, [string]$Password) {
    return Invoke-Api 'POST' '/api/v1/auth/login' @{} @{ login = $Name; password = $Password }
}

Push-Location $projectRoot
try {
    Assert-True ((Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 5).status -eq 'UP') 'Backend is unavailable'
    Assert-True ((Invoke-RestMethod -Uri "$aiUrl/internal/v1/health" -TimeoutSec 5).status -eq 'UP') 'AI service is unavailable'
    Assert-True ((docker compose exec -T redis redis-cli ping) -match 'PONG') 'Redis is unavailable'

    $script:smokePassword = New-DerivedPassword 'phase8-smoke'
    $script:intruderPassword = New-DerivedPassword 'phase8-intruder'
    Ensure-SmokeUsers
    $login = Login 'phase8_smoke' $script:smokePassword
    $intruderLogin = Login 'phase8_intruder' $script:intruderPassword
    $headers = @{ Authorization = "Bearer $($login.data.accessToken)"; 'X-Trace-Id' = "phase8-smoke-$([Guid]::NewGuid().ToString('N'))" }
    $intruderHeaders = @{ Authorization = "Bearer $($intruderLogin.data.accessToken)"; 'X-Trace-Id' = "phase8-owner-$([Guid]::NewGuid().ToString('N'))" }

    if ($VerifyPersistence) {
        if (-not (Test-Path -LiteralPath $statePath)) { throw 'Persistence state file is missing' }
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $detail = Invoke-Api 'GET' "/api/v1/interviews/$($state.interviewId)" $headers
        Assert-True ($detail.data.status -eq 'IN_PROGRESS') 'Persisted Interview state was not restored'
        Assert-True ($detail.data.rounds.Count -eq 2) 'Persisted rounds were not restored'
        Assert-True (($detail.data.rounds.questions | Where-Object { $_.sourceType -eq 'PREDICTED' }).Count -ge 6) 'Predicted questions did not persist'
        Assert-True (($detail.data.rounds.questions.answerNotes).Count -ge 2) 'Answer Notes did not persist'
        $reviews = Invoke-Api 'GET' "/api/v1/interviews/$($state.interviewId)/reviews" $headers
        Assert-True ($reviews.data.Count -ge 2 -and $reviews.data[0].reviewVersion -eq 2 -and $reviews.data[1].status -eq 'CONFIRMED') 'Immutable Review history did not persist'
        $gaps = Invoke-Api 'GET' '/api/v1/knowledge-gaps' $headers
        $persistedGaps = $gaps.data | Where-Object { $_.sourceInterviewId -eq $state.interviewId }
        Assert-True (@($persistedGaps | Where-Object { $_.status -eq 'ACTIVE' }).Count -ge 1 -and @($persistedGaps | Where-Object { $_.status -eq 'DISMISSED' }).Count -ge 1) 'Knowledge Gap decisions did not persist'
        $reminders = (Invoke-Api 'GET' '/api/v1/interview-reminders' $headers).data | Where-Object { $_.interviewId -eq $state.interviewId }
        Assert-True (@($reminders | Where-Object { $_.status -eq 'PENDING' }).Count -ge 1 -and @($reminders | Where-Object { $_.status -eq 'DONE' }).Count -ge 1 -and @($reminders | Where-Object { $_.status -eq 'CANCELLED' }).Count -ge 1) 'Reminder lifecycle did not persist'
        $dashboard = Invoke-Api 'GET' '/api/v1/interviews/dashboard' $headers
        Assert-True ($dashboard.data.upcomingInterviews.Count -ge 1 -and $dashboard.data.pendingReminders.Count -ge 1) 'Dashboard facts did not persist'
        @{ status = 'PASS'; mode = 'PERSISTENCE'; interviewId = $state.interviewId; httpCount = 7 } | ConvertTo-Json -Compress
        exit 0
    }

    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $offset = [TimeSpan]::FromHours(8)
    $start1 = [DateTimeOffset]::Now.ToOffset($offset).AddDays(2).Date.AddHours(10)
    $end1 = $start1.AddHours(1)
    $start2 = $start1.AddDays(3)
    $end2 = $start2.AddHours(1)
    $format = 'yyyy-MM-ddTHH:mm:sszzz'
    $interview = Invoke-Api 'POST' '/api/v1/interviews' $headers @{
        companyName = "Phase 8 Smoke $suffix"; role = 'Senior Java Backend Engineer'; timezone = 'Asia/Shanghai';
        notes = 'Created by the Phase 8 real HTTP smoke test'
    }
    $interviewId = $interview.data.id
    Assert-True ($interview.data.status -eq 'SCHEDULED') 'Interview create failed'
    $round1 = Invoke-Api 'POST' "/api/v1/interviews/$interviewId/rounds" $headers @{
        roundNo = 1; roundType = 'TECHNICAL'; title = 'Technical Round'; scheduledStartAt = $start1.ToString($format);
        scheduledEndAt = $end1.ToString($format); timezone = 'Asia/Shanghai'; format = 'ONLINE'; meetingLink = 'https://example.invalid/phase8';
        location = $null; interviewerName = 'Smoke Interviewer'; notes = 'No meeting was opened or joined'
    }
    $round2 = Invoke-Api 'POST' "/api/v1/interviews/$interviewId/rounds" $headers @{
        roundNo = 2; roundType = 'BEHAVIORAL'; title = 'Behavioral Round'; scheduledStartAt = $start2.ToString($format);
        scheduledEndAt = $end2.ToString($format); timezone = 'Asia/Shanghai'; format = 'ONLINE'; meetingLink = $null;
        location = $null; interviewerName = $null; notes = $null
    }
    $round3 = Invoke-Api 'POST' "/api/v1/interviews/$interviewId/rounds" $headers @{
        roundNo = 3; roundType = 'OTHER'; title = 'CRUD Delete Round'; scheduledStartAt = $start2.AddDays(1).ToString($format);
        scheduledEndAt = $end2.AddDays(1).ToString($format); timezone = 'Asia/Shanghai'; format = 'OTHER'
    }
    $null = Invoke-Api 'DELETE' "/api/v1/interview-rounds/$($round3.data.id)" $headers

    $predictionHeaders = $headers.Clone(); $predictionHeaders['Idempotency-Key'] = "phase8-predict-$suffix"
    $predicted = Invoke-Api 'POST' "/api/v1/interview-rounds/$($round1.data.id)/questions:predict" $predictionHeaders
    Assert-True ($predicted.data.Count -eq 6) 'Prediction did not return six categories'
    Assert-True (($predicted.data | Where-Object { $_.sourceType -ne 'PREDICTED' }).Count -eq 0) 'Prediction source label is unsafe'
    $predictedReplay = Invoke-Api 'POST' "/api/v1/interview-rounds/$($round1.data.id)/questions:predict" $predictionHeaders
    Assert-True ($predictedReplay.data.Count -eq 6) 'Prediction idempotency replay failed'

    $actual1 = Invoke-Api 'POST' "/api/v1/interview-rounds/$($round1.data.id)/questions" $headers @{
        sourceType = 'ACTUAL'; category = 'SYSTEM_DESIGN'; difficulty = 'HARD';
        question = 'How would you design an idempotent event-driven order service?'; purpose = 'User-recorded actual question';
        basis = 'User memory after the interview'; answerFramework = $null; suggestedFollowUps = @(); riskNotes = $null; displayOrder = 100
    }
    $actual2 = Invoke-Api 'POST' "/api/v1/interview-rounds/$($round1.data.id)/questions" $headers @{
        sourceType = 'ACTUAL'; category = 'PROJECT_DEEP_DIVE'; difficulty = 'MEDIUM';
        question = 'Describe a production incident and the trade-off you made.'; purpose = 'User-recorded actual question';
        basis = 'User memory after the interview'; answerFramework = $null; suggestedFollowUps = @(); riskNotes = $null; displayOrder = 101
    }
    $note1 = Invoke-Api 'POST' "/api/v1/interview-questions/$($actual1.data.id)/answer-notes" $headers @{
        answer = 'I described the idempotency key, transactional outbox, consumer deduplication and monitoring boundaries without inventing throughput numbers.';
        selfRating = 4; recordedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $note1v2 = Invoke-Api 'POST' "/api/v1/interview-questions/$($actual1.data.id)/answer-notes" $headers @{
        answer = 'Second version: I clarified database uniqueness, retry semantics, dead-letter handling and how I would verify recovery.';
        selfRating = 4; recordedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $note2 = Invoke-Api 'POST' "/api/v1/interview-questions/$($actual2.data.id)/answer-notes" $headers @{
        answer = 'I separated the observed incident facts from my inference, then explained mitigation, rollback and follow-up actions.';
        selfRating = 3; recordedAt = [DateTimeOffset]::UtcNow.ToString('o')
    }
    Assert-True ($note1.data.noteVersion -eq 1 -and $note1v2.data.noteVersion -eq 2 -and $note2.data.noteVersion -eq 1) 'Answer Note append-only versioning failed'

    $updatedRound1 = Invoke-Api 'PUT' "/api/v1/interview-rounds/$($round1.data.id)" $headers @{
        roundNo = 1; roundType = 'TECHNICAL'; title = 'Technical Round'; scheduledStartAt = $start1.ToString($format);
        scheduledEndAt = $end1.ToString($format); timezone = 'Asia/Shanghai'; format = 'ONLINE'; meetingLink = 'https://example.invalid/phase8';
        location = $null; interviewerName = 'Smoke Interviewer'; status = 'COMPLETED'; result = 'PENDING'; notes = 'Completed by user'; version = $round1.data.version
    }
    Assert-True ($updatedRound1.data.status -eq 'COMPLETED') 'Round update failed'

    $reviewHeaders = $headers.Clone(); $reviewHeaders['Idempotency-Key'] = "phase8-review-$suffix"
    $review = Invoke-Api 'POST' "/api/v1/interviews/$interviewId/reviews:generate" $reviewHeaders
    Assert-True ($review.data.status -eq 'DRAFT' -and $review.data.reviewVersion -eq 1) 'Review generation failed'
    $reviewReplay = Invoke-Api 'POST' "/api/v1/interviews/$interviewId/reviews:generate" $reviewHeaders
    Assert-True ($reviewReplay.data.id -eq $review.data.id) 'Review idempotency replay failed'
    $review2Headers = $headers.Clone(); $review2Headers['Idempotency-Key'] = "phase8-review-v2-$suffix"
    $review2 = Invoke-Api 'POST' "/api/v1/interviews/$interviewId/reviews:generate" $review2Headers
    $reviewHistory = Invoke-Api 'GET' "/api/v1/interviews/$interviewId/reviews" $headers
    Assert-True ($review2.data.reviewVersion -eq 2 -and $review2.data.id -ne $review.data.id -and $reviewHistory.data.Count -eq 2) 'Review append-only history failed'

    $interviewGaps = (Invoke-Api 'GET' '/api/v1/knowledge-gaps' $headers).data | Where-Object { $_.sourceReviewId -eq $review.data.id }
    Assert-True ($interviewGaps.Count -ge 2) 'Review did not create proposed Knowledge Gaps'
    Expect-Http 409 { Invoke-Api 'POST' "/api/v1/knowledge-gaps/$($interviewGaps[0].id):activate" $headers @{ version = $interviewGaps[0].version } } 'Unconfirmed gap activation'
    $confirmed = Invoke-Api 'POST' "/api/v1/interview-reviews/$($review.data.id):confirm" $headers @{ version = $review.data.version }
    $activated = Invoke-Api 'POST' "/api/v1/knowledge-gaps/$($interviewGaps[0].id):activate" $headers @{ version = $interviewGaps[0].version }
    $dismissed = Invoke-Api 'POST' "/api/v1/knowledge-gaps/$($interviewGaps[1].id):dismiss" $headers @{ version = $interviewGaps[1].version }
    Assert-True ($confirmed.data.status -eq 'CONFIRMED' -and $activated.data.status -eq 'ACTIVE' -and $dismissed.data.status -eq 'DISMISSED') 'Knowledge Gap confirmation flow failed'

    $remindAt = [DateTimeOffset]::Now.ToOffset($offset).AddHours(5)
    $reminder = Invoke-Api 'POST' '/api/v1/interview-reminders' $headers @{
        interviewId = $interviewId; roundId = $round2.data.id; reminderType = 'PREPARE'; title = 'Prepare behavioral examples';
        remindAt = $remindAt.ToString($format); timezone = 'Asia/Shanghai'
    }
    Expect-Http 409 { Invoke-Api 'POST' '/api/v1/interview-reminders' $headers @{
        interviewId = $interviewId; roundId = $round2.data.id; reminderType = 'PREPARE'; title = 'Prepare behavioral examples';
        remindAt = $remindAt.ToString($format); timezone = 'Asia/Shanghai'
    } } 'Duplicate reminder'
    $rescheduled = Invoke-Api 'PUT' "/api/v1/interview-reminders/$($reminder.data.id)" $headers @{
        reminderType = 'PREPARE'; title = 'Prepare behavioral examples - rescheduled'; remindAt = $remindAt.AddHours(1).ToString($format);
        timezone = 'Asia/Shanghai'; version = $reminder.data.version
    }
    $done = Invoke-Api 'POST' "/api/v1/interview-reminders/$($reminder.data.id):done" $headers @{ version = $rescheduled.data.version }
    $cancelCandidate = Invoke-Api 'POST' '/api/v1/interview-reminders' $headers @{
        interviewId = $interviewId; roundId = $round2.data.id; reminderType = 'REVIEW'; title = 'Review reminder to cancel';
        remindAt = $remindAt.AddHours(2).ToString($format); timezone = 'Asia/Shanghai'
    }
    $cancelled = Invoke-Api 'POST' "/api/v1/interview-reminders/$($cancelCandidate.data.id):cancel" $headers @{ version = $cancelCandidate.data.version }
    $pending = Invoke-Api 'POST' '/api/v1/interview-reminders' $headers @{
        interviewId = $interviewId; roundId = $round2.data.id; reminderType = 'START'; title = 'Upcoming round';
        remindAt = $remindAt.AddHours(3).ToString($format); timezone = 'Asia/Shanghai'
    }
    Assert-True ($done.data.status -eq 'DONE' -and $cancelled.data.status -eq 'CANCELLED' -and $pending.data.status -eq 'PENDING') 'Reminder lifecycle failed'

    $inProgress = Invoke-Api 'PUT' "/api/v1/interviews/$interviewId" $headers @{
        companyName = $interview.data.companyName; role = $interview.data.role; status = 'IN_PROGRESS'; result = 'PENDING';
        timezone = 'Asia/Shanghai'; notes = 'Updated by real HTTP smoke'; version = $interview.data.version
    }
    Assert-True ($inProgress.data.status -eq 'IN_PROGRESS') 'Interview update failed'
    Expect-Http 409 { Invoke-Api 'PUT' "/api/v1/interviews/$interviewId" $headers @{
        companyName = $interview.data.companyName; role = $interview.data.role; status = 'IN_PROGRESS'; result = 'PENDING';
        timezone = 'Asia/Shanghai'; notes = 'Stale version must fail'; version = $interview.data.version
    } } 'Interview optimistic lock'
    $detail = Invoke-Api 'GET' "/api/v1/interviews/$interviewId" $headers
    Assert-True ($detail.data.rounds.Count -eq 2) 'Round logical delete or readback failed'
    $list = Invoke-Api 'GET' "/api/v1/interviews?page=1&size=100&company=$([Uri]::EscapeDataString("Phase 8 Smoke $suffix"))" $headers
    Assert-True ($list.data.total -eq 1) 'Interview filter/list failed'
    $dashboard = Invoke-Api 'GET' '/api/v1/interviews/dashboard' $headers
    Assert-True ($dashboard.data.upcomingInterviews.Count -ge 1 -and $dashboard.data.pendingReminders.Count -ge 1 -and $dashboard.data.activeKnowledgeGapCount -ge 1) 'Dashboard integration failed'
    Expect-Http 404 { Invoke-Api 'GET' "/api/v1/interviews/$interviewId" $intruderHeaders } 'Interview ownership'
    Expect-Http 404 { Invoke-Api 'POST' "/api/v1/interview-rounds/$($round2.data.id)/questions:predict" ($intruderHeaders + @{ 'Idempotency-Key' = 'ownership-check' }) } 'Round ownership'

    $temporary = Invoke-Api 'POST' '/api/v1/interviews' $headers @{ companyName = "Delete Test $suffix"; role = 'Temporary'; timezone = 'Asia/Shanghai' }
    $null = Invoke-Api 'DELETE' "/api/v1/interviews/$($temporary.data.id)" $headers
    Expect-Http 404 { Invoke-Api 'GET' "/api/v1/interviews/$($temporary.data.id)" $headers } 'Interview logical delete'

    @{ interviewId = $interviewId; createdAt = [DateTimeOffset]::UtcNow.ToString('o') } |
        ConvertTo-Json -Compress | Set-Content -LiteralPath $statePath -Encoding UTF8
    [pscustomobject]@{
        status = 'PASS'; mode = 'FULL'; httpCount = 42; interviewId = $interviewId; rounds = 2;
        predictedQuestions = $predicted.data.Count; actualQuestions = 2; answerNoteVersions = 3;
        reviewVersions = 2; activatedGaps = 1; dismissedGaps = 1;
        reminderCreateUpdateDoneCancel = 'PASS'; ownership = 'PASS'; logicalDelete = 'PASS';
        externalMessagesSent = 0; externalMeetingInvites = 0; automaticApplicationUpdates = 0
    } | ConvertTo-Json -Compress
} finally {
    Pop-Location
    $script:smokePassword = $null
    $script:intruderPassword = $null
}
