[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatabaseBackup,
    [Parameter(Mandatory = $true)][string]$ExpectedDatabaseBackupSha256,
    [Parameter(Mandatory = $true)][string]$ProductionJudgeDataDirectory,
    [Parameter(Mandatory = $true)][string]$UploadedFileArchive,
    [Parameter(Mandatory = $true)][string]$ExpectedUploadedFileArchiveSha256,
    [string]$MySqlImage = 'mysql:8.0.27',
    [string]$RedisImage = 'redis:7-alpine',
    [string]$NacosImage = 'nacos/nacos-server:1.4.2',
    [string]$BackendRuntimeImage = 'nacos/nacos-server:1.4.2',
    [string]$JudgeRuntimeImage = 'registry.cn-shenzhen.aliyuncs.com/hcode/hoj_judgeserver@sha256:6c85513645079eb48b3650dd5675ac307c6f813a63b4030bf0dcc032a18b3c6a',
    [string]$SandboxRuntimeImage = 'registry.cn-shenzhen.aliyuncs.com/hcode/hoj_judgeserver@sha256:6c85513645079eb48b3650dd5675ac307c6f813a63b4030bf0dcc032a18b3c6a',
    [string]$FrontendImage = 'nginx:1.27-alpine'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $PSScriptRoot 'local-stack.compose.yml'
$nginxConfig = Join-Path $PSScriptRoot 'local-stack.nginx.conf'
$dailyCheckInMigration = Join-Path $repoRoot 'sqlAndsetting\hoj-daily-check-in-update.sql'
$backendJar = Join-Path $repoRoot 'hoj-springboot\DataBackup\target\hoj-backend-4.6.jar'
$judgeJar = Join-Path $repoRoot 'hoj-springboot\JudgeServer\target\hoj-judgeServer-4.6.jar'
$frontendDist = Join-Path $repoRoot 'hoj-vue\dist'
$sandboxBinary = Join-Path $repoRoot 'sandbox\Sandbox-amd64-v1.8.0'
$stackRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'output\local-stack'))
$outputRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'output')).TrimEnd('\')
$projectName = 'hojlocal'
$runId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$mysqlPassword = [guid]::NewGuid().ToString('N')
$redisPassword = [guid]::NewGuid().ToString('N')
$jwtSecret = [guid]::NewGuid().ToString('N')
$judgeToken = [guid]::NewGuid().ToString('N')
$aiKeyEncryptionSecret = [guid]::NewGuid().ToString('N')
$stagingUid = "local$runId"
$stagingUsername = "local_$runId"
$stagingPassword = 'LocalStage123!'
$envFile = Join-Path $stackRoot '.env'
$stateFile = Join-Path $stackRoot 'state.json'
$workDirectory = Join-Path $stackRoot 'work'
$sqlFile = Join-Path $workDirectory 'production.sql'
$uploadedExtractionRoot = Join-Path $workDirectory 'uploaded'
$uploadedFileDirectory = Join-Path $uploadedExtractionRoot 'file'
$judgeWorkDirectory = Join-Path $workDirectory 'judge'
$composeArgs = @('--env-file', $envFile, '-p', $projectName, '-f', $composeFile)
$stackStarted = $false
$savedMySqlPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')

function Assert-FileHash {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description was not found: $Path"
    }
    if ($Expected -notmatch '^[0-9a-fA-F]{64}$') {
        throw "$Description requires an explicit 64-character SHA-256."
    }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $Expected.ToUpperInvariant()) {
        throw "$Description hash mismatch: expected=$($Expected.ToUpperInvariant()), actual=$actual"
    }
    return $actual
}

function Expand-GzipFile {
    param([string]$Source, [string]$Destination)
    $sourceStream = [IO.File]::OpenRead($Source)
    try {
        $gzipStream = New-Object IO.Compression.GZipStream($sourceStream, [IO.Compression.CompressionMode]::Decompress)
        try {
            $destinationStream = [IO.File]::Create($Destination)
            try { $gzipStream.CopyTo($destinationStream) } finally { $destinationStream.Dispose() }
        }
        finally { $gzipStream.Dispose() }
    }
    finally { $sourceStream.Dispose() }
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose @composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Get-ComposePort {
    param([string]$Service, [int]$ContainerPort)
    $binding = & docker compose @composeArgs port $Service $ContainerPort
    if ($LASTEXITCODE -ne 0 -or -not $binding) {
        throw "Unable to resolve $Service port $ContainerPort."
    }
    return [int](($binding | Select-Object -First 1) -split ':')[-1]
}

function Wait-Http {
    param([string]$Uri, [string]$Description, [int]$Attempts = 180)
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $response
            }
        }
        catch { }
        Start-Sleep -Seconds 1
    }
    throw "$Description did not become ready: $Uri"
}

