[CmdletBinding()]
param(
    [string]$StackRoot
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($StackRoot)) {
    $StackRoot = Join-Path $repoRoot 'output\local-stack'
}
$StackRoot = [IO.Path]::GetFullPath($StackRoot)
$stateFile = Join-Path $StackRoot 'state.json'
$reportFile = Join-Path $StackRoot 'acceptance-report.json'

function Assert-CommonResult {
    param([object]$Result, [string]$Description)
    if ($null -eq $Result -or [int]$Result.status -ne 200) {
        $serialized = $Result | ConvertTo-Json -Depth 8 -Compress
        throw "$Description failed: $serialized"
    }
}

function Invoke-MySqlScalar {
    param([string]$Sql)
    $output = & mysql --connect-timeout=5 --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" `
        --user=root --batch --skip-column-names --raw --execute=$Sql
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    if ($null -eq $output) {
        return ''
    }
    return (($output | Select-Object -First 1).ToString().Trim())
}

function Get-EnvValue {
    param([string]$Path, [string]$Name)
    $prefix = "$Name="
    $line = Get-Content -LiteralPath $Path | Where-Object { $_.StartsWith($prefix) } | Select-Object -First 1
    if ($null -eq $line) {
        throw "Missing $Name in $Path."
    }
    return $line.Substring($prefix.Length)
}

foreach ($command in @('docker', 'mysql')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $command"
    }
}
if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) {
    throw "Local stack state was not found. Run scripts/start-local-stack.ps1 first: $stateFile"
}

$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
$composeFile = [string]$state.composeFile
$envFile = [string]$state.envFile
foreach ($file in @($composeFile, $envFile)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Local stack input was not found: $file"
    }
}
$composeArgs = @('--env-file', $envFile, '-p', [string]$state.projectName, '-f', $composeFile)
$runningServices = @(& docker compose @composeArgs ps --services --filter 'status=running')
if ($LASTEXITCODE -ne 0 -or @($runningServices).Count -ne 7) {
    throw "Expected seven running local-stack services, found $(@($runningServices).Count)."
}

$mysqlBindingLines = & docker compose @composeArgs port mysql 3306
$composePortExitCode = $LASTEXITCODE
$mysqlBinding = ($mysqlBindingLines | Select-Object -First 1).ToString().Trim()
if ($composePortExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($mysqlBinding)) {
    throw 'Unable to resolve the local MySQL host port.'
}
$script:mysqlPort = [int](($mysqlBinding -split ':')[-1])
$savedMySqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
[Environment]::SetEnvironmentVariable('MYSQL_PWD', (Get-EnvValue -Path $envFile -Name 'LOCAL_MYSQL_PASSWORD'), 'Process')