function Invoke-MySqlScalar {
    param([string]$Sql)
    $output = & mysql --connect-timeout=5 --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" `
        --user=root --batch --skip-column-names --raw --execute=$Sql
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    return (($output | Select-Object -First 1).ToString().Trim())
}

foreach ($command in @('docker', 'mysql', 'tar')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $command"
    }
}
foreach ($file in @($composeFile, $nginxConfig, $dailyCheckInMigration, $backendJar, $judgeJar, $sandboxBinary)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Required local-stack input was not found: $file"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $frontendDist 'index.html') -PathType Leaf)) {
    throw "Frontend build output was not found: $frontendDist"
}
if (Test-Path -LiteralPath $stackRoot) {
    throw "A local acceptance stack state already exists. Run scripts/stop-local-stack.ps1 first: $stackRoot"
}
$existingStack = & docker ps -a --filter "label=com.docker.compose.project=$projectName" --format '{{.Names}}'
if ($existingStack) {
    throw "Docker resources for project '$projectName' already exist. Refusing to replace them."
}

$DatabaseBackup = [IO.Path]::GetFullPath($DatabaseBackup)
$UploadedFileArchive = [IO.Path]::GetFullPath($UploadedFileArchive)
$ProductionJudgeDataDirectory = [IO.Path]::GetFullPath($ProductionJudgeDataDirectory)
$productionTestCaseDirectory = Join-Path $ProductionJudgeDataDirectory 'test_case'
if (-not (Test-Path -LiteralPath $productionTestCaseDirectory -PathType Container)) {
    throw "Production test-case copy was not found: $productionTestCaseDirectory"
}
$databaseHash = Assert-FileHash -Path $DatabaseBackup -Expected $ExpectedDatabaseBackupSha256 -Description 'Database backup'
$uploadedArchiveHash = Assert-FileHash -Path $UploadedFileArchive -Expected $ExpectedUploadedFileArchiveSha256 -Description 'Uploaded-file archive'