try {
    $schemaSql = "SET SESSION group_concat_max_len=10000000; SELECT SHA2(GROUP_CONCAT(CONCAT(table_name, ':', column_name, ':', column_type, ':', is_nullable, ':', IFNULL(column_default, '<NULL>'), ':', extra) ORDER BY table_name, ordinal_position SEPARATOR '|'), 256) FROM information_schema.columns WHERE table_schema='hoj';"
    $schemaSignatureBefore = Invoke-MySqlScalar -Sql $schemaSql
    $tableCountBefore = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hoj' AND table_type='BASE TABLE';")
    $problemCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.problem;')
    $fileCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.`file`;')
    $judgeCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge;')
    $judgeCaseCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge_case;')
    $userAcProblemCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.user_acproblem;')

    Write-Host 'Checking container health and production-copy read paths...'
    $frontendResponse = Invoke-WebRequest -UseBasicParsing -Uri "$($state.frontendUrl)/" -TimeoutSec 10
    if ($frontendResponse.StatusCode -ne 200) { throw 'Local frontend did not return HTTP 200.' }
    $judgeHealth = Invoke-RestMethod -Uri "$($state.judgeUrl)/actuator/health" -TimeoutSec 10
    if ([string]$judgeHealth.status -ne 'UP') { throw 'Local JudgeServer health is not UP.' }
    Assert-CommonResult -Result (Invoke-RestMethod -Uri "$($state.backendUrl)/api/get-website-config" -TimeoutSec 10) -Description 'Website configuration'
    $languages = Invoke-RestMethod -Uri "$($state.backendUrl)/api/languages?all=true" -TimeoutSec 10
    Assert-CommonResult -Result $languages -Description 'Language list'
    if (@($languages.data).Count -lt 1) { throw 'Language list returned no entries.' }
    Assert-CommonResult -Result (Invoke-RestMethod -Uri "$($state.backendUrl)/api/get-group-list?limit=10&currentPage=1" -TimeoutSec 10) -Description 'Missing-onlyMine group regression'

    $uploadedRoot = Join-Path $StackRoot 'work\uploaded\file'
    $uploadedCandidate = $null
    foreach ($relativeDirectory in @('avatar', 'avatar\group', 'md', 'carousel', 'problem')) {
        $candidateDirectory = Join-Path $uploadedRoot $relativeDirectory
        if (Test-Path -LiteralPath $candidateDirectory -PathType Container) {
            $uploadedCandidate = Get-ChildItem -LiteralPath $candidateDirectory -File -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($null -ne $uploadedCandidate) { break }
        }
    }
    if ($null -eq $uploadedCandidate) {
        throw 'No production uploaded file was found in the extracted compatibility copy.'
    }
    $uploadedFileUrl = "$($state.frontendUrl)/api/public/file/$([Uri]::EscapeDataString($uploadedCandidate.Name))"
    $uploadedFileResponse = Invoke-WebRequest -UseBasicParsing -Uri $uploadedFileUrl -TimeoutSec 20
    if ($uploadedFileResponse.StatusCode -ne 200 -or $uploadedFileResponse.RawContentLength -le 0) {
        throw 'A production uploaded file was not readable through the upgraded backend.'
    }

    Write-Host 'Checking legacy login and historical production records...'
    $loginBody = @{ username = [string]$state.username; password = [string]$state.password } | ConvertTo-Json -Compress
    $loginResponse = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$($state.backendUrl)/api/login" `
        -ContentType 'application/json; charset=utf-8' -Body $loginBody -TimeoutSec 10
    $loginResult = $loginResponse.Content | ConvertFrom-Json
    Assert-CommonResult -Result $loginResult -Description 'Legacy MD5 login'
    $authorizationToken = [string]$loginResponse.Headers['Authorization']
    if ([string]::IsNullOrWhiteSpace($authorizationToken)) {
        throw 'Login succeeded without returning an Authorization token.'
    }
    $authHeaders = @{ Authorization = $authorizationToken }
    $dailyCheckInStatus = Invoke-RestMethod -Uri "$($state.backendUrl)/api/daily-check-in" -Headers $authHeaders -TimeoutSec 10
    Assert-CommonResult -Result $dailyCheckInStatus -Description 'Daily check-in status after authenticated refresh'
    $problemDisplayId = [string]$state.problemDisplayId
    $problemDetail = Invoke-RestMethod -Uri "$($state.backendUrl)/api/get-problem-detail?problemId=$([Uri]::EscapeDataString($problemDisplayId))" -Headers $authHeaders -TimeoutSec 10
    Assert-CommonResult -Result $problemDetail -Description 'Historical problem detail'
    $historicalDetail = Invoke-RestMethod -Uri "$($state.backendUrl)/api/get-submission-detail?submitId=$($state.historicalSubmitId)" -Headers $authHeaders -TimeoutSec 10
    Assert-CommonResult -Result $historicalDetail -Description 'Historical submission detail'
    if ([int]$historicalDetail.data.submission.status -ne 0) {
        throw 'The selected historical Accepted submission no longer has Accepted status.'
    }

    $acceptedCodeBase64 = Invoke-MySqlScalar -Sql "SELECT REPLACE(REPLACE(TO_BASE64(code), CHAR(10), ''), CHAR(13), '') FROM hoj.judge WHERE submit_id=$($state.historicalSubmitId);"
    $acceptedCode = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($acceptedCodeBase64))
    if ([string]::IsNullOrWhiteSpace($acceptedCode)) {
        throw 'The historical Accepted source could not be loaded.'
    }

    Write-Host 'Submitting Accepted, Wrong Answer, Compile Error, and Time Limit Exceeded cases...'
    $submissionCases = @(
        [pscustomobject]@{ Name = 'Accepted'; ExpectedStatus = 0; Code = $acceptedCode },
        [pscustomobject]@{ Name = 'Wrong Answer'; ExpectedStatus = -1; Code = "#include <iostream>`nint main(){std::cout<<`"__HOJ_LOCAL_WRONG__`";return 0;}" },
        [pscustomobject]@{ Name = 'Compile Error'; ExpectedStatus = -2; Code = "#include <iostream>`nint main(){ this deliberately invalid source must not compile; }" },
        [pscustomobject]@{ Name = 'Time Limit Exceeded'; ExpectedStatus = 1; Code = "#include <cstdint>`nint main(){volatile std::uint64_t value=0;while(true){value++;}return 0;}" }
    )
    $submissionResults = [ordered]@{}
    foreach ($submissionCase in $submissionCases) {
        $submitBody = @{
            pid = $problemDisplayId
            language = 'C++'
            code = $submissionCase.Code
            cid = 0
            isRemote = $false
        } | ConvertTo-Json -Depth 5 -Compress
        $submitResponse = Invoke-RestMethod -Method Post -Uri "$($state.backendUrl)/api/submit-problem-judge" `
            -Headers $authHeaders -ContentType 'application/json; charset=utf-8' -Body $submitBody -TimeoutSec 20
        Assert-CommonResult -Result $submitResponse -Description "$($submissionCase.Name) submission"
        $submitId = [long]$submitResponse.data.submitId
        if ($submitId -le 0) { throw "$($submissionCase.Name) did not return a valid submitId." }

        $finalStatus = $null
        for ($attempt = 1; $attempt -le 120; $attempt++) {
            $status = [int](Invoke-MySqlScalar -Sql "SELECT status FROM hoj.judge WHERE submit_id=$submitId;")
            if (@(5, 6, 7, 9) -notcontains $status) {
                $finalStatus = $status
                break
            }
            Start-Sleep -Seconds 1
        }
        if ($null -eq $finalStatus) { throw "$($submissionCase.Name) remained active for 120 seconds." }
        if ([int]$finalStatus -ne [int]$submissionCase.ExpectedStatus) {
            $diagnostic = Invoke-MySqlScalar -Sql "SELECT COALESCE(error_message, '') FROM hoj.judge WHERE submit_id=$submitId;"
            throw "$($submissionCase.Name) returned status $finalStatus instead of $($submissionCase.ExpectedStatus): $diagnostic"
        }
        $detail = Invoke-RestMethod -Uri "$($state.backendUrl)/api/get-submission-detail?submitId=$submitId" -Headers $authHeaders -TimeoutSec 10
        Assert-CommonResult -Result $detail -Description "$($submissionCase.Name) detail"
        if ([int]$detail.data.submission.status -ne [int]$finalStatus) {
            throw "$($submissionCase.Name) detail did not expose the persisted status."
        }
        $submissionResults[$submissionCase.Name] = $submitId
        Write-Host "  $($submissionCase.Name): submitId=$submitId, status=$finalStatus"
    }

    $compileDiagnosticLength = [int](Invoke-MySqlScalar -Sql "SELECT CHAR_LENGTH(COALESCE(error_message, '')) FROM hoj.judge WHERE submit_id=$($submissionResults['Compile Error']);")
    if ($compileDiagnosticLength -le 0) { throw 'Compile Error did not persist compiler diagnostics.' }
    $taskCount = [int](Invoke-MySqlScalar -Sql 'SELECT COALESCE(SUM(task_number), 0) FROM hoj.judge_server;')
    if ($taskCount -ne 0) { throw "JudgeServer task counters were not released: $taskCount" }

    $schemaSignatureAfter = Invoke-MySqlScalar -Sql $schemaSql
    $tableCountAfter = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hoj' AND table_type='BASE TABLE';")
    $problemCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.problem;')
    $fileCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.`file`;')
    $judgeCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge;')
    $judgeCaseCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge_case;')
    $userAcProblemCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.user_acproblem;')

    if ($schemaSignatureAfter -ne $schemaSignatureBefore -or $tableCountAfter -ne $tableCountBefore) {
        throw 'Database schema changed during local compatibility acceptance.'
    }
    if ($problemCountAfter -ne $problemCountBefore -or $fileCountAfter -ne $fileCountBefore) {
        throw 'Problem or uploaded-file metadata changed during local compatibility acceptance.'
    }
    if ($judgeCountAfter -ne ($judgeCountBefore + 4)) {
        throw "Expected four judge rows, before=$judgeCountBefore after=$judgeCountAfter."
    }
    if ($judgeCaseCountAfter -le $judgeCaseCountBefore) {
        throw 'No judge-case results were persisted by the real judge flow.'
    }
    if ($userAcProblemCountAfter -ne ($userAcProblemCountBefore + 1)) {
        throw "Expected one Accepted relation, before=$userAcProblemCountBefore after=$userAcProblemCountAfter."
    }

    $report = [ordered]@{
        verifiedAt = [DateTimeOffset]::Now.ToString('o')
        databaseBackupSha256 = [string]$state.databaseBackupSha256
        uploadedFileArchiveSha256 = [string]$state.uploadedFileArchiveSha256
        schemaSignature = $schemaSignatureAfter
        tableCount = $tableCountAfter
        uploadedFileUrl = $uploadedFileUrl
        problemDisplayId = $problemDisplayId
        historicalSubmitId = [long]$state.historicalSubmitId
        submissions = $submissionResults
        judgeRowsAdded = $judgeCountAfter - $judgeCountBefore
        judgeCaseRowsAdded = $judgeCaseCountAfter - $judgeCaseCountBefore
        acceptedRelationsAdded = $userAcProblemCountAfter - $userAcProblemCountBefore
        result = 'passed'
    }
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportFile -Encoding UTF8
    Write-Host 'Local all-container API, file, database, and real-judge acceptance passed.'
    Write-Host "Report: $reportFile"
}
finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $savedMySqlPwd, 'Process')
}