try {
    New-Item -ItemType Directory -Path $workDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $uploadedExtractionRoot -Force | Out-Null
    foreach ($directory in @('run', 'log', 'spj', 'interactive')) {
        New-Item -ItemType Directory -Path (Join-Path $judgeWorkDirectory $directory) -Force | Out-Null
    }

    Write-Host 'Expanding the verified production database backup...'
    Expand-GzipFile -Source $DatabaseBackup -Destination $sqlFile

    Write-Host 'Validating and extracting the verified uploaded-file archive...'
    $archiveEntries = & tar -tzf $UploadedFileArchive
    if ($LASTEXITCODE -ne 0 -or -not $archiveEntries) {
        throw 'Unable to list the uploaded-file archive.'
    }
    foreach ($entry in $archiveEntries) {
        $normalized = $entry.ToString().Replace('\\', '/').TrimEnd('/')
        if ($normalized.StartsWith('/') -or $normalized -match '^[A-Za-z]:' -or
                @($normalized.Split('/') | Where-Object { $_ -eq '..' }).Count -gt 0) {
            throw "Unsafe uploaded-file archive entry: $entry"
        }
    }
    & tar -xzf $UploadedFileArchive -C $uploadedExtractionRoot
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $uploadedFileDirectory -PathType Container)) {
        throw 'Uploaded-file archive extraction failed.'
    }

    $environment = [ordered]@{
        LOCAL_MYSQL_IMAGE = $MySqlImage
        LOCAL_REDIS_IMAGE = $RedisImage
        LOCAL_NACOS_IMAGE = $NacosImage
        LOCAL_BACKEND_RUNTIME_IMAGE = $BackendRuntimeImage
        LOCAL_JUDGE_RUNTIME_IMAGE = $JudgeRuntimeImage
        LOCAL_SANDBOX_IMAGE = $SandboxRuntimeImage
        LOCAL_FRONTEND_IMAGE = $FrontendImage
        LOCAL_MYSQL_PASSWORD = $mysqlPassword
        LOCAL_REDIS_PASSWORD = $redisPassword
        LOCAL_JWT_SECRET = $jwtSecret
        LOCAL_JUDGE_TOKEN = $judgeToken
        LOCAL_AI_KEY_ENCRYPTION_SECRET = $aiKeyEncryptionSecret
        LOCAL_BACKEND_JAR = $backendJar.Replace('\\', '/')
        LOCAL_JUDGE_JAR = $judgeJar.Replace('\\', '/')
        LOCAL_FRONTEND_DIST = $frontendDist.Replace('\\', '/')
        LOCAL_SANDBOX_BINARY = $sandboxBinary.Replace('\\', '/')
        LOCAL_JUDGE_WORK_DIRECTORY = $judgeWorkDirectory.Replace('\\', '/')
        LOCAL_PRODUCTION_TEST_CASE_DIRECTORY = $productionTestCaseDirectory.Replace('\\', '/')
        LOCAL_UPLOADED_FILE_DIRECTORY = $uploadedFileDirectory.Replace('\\', '/')
        LOCAL_NGINX_CONFIG = $nginxConfig.Replace('\\', '/')
    }
    $environment.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content -LiteralPath $envFile -Encoding UTF8

    Write-Host 'Starting local MySQL, Redis, Nacos, and sandbox containers...'
    Invoke-Compose -Arguments @('up', '--detach', 'mysql', 'redis', 'nacos', 'sandbox')
    $stackStarted = $true

    $mysqlContainer = [string](& docker compose @composeArgs ps -q mysql)
    $mysqlContainer = $mysqlContainer.Trim()
    if (-not $mysqlContainer) { throw 'Unable to resolve the local MySQL container.' }
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker exec --env "MYSQL_PWD=$mysqlPassword" $mysqlContainer mysqladmin ping --user=root --silent 2>$null | Out-Null
        $mysqlPingExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorActionPreference
        if ($mysqlPingExitCode -eq 0) { break }
        if ($attempt -eq 90) { throw 'Local MySQL did not become ready.' }
        Start-Sleep -Seconds 1
    }
    $script:mysqlPort = Get-ComposePort -Service mysql -ContainerPort 3306
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $mysqlPassword, 'Process')
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & mysql --connect-timeout=2 --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" `
            --user=root --silent --skip-column-names --execute='SELECT 1' 2>$null | Out-Null
        $mysqlHostProbeExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorActionPreference
        if ($mysqlHostProbeExitCode -eq 0) { break }
        if ($attempt -eq 30) { throw 'Local MySQL host port did not become ready.' }
        Start-Sleep -Seconds 1
    }
    $sqlSourcePath = $sqlFile.Replace('\\', '/')
    Write-Host "Restoring the production database copy into local MySQL on port $script:mysqlPort..."
    & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" --user=root `
        --default-character-set=utf8mb4 --execute="source $sqlSourcePath"
    if ($LASTEXITCODE -ne 0) { throw 'Restoring the local production database copy failed.' }

    $dailyCheckInMigrationSourcePath = $dailyCheckInMigration.Replace('\', '/')
    Write-Host 'Applying local database compatibility migrations...'
    & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" --user=root `
        --database=hoj --default-character-set=utf8mb4 --execute="source $dailyCheckInMigrationSourcePath"
    if ($LASTEXITCODE -ne 0) { throw 'Applying the daily check-in database migration failed.' }

    $md5 = [Security.Cryptography.MD5]::Create()
    try {
        $stagingPasswordHash = ([BitConverter]::ToString($md5.ComputeHash([Text.Encoding]::UTF8.GetBytes($stagingPassword)))).Replace('-', '').ToLowerInvariant()
    }
    finally { $md5.Dispose() }
    $accountSql = "INSERT INTO hoj.user_info (uuid, username, password, nickname, status, gmt_create, gmt_modified) VALUES ('$stagingUid', '$stagingUsername', '$stagingPasswordHash', 'Local container acceptance', 0, NOW(), NOW()); INSERT INTO hoj.user_record (uid, gmt_create, gmt_modified) VALUES ('$stagingUid', NOW(), NOW()); INSERT INTO hoj.user_role (uid, role_id, gmt_create, gmt_modified) VALUES ('$stagingUid', 1000, NOW(), NOW());"
    & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" --user=root --default-character-set=utf8mb4 --execute=$accountSql
    if ($LASTEXITCODE -ne 0) { throw 'Creating the local staging account failed.' }

    $candidateSql = "SELECT CONCAT(p.id, CHAR(9), p.problem_id, CHAR(9), MAX(j.submit_id)) FROM hoj.problem p INNER JOIN hoj.judge j ON j.pid=p.id WHERE p.is_remote=0 AND p.auth=1 AND p.judge_mode='default' AND p.is_file_io=0 AND j.status=0 AND j.language='C++' AND j.code IS NOT NULL GROUP BY p.id, p.problem_id ORDER BY MAX(j.submit_id) DESC LIMIT 1000;"
    $candidateRows = & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" --user=root --batch --skip-column-names --raw --execute=$candidateSql
    if ($LASTEXITCODE -ne 0) { throw 'Querying a local acceptance problem failed.' }
    $selected = $null
    foreach ($row in $candidateRows) {
        $parts = $row.ToString().Split("`t")
        if ($parts.Count -ne 3) { continue }
        $directory = Join-Path $productionTestCaseDirectory "problem_$($parts[0])"
        if (Test-Path -LiteralPath (Join-Path $directory 'info') -PathType Leaf) {
            $selected = [pscustomobject]@{ problemId = [long]$parts[0]; displayId = $parts[1]; historicalSubmitId = [long]$parts[2] }
            break
        }
    }
    if ($null -eq $selected) { throw 'No representative production problem with copied test cases was found.' }

    $nacosPort = Get-ComposePort -Service nacos -ContainerPort 8848
    $nacosBaseUrl = "http://127.0.0.1:$nacosPort"
    Wait-Http -Uri "$nacosBaseUrl/nacos/v1/console/health/readiness" -Description 'Local Nacos' | Out-Null
    $nacosConfig = @"
hoj:
  jwt:
    secret: $jwtSecret
    expire: 86400
    checkRefreshExpire: 43200
  judge:
    token: $judgeToken
  db:
    host: mysql
    public-host: mysql
    port: 3306
    public-port: 3306
    name: hoj
    username: root
    password: $mysqlPassword
  redis:
    host: redis
    port: 6379
    password: $redisPassword
"@
    $publish = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$nacosBaseUrl/nacos/v1/cs/configs" `
        -Body @{ dataId = 'hoj-prod.yml'; group = 'DEFAULT_GROUP'; type = 'yaml'; content = $nacosConfig } -TimeoutSec 10
    if ($publish.Content.Trim() -ne 'true') { throw 'Nacos rejected hoj-prod.yml.' }
    $switchPublish = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$nacosBaseUrl/nacos/v1/cs/configs" `
        -Body @{ dataId = 'hoj-switch.yml'; group = 'DEFAULT_GROUP'; type = 'yaml'; content = "defaultSubmitInterval: 0`nopenPublicJudge: true`n" } -TimeoutSec 10
    if ($switchPublish.Content.Trim() -ne 'true') { throw 'Nacos rejected hoj-switch.yml.' }

    Write-Host 'Starting current backend, JudgeServer, and native Vue 3 frontend containers...'
    Invoke-Compose -Arguments @('up', '--detach', 'backend', 'judgeserver', 'frontend')
    $backendPort = Get-ComposePort -Service backend -ContainerPort 6688
    $judgePort = Get-ComposePort -Service judgeserver -ContainerPort 8088
    $frontendPort = Get-ComposePort -Service frontend -ContainerPort 80
    Wait-Http -Uri "http://127.0.0.1:$backendPort/api/get-website-config" -Description 'Local backend' | Out-Null
    Wait-Http -Uri "http://127.0.0.1:$judgePort/actuator/health" -Description 'Local JudgeServer' | Out-Null
    Wait-Http -Uri "http://127.0.0.1:$frontendPort/" -Description 'Local frontend' | Out-Null

    $groupResponse = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/get-group-list?limit=10&currentPage=1" -TimeoutSec 10
    if ([int]$groupResponse.status -ne 200) { throw 'The missing-onlyMine regression check did not return status 200.' }

    $state = [ordered]@{
        projectName = $projectName
        composeFile = $composeFile
        envFile = $envFile
        stackRoot = $stackRoot
        frontendUrl = "http://127.0.0.1:$frontendPort"
        backendUrl = "http://127.0.0.1:$backendPort"
        judgeUrl = "http://127.0.0.1:$judgePort"
        username = $stagingUsername
        password = $stagingPassword
        stagingUid = $stagingUid
        problemId = $selected.problemId
        problemDisplayId = $selected.displayId
        historicalSubmitId = $selected.historicalSubmitId
        databaseBackup = $DatabaseBackup
        databaseBackupSha256 = $databaseHash
        uploadedFileArchiveSha256 = $uploadedArchiveHash
    }
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Write-Host 'Local container acceptance stack is ready.'
    Write-Host "Frontend: $($state.frontendUrl)"
    Write-Host "Backend:  $($state.backendUrl)"
    Write-Host "Judge:    $($state.judgeUrl)"
    Write-Host "Staging account: $stagingUsername"
    Write-Host "Representative problem: $($selected.displayId); historical submission: $($selected.historicalSubmitId)"
    Write-Host 'Run scripts/stop-local-stack.ps1 when browser and API acceptance is complete.'
}
catch {
    Write-Warning $_.Exception.Message
    if ($stackStarted -and (Test-Path -LiteralPath $envFile)) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker compose @composeArgs ps
        & docker compose @composeArgs logs --tail 120 backend judgeserver frontend
        & docker compose @composeArgs down --volumes --remove-orphans
        $ErrorActionPreference = $previous
    }
    if (Test-Path -LiteralPath $stackRoot) {
        $resolved = [IO.Path]::GetFullPath($stackRoot)
        if (-not $resolved.StartsWith($outputRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean a path outside the repository output directory: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    throw
}
finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $savedMySqlPwd, 'Process')
}
