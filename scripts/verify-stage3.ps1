[CmdletBinding()]
param(
    [string]$MySqlImage = 'mysql:8.0',
    [string]$RedisImage = 'redis:7-alpine',
    [string]$NacosImage = 'nacos/nacos-server:1.4.2',
    [string]$JavaPath = 'C:\Program Files\Java\jdk1.8.0_202\bin\java.exe',
    [switch]$IncludeSandboxJudge,
    [switch]$IncludePersistentJudge,
    [switch]$IncludeRollback,
    [string]$RollbackBackendJar,
    [string]$RollbackJudgeJar,
    [string]$DatabaseBackup,
    [string]$ExpectedDatabaseBackupSha256,
    [string]$ProductionJudgeDataDirectory,
    [string]$SandboxRuntimeImage = 'judge0/judge0:latest',
    [ValidateRange(1024, 65535)][int]$SandboxPort = 5050,
    [string]$JudgeRuntimeImage = 'nacos/nacos-server:1.4.2',
    [string]$JudgeContainerJavaPath = '/usr/lib/jvm/java-1.8.0-openjdk/bin/java'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ProductionCopyAcceptance = -not [string]::IsNullOrWhiteSpace($DatabaseBackup)
if ($ProductionCopyAcceptance) {
    $IncludeRollback = [switch]$true
}
if ($IncludeRollback) {
    $IncludePersistentJudge = [switch]$true
}
if ($IncludePersistentJudge) {
    $IncludeSandboxJudge = [switch]$true
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $repoRoot 'sqlAndsetting\hoj.sql'
$databaseRestoreFile = $sqlFile
$databaseRestoreSourcePath = $databaseRestoreFile.Replace('\', '/')
$backendJar = Join-Path $repoRoot 'hoj-springboot\DataBackup\target\hoj-backend-4.6.jar'
$judgeJar = Join-Path $repoRoot 'hoj-springboot\JudgeServer\target\hoj-judgeServer-4.6.jar'
$runId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$mysqlContainer = "hoj-stage3-mysql-$runId"
$redisContainer = "hoj-stage3-redis-$runId"
$nacosContainer = "hoj-stage3-nacos-$runId"
$sandboxContainer = "hoj-stage4-sandbox-$runId"
$judgeRuntimeContainer = "hoj-stage5-judge-$runId"
$rollbackJudgeRuntimeContainer = "hoj-stage6-judge-$runId"
$judgeServerName = "judger-$runId"
$networkName = "hoj-stage-network-$runId"
$mysqlPassword = [guid]::NewGuid().ToString('N')
$redisPassword = [guid]::NewGuid().ToString('N')
$jwtSecret = [guid]::NewGuid().ToString('N')
$judgeToken = [guid]::NewGuid().ToString('N')
$backendProcess = $null
$judgeProcess = $null
$startedContainers = New-Object System.Collections.Generic.List[string]
$networkCreated = $false
$sandboxBinary = Join-Path $repoRoot 'sandbox\Sandbox-amd64-v1.8.0'

$tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')
$workDirectory = [IO.Path]::GetFullPath((Join-Path $tempRoot "hoj-stage3-$runId"))
if (-not $workDirectory.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use a work directory outside the system temp directory: $workDirectory"
}

$backendStdout = Join-Path $workDirectory 'backend.stdout.log'
$backendStderr = Join-Path $workDirectory 'backend.stderr.log'
$judgeStdout = Join-Path $workDirectory 'judge.stdout.log'
$judgeStderr = Join-Path $workDirectory 'judge.stderr.log'
$rollbackBackendStdout = Join-Path $workDirectory 'rollback-backend.stdout.log'
$rollbackBackendStderr = Join-Path $workDirectory 'rollback-backend.stderr.log'
$judgeDataDirectory = Join-Path $workDirectory 'judge-data'
$productionTestCaseDirectory = $null
$databaseBackupHashBefore = $null
$productionTestCaseSignatureBefore = $null
$stagingUid = "stage22$runId"
$stagingUsername = "stage22_$runId"
$stagingPassword = [guid]::NewGuid().ToString('N').Substring(0, 16)
$loginUsername = 'root'
$loginPassword = 'hoj123456'

$environmentNames = @(
    'MYSQL_PWD', 'NACOS_URL', 'NACOS_USERNAME', 'NACOS_PASSWORD',
    'MYSQL_HOST', 'MYSQL_PORT', 'MYSQL_PUBLIC_HOST', 'MYSQL_PUBLIC_PORT',
    'MYSQL_USERNAME', 'MYSQL_ROOT_PASSWORD', 'MYSQL_DATABASE_NAME',
    'REDIS_HOST', 'REDIS_PORT', 'REDIS_PASSWORD', 'BACKEND_SERVER_PORT',
    'JWT_TOKEN_SECRET', 'JUDGE_TOKEN', 'OPEN_REMOTE_JUDGE',
    'STARTUP_DATABASE_INITIALIZATION_ENABLED', 'PASSWORD_BCRYPT_WRITE_ENABLED',
    'PASSWORD_UPGRADE_ON_LOGIN_ENABLED', 'FORCED_UPDATE_REMOTE_JUDGE_ACCOUNT',
    'LOGGING_FILE_PATH', 'SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES',
    'SPRING_PROFILES_ACTIVE', 'JUDGE_SERVER_PORT', 'JUDGE_SERVER_IP',
    'JUDGE_SERVER_NAME', 'REMOTE_JUDGE_OPEN', 'MAX_TASK_NUM',
    'REMOTE_JUDGE_MAX_TASK_NUM', 'SANDBOX_BASE_URL'
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Assert-CommandExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available: $Name"
    }
}

function Set-ProcessEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [AllowEmptyString()][string]$Value
    )

    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Command,
        [Parameter(Mandatory = $true)][string]$Description
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Get-DockerHostPort {
    param(
        [Parameter(Mandatory = $true)][string]$Container,
        [Parameter(Mandatory = $true)][string]$ContainerPort
    )

    $binding = & docker port $Container $ContainerPort
    if ($LASTEXITCODE -ne 0 -or -not $binding) {
        throw "Unable to resolve host port $ContainerPort for $Container."
    }
    return [int](($binding -split ':')[-1].Trim())
}

function Get-FreeTcpPort {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Assert-LocalTcpPortAvailable {
    param([Parameter(Mandatory = $true)][int]$Port)

    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, $Port)
    try {
        $listener.Start()
    }
    catch {
        throw "Local TCP port $Port is already in use; refusing to replace the existing listener."
    }
    finally {
        $listener.Stop()
    }
}

function Wait-ContainerCommand {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Command,
        [Parameter(Mandatory = $true)][string]$Description,
        [int]$Attempts = 90
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $Command 2>$null | Out-Null
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
        if ($exitCode -eq 0) {
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "$Description did not become ready after $Attempts attempts."
}

function Wait-HttpEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [string]$Description = 'HTTP endpoint',
        $Process,
        [int]$Attempts = 120
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if ($null -ne $Process -and $Process.HasExited) {
            throw "$Description process exited with code $($Process.ExitCode) before becoming ready."
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $response
            }
        }
        catch {
            # Service startup commonly refuses connections until its web server is ready.
        }
        Start-Sleep -Seconds 1
    }
    throw "$Description did not become ready: $Uri"
}

function Wait-DockerHttpEndpoint {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$Container,
        [string]$Description = 'Container HTTP endpoint',
        [int]$Attempts = 120
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        $running = & docker inspect --format '{{.State.Running}}' $Container 2>$null
        $inspectExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
        if ($inspectExitCode -ne 0 -or ($running | Select-Object -First 1).Trim() -ne 'true') {
            throw "$Description container exited before becoming ready: $Container"
        }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $response
            }
        }
        catch {
            # The published port may refuse connections until Spring Boot is ready.
        }
        Start-Sleep -Seconds 1
    }
    throw "$Description did not become ready: $Uri"
}

function Invoke-MySqlScalar {
    param([Parameter(Mandatory = $true)][string]$Sql)

    $output = & mysql --connect-timeout=5 --protocol=tcp --host=127.0.0.1 `
        "--port=$script:mysqlPort" --user=root --batch --skip-column-names --execute=$Sql
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    return (($output | Select-Object -First 1).ToString().Trim())
}

function Assert-CommonResultSuccess {
    param(
        [Parameter(Mandatory = $true)]$Result,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ($null -eq $Result -or [int]$Result.status -ne 200) {
        $serialized = $Result | ConvertTo-Json -Depth 8 -Compress
        throw "$Description returned a non-success result: $serialized"
    }
}

function Get-NacosInstances {
    param(
        [Parameter(Mandatory = $true)][string]$NacosBaseUrl,
        [Parameter(Mandatory = $true)][string]$ServiceName
    )

    $encodedName = [Uri]::EscapeDataString($ServiceName)
    return Invoke-RestMethod -Uri "$NacosBaseUrl/nacos/v1/ns/instance/list?serviceName=$encodedName&healthyOnly=true" -TimeoutSec 5
}

function Wait-NacosRegistration {
    param(
        [Parameter(Mandatory = $true)][string]$NacosBaseUrl,
        [Parameter(Mandatory = $true)][string]$ServiceName,
        [int]$Attempts = 45
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $result = Get-NacosInstances -NacosBaseUrl $NacosBaseUrl -ServiceName $ServiceName
            if ($null -ne $result.hosts -and @($result.hosts).Count -gt 0) {
                return $result
            }
        }
        catch {
            # Registration and health propagation are eventually consistent.
        }
        Start-Sleep -Seconds 1
    }
    return $null
}

function Show-LogTail {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$Lines = 80
    )

    if (Test-Path -LiteralPath $Path) {
        Write-Warning "Last $Lines lines from $Path"
        Get-Content -LiteralPath $Path -Tail $Lines
    }
}

function Expand-GzipFile {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    $sourceStream = [IO.File]::OpenRead($Source)
    try {
        $gzipStream = New-Object IO.Compression.GZipStream(
            $sourceStream,
            [IO.Compression.CompressionMode]::Decompress
        )
        try {
            $destinationStream = [IO.File]::Create($Destination)
            try {
                $gzipStream.CopyTo($destinationStream)
            }
            finally {
                $destinationStream.Dispose()
            }
        }
        finally {
            $gzipStream.Dispose()
        }
    }
    finally {
        $sourceStream.Dispose()
    }
}

function Get-DirectoryContentSignature {
    param([Parameter(Mandatory = $true)][string]$Directory)

    $resolvedDirectory = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
    $entries = foreach ($file in Get-ChildItem -LiteralPath $resolvedDirectory -Recurse -File | Sort-Object FullName) {
        $relativePath = $file.FullName.Substring($resolvedDirectory.Length).TrimStart('\').Replace('\', '/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        "$relativePath|$($file.Length)|$hash"
    }
    $payload = [Text.Encoding]::UTF8.GetBytes(($entries -join "`n"))
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha256.ComputeHash($payload))).Replace('-', '')
    }
    finally {
        $sha256.Dispose()
    }
}

Assert-CommandExists -Name 'docker'
Assert-CommandExists -Name 'mysql'
if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) {
    throw "Java 8 executable was not found: $JavaPath"
}
if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
    throw "Backend JAR was not found. Run scripts/verify-stage1.ps1 -BackendOnly first: $backendJar"
}
if (-not (Test-Path -LiteralPath $judgeJar -PathType Leaf)) {
    throw "Judge server JAR was not found. Run scripts/verify-stage1.ps1 -BackendOnly first: $judgeJar"
}
if ($ProductionCopyAcceptance) {
    if (-not (Test-Path -LiteralPath $DatabaseBackup -PathType Leaf)) {
        throw "Production database backup was not found: $DatabaseBackup"
    }
    if ($DatabaseBackup -notmatch '\.sql\.gz$') {
        throw 'Production-copy acceptance requires a gzip-compressed .sql.gz backup.'
    }
    if ($ExpectedDatabaseBackupSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw 'Production-copy acceptance requires an explicit 64-character ExpectedDatabaseBackupSha256 value.'
    }
    $DatabaseBackup = [IO.Path]::GetFullPath($DatabaseBackup)
    $databaseBackupHashBefore = (Get-FileHash -LiteralPath $DatabaseBackup -Algorithm SHA256).Hash
    if ($databaseBackupHashBefore -ne $ExpectedDatabaseBackupSha256.ToUpperInvariant()) {
        throw "Production database backup hash mismatch: expected=$($ExpectedDatabaseBackupSha256.ToUpperInvariant()), actual=$databaseBackupHashBefore"
    }
    if ([string]::IsNullOrWhiteSpace($ProductionJudgeDataDirectory) -or
            -not (Test-Path -LiteralPath $ProductionJudgeDataDirectory -PathType Container)) {
        throw "Production judge data directory was not found: $ProductionJudgeDataDirectory"
    }
    $ProductionJudgeDataDirectory = [IO.Path]::GetFullPath($ProductionJudgeDataDirectory)
    $productionTestCaseDirectory = Join-Path $ProductionJudgeDataDirectory 'test_case'
    if (-not (Test-Path -LiteralPath $productionTestCaseDirectory -PathType Container)) {
        throw "Production test-case directory was not found: $productionTestCaseDirectory"
    }
    $loginUsername = $stagingUsername
    $loginPassword = $stagingPassword
}
if ($IncludeRollback) {
    if ([string]::IsNullOrWhiteSpace($RollbackBackendJar) -or
            -not (Test-Path -LiteralPath $RollbackBackendJar -PathType Leaf)) {
        throw "Rollback backend JAR was not found: $RollbackBackendJar"
    }
    if ([string]::IsNullOrWhiteSpace($RollbackJudgeJar) -or
            -not (Test-Path -LiteralPath $RollbackJudgeJar -PathType Leaf)) {
        throw "Rollback judge-server JAR was not found: $RollbackJudgeJar"
    }
    $RollbackBackendJar = [IO.Path]::GetFullPath($RollbackBackendJar)
    $RollbackJudgeJar = [IO.Path]::GetFullPath($RollbackJudgeJar)
}
if ($IncludeSandboxJudge) {
    if (-not (Test-Path -LiteralPath $sandboxBinary -PathType Leaf)) {
        throw "The checked-in amd64 sandbox binary was not found: $sandboxBinary"
    }
    Assert-LocalTcpPortAvailable -Port $SandboxPort
}

$backendPort = Get-FreeTcpPort
while ($IncludeSandboxJudge -and $backendPort -eq $SandboxPort) {
    $backendPort = Get-FreeTcpPort
}
$judgePort = Get-FreeTcpPort
while ($judgePort -eq $backendPort -or ($IncludeSandboxJudge -and $judgePort -eq $SandboxPort)) {
    $judgePort = Get-FreeTcpPort
}

New-Item -ItemType Directory -Path $workDirectory | Out-Null
New-Item -ItemType Directory -Path $judgeDataDirectory | Out-Null
if ($ProductionCopyAcceptance) {
    $databaseRestoreFile = Join-Path $workDirectory 'production-hoj.sql'
    Write-Host 'Expanding the verified production database backup into the isolated work directory...'
    Expand-GzipFile -Source $DatabaseBackup -Destination $databaseRestoreFile
    $databaseRestoreSourcePath = $databaseRestoreFile.Replace('\', '/')
}
$sqlHashBefore = (Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash

try {
    Write-Host 'Starting isolated MySQL, Redis, and Nacos containers...'
    Set-ProcessEnvironment -Name 'MYSQL_PWD' -Value $mysqlPassword

    Invoke-CheckedNative -Description 'Creating isolated Docker network' -Command {
        & docker network create --driver bridge $networkName | Out-Null
    }
    $networkCreated = $true

    Invoke-CheckedNative -Description 'Starting isolated MySQL' -Command {
        & docker run --name $mysqlContainer --detach `
            --network $networkName `
            --env "MYSQL_ROOT_PASSWORD=$mysqlPassword" `
            --publish '127.0.0.1::3306' `
            $MySqlImage `
            --character-set-server=utf8mb4 `
            --collation-server=utf8mb4_unicode_ci | Out-Null
    }
    $startedContainers.Add($mysqlContainer)

    Invoke-CheckedNative -Description 'Starting isolated Redis' -Command {
        & docker run --name $redisContainer --detach `
            --network $networkName `
            --publish '127.0.0.1::6379' `
            $RedisImage redis-server --requirepass $redisPassword | Out-Null
    }
    $startedContainers.Add($redisContainer)

    Invoke-CheckedNative -Description 'Starting isolated Nacos' -Command {
        & docker run --name $nacosContainer --detach `
            --network $networkName `
            --env 'MODE=standalone' `
            --env 'NACOS_AUTH_ENABLE=false' `
            --env 'JVM_XMS=128m' `
            --env 'JVM_XMX=256m' `
            --env 'JVM_XMN=64m' `
            --publish '127.0.0.1::8848' `
            $NacosImage | Out-Null
    }
    $startedContainers.Add($nacosContainer)

    if ($IncludeSandboxJudge) {
        $dockerPlatform = (& docker info --format '{{.OSType}}/{{.Architecture}}').Trim()
        if ($LASTEXITCODE -ne 0 -or $dockerPlatform -ne 'linux/x86_64') {
            throw "The checked-in sandbox requires Docker Linux/x86_64, but the engine reported '$dockerPlatform'."
        }
        Write-Host "Starting the checked-in Go Judge sandbox on port $SandboxPort..."
        Invoke-CheckedNative -Description 'Starting isolated judge sandbox' -Command {
            $sandboxDockerArgs = @(
                'run', '--name', $sandboxContainer, '--detach', '--privileged',
                '--network', $networkName,
                '--publish', "127.0.0.1:${SandboxPort}:5050"
            )
            if ($IncludeRollback) {
                $sandboxDockerArgs += @('--publish', "127.0.0.1:${judgePort}:${judgePort}")
            }
            $sandboxDockerArgs += @(
                '--mount', "type=bind,source=$sandboxBinary,target=/sandbox,readonly",
                '--mount', "type=bind,source=$judgeDataDirectory,target=/judge"
            )
            if ($ProductionCopyAcceptance) {
                $sandboxDockerArgs += @(
                    '--mount', "type=bind,source=$productionTestCaseDirectory,target=/judge/test_case,readonly"
                )
            }
            $sandboxDockerArgs += @(
                '--entrypoint', '/sandbox',
                $SandboxRuntimeImage,
                '--silent=true', '--file-timeout=10m', '--parallelism=2'
            )
            & docker @sandboxDockerArgs | Out-Null
        }
        $startedContainers.Add($sandboxContainer)
        Wait-HttpEndpoint -Uri "http://127.0.0.1:$SandboxPort/version" `
            -Description 'Isolated judge sandbox' -Attempts 60 | Out-Null
    }

    Wait-ContainerCommand -Description 'Isolated MySQL' -Command {
        & docker exec --env "MYSQL_PWD=$mysqlPassword" $mysqlContainer `
            mysqladmin ping --user=root --silent
    }
    Wait-ContainerCommand -Description 'Isolated Redis' -Command {
        & docker exec $redisContainer redis-cli --no-auth-warning -a $redisPassword ping
    }

    $script:mysqlPort = Get-DockerHostPort -Container $mysqlContainer -ContainerPort '3306/tcp'
    $redisPort = Get-DockerHostPort -Container $redisContainer -ContainerPort '6379/tcp'
    $nacosPort = Get-DockerHostPort -Container $nacosContainer -ContainerPort '8848/tcp'
    $nacosBaseUrl = "http://127.0.0.1:$nacosPort"

    Wait-ContainerCommand -Description 'Isolated MySQL host port' -Attempts 30 -Command {
        & mysql --connect-timeout=2 --protocol=tcp --host=127.0.0.1 `
            "--port=$script:mysqlPort" --user=root --silent --skip-column-names --execute='SELECT 1'
    }

    Wait-HttpEndpoint -Uri "$nacosBaseUrl/nacos/v1/console/health/readiness" `
        -Description 'Isolated Nacos' -Attempts 180 | Out-Null

    $restoreDescription = 'checked-in HOJ schema'
    if ($ProductionCopyAcceptance) {
        $restoreDescription = 'verified production HOJ database copy'
    }
    Write-Host "Restoring $restoreDescription into isolated MySQL on port $script:mysqlPort..."
    Invoke-CheckedNative -Description "Restoring $restoreDescription" -Command {
        & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" --user=root `
            --default-character-set=utf8mb4 --execute="source $databaseRestoreSourcePath"
    }

    $historicalSubmitId = $null
    $historicalAcceptedCode = $null
    $selectedProductionTestCaseDirectory = $null
    if ($ProductionCopyAcceptance) {
        $md5 = [Security.Cryptography.MD5]::Create()
        try {
            $stagingPasswordHash = ([BitConverter]::ToString(
                    $md5.ComputeHash([Text.Encoding]::UTF8.GetBytes($stagingPassword))
                )).Replace('-', '').ToLowerInvariant()
        }
        finally {
            $md5.Dispose()
        }
        $seedStagingAccountSql = "INSERT INTO hoj.user_info (uuid, username, password, nickname, status, gmt_create, gmt_modified) VALUES ('$stagingUid', '$stagingUsername', '$stagingPasswordHash', 'Production-copy acceptance', 0, NOW(), NOW()); INSERT INTO hoj.user_record (uid, gmt_create, gmt_modified) VALUES ('$stagingUid', NOW(), NOW()); INSERT INTO hoj.user_role (uid, role_id, gmt_create, gmt_modified) VALUES ('$stagingUid', 1000, NOW(), NOW());"
        Invoke-CheckedNative -Description 'Creating a staging-only acceptance account in the disposable database' -Command {
            & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" --user=root `
                --default-character-set=utf8mb4 --execute=$seedStagingAccountSql
        }

        $candidateSql = "SELECT CONCAT(p.id, CHAR(9), MAX(j.submit_id)) FROM hoj.problem p INNER JOIN hoj.judge j ON j.pid=p.id WHERE p.is_remote=0 AND p.auth=1 AND p.judge_mode='default' AND p.is_file_io=0 AND p.time_limit BETWEEN 100 AND 3000 AND j.status=0 AND j.language='C++' AND j.code IS NOT NULL AND CHAR_LENGTH(j.code)>0 GROUP BY p.id ORDER BY MAX(j.submit_id) DESC LIMIT 1000;"
        $candidateRows = & mysql --connect-timeout=5 --protocol=tcp --host=127.0.0.1 `
            "--port=$script:mysqlPort" --user=root --batch --skip-column-names --raw --execute=$candidateSql
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to query a representative production problem from the disposable database.'
        }
        foreach ($candidateRow in $candidateRows) {
            $parts = $candidateRow.ToString().Split("`t")
            if ($parts.Count -ne 2) {
                continue
            }
            $candidateProblemId = [long]$parts[0]
            $candidateSubmitId = [long]$parts[1]
            $candidateDirectory = Join-Path $productionTestCaseDirectory "problem_$candidateProblemId"
            if ((Test-Path -LiteralPath (Join-Path $candidateDirectory 'info') -PathType Leaf) -and
                    @(Get-ChildItem -LiteralPath $candidateDirectory -Filter '*.in' -File -ErrorAction SilentlyContinue).Count -gt 0 -and
                    @(Get-ChildItem -LiteralPath $candidateDirectory -Filter '*.out' -File -ErrorAction SilentlyContinue).Count -gt 0) {
                $problemId = $candidateProblemId
                $historicalSubmitId = $candidateSubmitId
                $selectedProductionTestCaseDirectory = $candidateDirectory
                break
            }
        }
        if ($null -eq $historicalSubmitId) {
            throw 'No public local C++ Accepted submission with matching production test-case files was found.'
        }
        $problemDisplayId = Invoke-MySqlScalar -Sql "SELECT problem_id FROM hoj.problem WHERE id=$problemId;"
        $acceptedCodeBase64 = Invoke-MySqlScalar -Sql "SELECT REPLACE(REPLACE(TO_BASE64(code), CHAR(10), ''), CHAR(13), '') FROM hoj.judge WHERE submit_id=$historicalSubmitId;"
        $historicalAcceptedCode = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($acceptedCodeBase64))
        if ([string]::IsNullOrWhiteSpace($problemDisplayId) -or [string]::IsNullOrWhiteSpace($historicalAcceptedCode)) {
            throw 'The representative production problem or Accepted source could not be loaded from the disposable database.'
        }
        $productionTestCaseSignatureBefore = Get-DirectoryContentSignature -Directory $selectedProductionTestCaseDirectory
        Write-Host "Selected production-copy problem id $problemId and historical submission id $historicalSubmitId for isolated rejudging."
    }

    $judgeDatabaseHost = '127.0.0.1'
    $judgeDatabasePort = $script:mysqlPort
    if ($IncludePersistentJudge) {
        $judgeDatabaseHost = $mysqlContainer
        $judgeDatabasePort = 3306
    }

    $nacosConfig = @'
hoj:
  jwt:
    secret: {0}
    expire: 86400
    checkRefreshExpire: 43200
  judge:
    token: {1}
  db:
    host: 127.0.0.1
    public-host: {6}
    port: {2}
    public-port: {7}
    name: hoj
    username: root
    password: {3}
  redis:
    host: 127.0.0.1
    port: {4}
    password: {5}
'@ -f $jwtSecret, $judgeToken, $script:mysqlPort, $mysqlPassword, $redisPort, $redisPassword, $judgeDatabaseHost, $judgeDatabasePort

    $publishResponse = Invoke-WebRequest -UseBasicParsing -Method Post `
        -Uri "$nacosBaseUrl/nacos/v1/cs/configs" `
        -Body @{ dataId = 'hoj-prod.yml'; group = 'DEFAULT_GROUP'; type = 'yaml'; content = $nacosConfig } `
        -TimeoutSec 10
    if ($publishResponse.Content.Trim() -ne 'true') {
        throw "Nacos rejected the temporary hoj-prod.yml configuration: $($publishResponse.Content)"
    }
    if ($IncludePersistentJudge) {
        $switchPublishResponse = Invoke-WebRequest -UseBasicParsing -Method Post `
            -Uri "$nacosBaseUrl/nacos/v1/cs/configs" `
            -Body @{
                dataId = 'hoj-switch.yml'
                group = 'DEFAULT_GROUP'
                type = 'yaml'
                content = "defaultSubmitInterval: 0`nopenPublicJudge: true`n"
            } -TimeoutSec 10
        if ($switchPublishResponse.Content.Trim() -ne 'true') {
            throw "Nacos rejected the temporary hoj-switch.yml configuration: $($switchPublishResponse.Content)"
        }
    }

    $schemaSignatureBefore = Invoke-MySqlScalar -Sql "SET SESSION group_concat_max_len=10000000; SELECT SHA2(GROUP_CONCAT(CONCAT(table_name, ':', column_name, ':', column_type, ':', is_nullable, ':', IFNULL(column_default, '<NULL>'), ':', extra) ORDER BY table_name, ordinal_position SEPARATOR '|'), 256) FROM information_schema.columns WHERE table_schema='hoj';"
    $tableCountBefore = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hoj' AND table_type='BASE TABLE';")
    $rootPasswordBefore = Invoke-MySqlScalar -Sql "SELECT password FROM hoj.user_info WHERE username='root';"
    $sessionCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.session;')
    $judgeServerCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge_server;')
    $problemCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.problem;')
    $judgeCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge;')
    $problemCaseCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.problem_case;')
    $judgeCaseCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge_case;')
    $userAcProblemCountBefore = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.user_acproblem;')

    $commonEnvironment = @{
        NACOS_URL = "127.0.0.1:$nacosPort"
        NACOS_USERNAME = 'nacos'
        NACOS_PASSWORD = 'nacos'
        MYSQL_HOST = '127.0.0.1'
        MYSQL_PORT = "$script:mysqlPort"
        MYSQL_PUBLIC_HOST = $judgeDatabaseHost
        MYSQL_PUBLIC_PORT = "$judgeDatabasePort"
        MYSQL_USERNAME = 'root'
        MYSQL_ROOT_PASSWORD = $mysqlPassword
        MYSQL_DATABASE_NAME = 'hoj'
        REDIS_HOST = '127.0.0.1'
        REDIS_PORT = "$redisPort"
        REDIS_PASSWORD = $redisPassword
        JWT_TOKEN_SECRET = $jwtSecret
        JUDGE_TOKEN = $judgeToken
        SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES = 'true'
        SPRING_PROFILES_ACTIVE = 'prod'
        LOGGING_FILE_PATH = (Join-Path $workDirectory 'logs')
        SANDBOX_BASE_URL = "http://127.0.0.1:$SandboxPort"
    }
    foreach ($entry in $commonEnvironment.GetEnumerator()) {
        Set-ProcessEnvironment -Name $entry.Key -Value $entry.Value
    }

    Set-ProcessEnvironment -Name 'BACKEND_SERVER_PORT' -Value "$backendPort"
    Set-ProcessEnvironment -Name 'OPEN_REMOTE_JUDGE' -Value 'false'
    Set-ProcessEnvironment -Name 'STARTUP_DATABASE_INITIALIZATION_ENABLED' -Value 'false'
    Set-ProcessEnvironment -Name 'PASSWORD_BCRYPT_WRITE_ENABLED' -Value 'false'
    Set-ProcessEnvironment -Name 'PASSWORD_UPGRADE_ON_LOGIN_ENABLED' -Value 'false'
    Set-ProcessEnvironment -Name 'FORCED_UPDATE_REMOTE_JUDGE_ACCOUNT' -Value 'false'

    Write-Host "Starting the upgraded backend JAR on port $backendPort..."
    $backendProcess = Start-Process -FilePath $JavaPath `
        -ArgumentList @('-Dfile.encoding=UTF-8', '-Xms128m', '-Xmx512m', '-jar', "`"$backendJar`"") `
        -WorkingDirectory $workDirectory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $backendStdout -RedirectStandardError $backendStderr

    Wait-HttpEndpoint -Uri "http://127.0.0.1:$backendPort/api/get-website-config" `
        -Description 'HOJ backend' -Process $backendProcess -Attempts 180 | Out-Null

    $websiteConfig = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/get-website-config" -TimeoutSec 10
    Assert-CommonResultSuccess -Result $websiteConfig -Description 'Website configuration smoke test'

    $languages = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/languages?all=true" -TimeoutSec 10
    Assert-CommonResultSuccess -Result $languages -Description 'Language list smoke test'
    if ($null -eq $languages.data -or @($languages.data).Count -lt 1) {
        throw 'Language list smoke test returned no language data.'
    }

    $announcements = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/get-common-announcement?limit=10&currentPage=1" -TimeoutSec 10
    Assert-CommonResultSuccess -Result $announcements -Description 'Announcement list smoke test'

    $loginBody = @{
        username = $loginUsername
        password = $loginPassword
    } | ConvertTo-Json -Compress
    $loginResponse = Invoke-WebRequest -UseBasicParsing -Method Post `
        -Uri "http://127.0.0.1:$backendPort/api/login" `
        -ContentType 'application/json; charset=utf-8' `
        -Body $loginBody -TimeoutSec 10
    $loginResult = $loginResponse.Content | ConvertFrom-Json
    Assert-CommonResultSuccess -Result $loginResult -Description 'Legacy MD5 login smoke test'
    $authorizationToken = [string]$loginResponse.Headers['Authorization']
    if ([string]::IsNullOrWhiteSpace($authorizationToken)) {
        throw 'Legacy MD5 login succeeded without returning an Authorization token.'
    }

    if ($ProductionCopyAcceptance) {
        $productionAuthHeaders = @{ Authorization = $authorizationToken }
        $historicalProblemDetail = Invoke-RestMethod `
            -Uri "http://127.0.0.1:$backendPort/api/get-problem-detail?problemId=$([Uri]::EscapeDataString($problemDisplayId))" `
            -Headers $productionAuthHeaders -TimeoutSec 10
        Assert-CommonResultSuccess -Result $historicalProblemDetail -Description 'Historical production problem detail'
        $historicalSubmissionDetail = Invoke-RestMethod `
            -Uri "http://127.0.0.1:$backendPort/api/get-submission-detail?submitId=$historicalSubmitId" `
            -Headers $productionAuthHeaders -TimeoutSec 10
        Assert-CommonResultSuccess -Result $historicalSubmissionDetail -Description 'Historical production submission detail'
        if ([int]$historicalSubmissionDetail.data.submission.status -ne 0) {
            throw 'Historical production Accepted submission was not readable with its persisted status.'
        }
        $productionContestList = Invoke-RestMethod `
            -Uri "http://127.0.0.1:$backendPort/api/get-contest-list?limit=10&currentPage=1" -TimeoutSec 10
        Assert-CommonResultSuccess -Result $productionContestList -Description 'Historical production contest list'
        $productionGroupList = Invoke-RestMethod `
            -Uri "http://127.0.0.1:$backendPort/api/get-group-list?limit=10&currentPage=1&onlyMine=false" -TimeoutSec 10
        Assert-CommonResultSuccess -Result $productionGroupList -Description 'Historical production group list'
    }

    Set-ProcessEnvironment -Name 'JUDGE_SERVER_PORT' -Value "$judgePort"
    Set-ProcessEnvironment -Name 'JUDGE_SERVER_IP' -Value '127.0.0.1'
    Set-ProcessEnvironment -Name 'JUDGE_SERVER_NAME' -Value $judgeServerName
    Set-ProcessEnvironment -Name 'REMOTE_JUDGE_OPEN' -Value 'false'
    Set-ProcessEnvironment -Name 'MAX_TASK_NUM' -Value '2'
    Set-ProcessEnvironment -Name 'REMOTE_JUDGE_MAX_TASK_NUM' -Value '2'

    Write-Host "Starting the upgraded judge server JAR on port $judgePort..."
    if ($IncludePersistentJudge) {
        Invoke-CheckedNative -Description 'Starting containerized judge server' -Command {
            $judgeDockerArgs = @('run', '--name', $judgeRuntimeContainer, '--detach')
            if ($IncludeRollback) {
                $judgeDockerArgs += @('--network', "container:$sandboxContainer")
            }
            else {
                $judgeDockerArgs += @(
                    '--network', $networkName,
                    '--publish', "127.0.0.1:${judgePort}:${judgePort}"
                )
            }
            $sandboxBaseUrl = "http://${sandboxContainer}:5050"
            if ($IncludeRollback) {
                $sandboxBaseUrl = 'http://127.0.0.1:5050'
            }
            $judgeDockerArgs += @(
                '--env', "JUDGE_SERVER_PORT=$judgePort",
                '--env', 'JUDGE_SERVER_IP=127.0.0.1',
                '--env', "JUDGE_SERVER_NAME=$judgeServerName",
                '--env', 'REMOTE_JUDGE_OPEN=false',
                '--env', 'MAX_TASK_NUM=2',
                '--env', 'REMOTE_JUDGE_MAX_TASK_NUM=2',
                '--env', "NACOS_URL=${nacosContainer}:8848",
                '--env', 'NACOS_USERNAME=nacos',
                '--env', 'NACOS_PASSWORD=nacos',
                '--env', 'SPRING_MAIN_ALLOW_CIRCULAR_REFERENCES=true',
                '--env', 'SPRING_PROFILES_ACTIVE=prod',
                '--env', "SANDBOX_BASE_URL=$sandboxBaseUrl",
                '--env', 'LOGGING_FILE_PATH=/tmp/hoj-logs',
                '--mount', "type=bind,source=$judgeJar,target=/app/hoj-judgeServer.jar,readonly",
                '--mount', "type=bind,source=$judgeDataDirectory,target=/judge"
            )
            if ($ProductionCopyAcceptance) {
                $judgeDockerArgs += @(
                    '--mount', "type=bind,source=$productionTestCaseDirectory,target=/judge/test_case,readonly"
                )
            }
            $judgeDockerArgs += @(
                '--entrypoint', $JudgeContainerJavaPath,
                $JudgeRuntimeImage,
                '-Dfile.encoding=UTF-8', '-Xms128m', '-Xmx512m', '-jar', '/app/hoj-judgeServer.jar'
            )
            & docker @judgeDockerArgs | Out-Null
        }
        $startedContainers.Add($judgeRuntimeContainer)
        Wait-DockerHttpEndpoint -Uri "http://127.0.0.1:$judgePort/actuator/health" `
            -Container $judgeRuntimeContainer -Description 'HOJ judge server health endpoint' -Attempts 180 | Out-Null
    }
    else {
        $judgeProcess = Start-Process -FilePath $JavaPath `
            -ArgumentList @('-Dfile.encoding=UTF-8', '-Xms128m', '-Xmx512m', '-jar', "`"$judgeJar`"") `
            -WorkingDirectory $workDirectory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $judgeStdout -RedirectStandardError $judgeStderr

        Wait-HttpEndpoint -Uri "http://127.0.0.1:$judgePort/actuator/health" `
            -Description 'HOJ judge server health endpoint' -Process $judgeProcess -Attempts 180 | Out-Null
    }

    $judgeVersion = Invoke-RestMethod -Uri "http://127.0.0.1:$judgePort/version" -TimeoutSec 10
    Assert-CommonResultSuccess -Result $judgeVersion -Description 'Judge server version smoke test'

    $registration = Wait-NacosRegistration -NacosBaseUrl $nacosBaseUrl -ServiceName 'hoj-judgeserver'
    if ($null -eq $registration) {
        $legacyRegistration = Get-NacosInstances -NacosBaseUrl $nacosBaseUrl -ServiceName 'hoj-judge-server'
        if ($null -ne $legacyRegistration.hosts -and @($legacyRegistration.hosts).Count -gt 0) {
            throw "Judge server registered as 'hoj-judge-server', but the backend discovers 'hoj-judgeserver'."
        }
        throw "Judge server did not register in Nacos as 'hoj-judgeserver'."
    }

    $sandboxJudgeResult = $null
    $persistentSubmissionResults = $null
    if ($IncludeSandboxJudge -and -not $IncludePersistentJudge) {
        Write-Host 'Running backend-to-sandbox C++ online-judge flow...'
        $problemId = 900000000000L + [Convert]::ToInt64($runId.Substring(0, 6), 16)
        $seedProblemSql = "INSERT INTO hoj.problem (id, problem_id, title, author, type, time_limit, memory_limit, stack_limit, is_remote, auth, judge_mode, is_remove_end_blank, is_file_io) VALUES ($problemId, 'STAGE4-$runId', 'Stage 4 isolated sandbox smoke', 'root', 0, 1000, 256, 128, 0, 1, 'default', 1, 0);"
        Invoke-CheckedNative -Description 'Seeding isolated stage-four problem' -Command {
            & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" `
                --user=root --default-character-set=utf8mb4 --execute=$seedProblemSql
        }

        $cppCode = "#include <iostream>`nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b;}"
        $testJudgeBody = @{
            pid = $problemId
            type = 'public'
            code = $cppCode
            language = 'C++'
            userInput = '20 22'
            expectedOutput = '42'
            isRemoteJudge = $false
            mode = 'text/x-c++src'
        } | ConvertTo-Json -Depth 5 -Compress
        $authHeaders = @{ Authorization = $authorizationToken }
        $submitTestResponse = Invoke-RestMethod -Method Post `
            -Uri "http://127.0.0.1:$backendPort/api/submit-problem-test-judge" `
            -Headers $authHeaders -ContentType 'application/json; charset=utf-8' `
            -Body $testJudgeBody -TimeoutSec 20
        Assert-CommonResultSuccess -Result $submitTestResponse -Description 'Online judge submission smoke test'
        $testJudgeKey = [string]$submitTestResponse.data
        if ([string]::IsNullOrWhiteSpace($testJudgeKey)) {
            throw 'Online judge submission did not return a result key.'
        }

        for ($attempt = 1; $attempt -le 90; $attempt++) {
            $candidate = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$backendPort/api/get-test-judge-result?testJudgeKey=$testJudgeKey" `
                -Headers $authHeaders -TimeoutSec 10
            Assert-CommonResultSuccess -Result $candidate -Description 'Online judge result polling'
            if ($null -ne $candidate.data -and [int]$candidate.data.status -ne 5) {
                $sandboxJudgeResult = $candidate
                break
            }
            Start-Sleep -Seconds 1
        }
        if ($null -eq $sandboxJudgeResult) {
            throw 'Online judge result remained pending for 90 seconds.'
        }
        if ([int]$sandboxJudgeResult.data.status -ne 0) {
            $serializedResult = $sandboxJudgeResult | ConvertTo-Json -Depth 8 -Compress
            throw "C++ online judge did not return Accepted: $serializedResult"
        }
        if ([string]$sandboxJudgeResult.data.userOutput.Trim() -ne '42') {
            $serializedResult = $sandboxJudgeResult | ConvertTo-Json -Depth 8 -Compress
            throw "C++ online judge returned unexpected output: $serializedResult"
        }
    }

    if ($IncludePersistentJudge) {
        Write-Host 'Running persistent Accepted, Wrong Answer, Compile Error, and Time Limit submissions...'
        $acceptedCode = $historicalAcceptedCode
        if (-not $ProductionCopyAcceptance) {
            $problemId = 910000000000L + [Convert]::ToInt64($runId.Substring(0, 6), 16)
            $problemDisplayId = "STAGE5-$runId"
            $acceptedCode = "#include <iostream>`nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b;return 0;}"
            $seedPersistentProblemSql = "INSERT INTO hoj.problem (id, problem_id, title, author, type, time_limit, memory_limit, stack_limit, is_remote, auth, judge_mode, judge_case_mode, is_remove_end_blank, is_upload_case, case_version, is_file_io) VALUES ($problemId, '$problemDisplayId', 'Stage 5 persistent judge smoke', 'root', 0, 200, 256, 128, 0, 1, 'default', 'default', 1, 0, 'stage5-$runId', 0); INSERT INTO hoj.problem_case (pid, input, output, score, status, group_num) VALUES ($problemId, '20 22', '42', 100, 0, 1);"
            Invoke-CheckedNative -Description 'Seeding isolated stage-five problem and test case' -Command {
                & mysql --protocol=tcp --host=127.0.0.1 "--port=$script:mysqlPort" `
                    --user=root --default-character-set=utf8mb4 --execute=$seedPersistentProblemSql
            }
        }

        $submissionCases = @(
            [pscustomobject]@{
                Name = 'Accepted'
                ExpectedStatus = 0
                Code = $acceptedCode
            },
            [pscustomobject]@{
                Name = 'Wrong Answer'
                ExpectedStatus = -1
                Code = "#include <iostream>`nint main(){std::cout<<`"__HOJ_STAGE_ACCEPTANCE_WRONG__`";return 0;}"
            },
            [pscustomobject]@{
                Name = 'Compile Error'
                ExpectedStatus = -2
                Code = "#include <iostream>`nint main(){ this intentionally invalid C++ source must fail compilation immediately; }"
            },
            [pscustomobject]@{
                Name = 'Time Limit Exceeded'
                ExpectedStatus = 1
                Code = "#include <cstdint>`nint main(){volatile std::uint64_t value=0;while(true){value++;}return 0;}"
            }
        )
        $persistentSubmissionResults = [ordered]@{}
        $authHeaders = @{ Authorization = $authorizationToken }
        foreach ($submissionCase in $submissionCases) {
            $submitBody = @{
                pid = $problemDisplayId
                language = 'C++'
                code = $submissionCase.Code
                cid = 0
                isRemote = $false
            } | ConvertTo-Json -Depth 5 -Compress
            $submitResponse = Invoke-RestMethod -Method Post `
                -Uri "http://127.0.0.1:$backendPort/api/submit-problem-judge" `
                -Headers $authHeaders -ContentType 'application/json; charset=utf-8' `
                -Body $submitBody -TimeoutSec 20
            Assert-CommonResultSuccess -Result $submitResponse -Description "$($submissionCase.Name) submission"
            $submitId = [long]$submitResponse.data.submitId
            if ($submitId -le 0) {
                throw "$($submissionCase.Name) submission did not return a valid submitId."
            }

            $finalStatus = $null
            for ($attempt = 1; $attempt -le 120; $attempt++) {
                $status = [int](Invoke-MySqlScalar -Sql "SELECT status FROM hoj.judge WHERE submit_id=$submitId;")
                if (@(5, 6, 7, 9) -notcontains $status) {
                    $finalStatus = $status
                    break
                }
                Start-Sleep -Seconds 1
            }
            if ($null -eq $finalStatus) {
                throw "$($submissionCase.Name) submission remained active for 120 seconds."
            }
            if ($finalStatus -ne [int]$submissionCase.ExpectedStatus) {
                $errorMessage = Invoke-MySqlScalar -Sql "SELECT COALESCE(error_message, '') FROM hoj.judge WHERE submit_id=$submitId;"
                throw "$($submissionCase.Name) submission returned status $finalStatus instead of $($submissionCase.ExpectedStatus): $errorMessage"
            }

            $submissionDetail = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$backendPort/api/get-submission-detail?submitId=$submitId" `
                -Headers $authHeaders -TimeoutSec 10
            Assert-CommonResultSuccess -Result $submissionDetail -Description "$($submissionCase.Name) submission detail"
            if ([int]$submissionDetail.data.submission.status -ne $finalStatus) {
                throw "$($submissionCase.Name) submission detail did not expose the persisted final status."
            }
            $persistentSubmissionResults[$submissionCase.Name] = $submitId
        }
    }

    $rollbackAcceptedSubmitId = $null
    if ($IncludeRollback) {
        Write-Host 'Stopping upgraded services and starting the rollback baseline on the same database...'

        if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
            Stop-Process -Id $backendProcess.Id -Force
            $backendProcess.WaitForExit(10000) | Out-Null
        }
        $backendProcess = $null

        Invoke-CheckedNative -Description 'Stopping upgraded containerized judge server' -Command {
            & docker rm --force $judgeRuntimeContainer | Out-Null
        }
        $startedContainers.Remove($judgeRuntimeContainer) | Out-Null

        Invoke-CheckedNative -Description 'Starting rollback judge server' -Command {
            $rollbackJudgeDockerArgs = @(
                'run', '--name', $rollbackJudgeRuntimeContainer, '--detach',
                '--network', "container:$sandboxContainer",
                '--env', "JUDGE_SERVER_PORT=$judgePort",
                '--env', 'JUDGE_SERVER_IP=127.0.0.1',
                '--env', "JUDGE_SERVER_NAME=$judgeServerName",
                '--env', 'REMOTE_JUDGE_OPEN=false',
                '--env', 'MAX_TASK_NUM=2',
                '--env', 'REMOTE_JUDGE_MAX_TASK_NUM=2',
                '--env', "NACOS_URL=${nacosContainer}:8848",
                '--env', 'NACOS_USERNAME=nacos',
                '--env', 'NACOS_PASSWORD=nacos',
                '--env', 'SPRING_PROFILES_ACTIVE=prod',
                '--env', 'LOGGING_FILE_PATH=/tmp/hoj-logs',
                '--mount', "type=bind,source=$RollbackJudgeJar,target=/app/hoj-judgeServer.jar,readonly",
                '--mount', "type=bind,source=$judgeDataDirectory,target=/judge"
            )
            if ($ProductionCopyAcceptance) {
                $rollbackJudgeDockerArgs += @(
                    '--mount', "type=bind,source=$productionTestCaseDirectory,target=/judge/test_case,readonly"
                )
            }
            $rollbackJudgeDockerArgs += @(
                '--entrypoint', $JudgeContainerJavaPath,
                $JudgeRuntimeImage,
                '-Dfile.encoding=UTF-8', '-Xms128m', '-Xmx512m', '-jar', '/app/hoj-judgeServer.jar'
            )
            & docker @rollbackJudgeDockerArgs | Out-Null
        }
        $startedContainers.Add($rollbackJudgeRuntimeContainer)
        Wait-DockerHttpEndpoint -Uri "http://127.0.0.1:$judgePort/actuator/health" `
            -Container $rollbackJudgeRuntimeContainer -Description 'Rollback judge server health endpoint' -Attempts 180 | Out-Null
        $rollbackJudgeVersion = Invoke-RestMethod -Uri "http://127.0.0.1:$judgePort/version" -TimeoutSec 10
        Assert-CommonResultSuccess -Result $rollbackJudgeVersion -Description 'Rollback judge server version smoke test'

        $backendProcess = Start-Process -FilePath $JavaPath `
            -ArgumentList @('-Dfile.encoding=UTF-8', '-Xms128m', '-Xmx512m', '-jar', "`"$RollbackBackendJar`"") `
            -WorkingDirectory $workDirectory -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $rollbackBackendStdout -RedirectStandardError $rollbackBackendStderr
        Wait-HttpEndpoint -Uri "http://127.0.0.1:$backendPort/api/get-website-config" `
            -Description 'Rollback HOJ backend' -Process $backendProcess -Attempts 180 | Out-Null

        $rollbackWebsiteConfig = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/get-website-config" -TimeoutSec 10
        Assert-CommonResultSuccess -Result $rollbackWebsiteConfig -Description 'Rollback website configuration smoke test'
        $rollbackLanguages = Invoke-RestMethod -Uri "http://127.0.0.1:$backendPort/api/languages?all=true" -TimeoutSec 10
        Assert-CommonResultSuccess -Result $rollbackLanguages -Description 'Rollback language list smoke test'

        $rollbackLoginResponse = Invoke-WebRequest -UseBasicParsing -Method Post `
            -Uri "http://127.0.0.1:$backendPort/api/login" `
            -ContentType 'application/json; charset=utf-8' `
            -Body $loginBody -TimeoutSec 10
        $rollbackLoginResult = $rollbackLoginResponse.Content | ConvertFrom-Json
        Assert-CommonResultSuccess -Result $rollbackLoginResult -Description 'Rollback legacy MD5 login smoke test'
        $rollbackAuthorizationToken = [string]$rollbackLoginResponse.Headers['Authorization']
        if ([string]::IsNullOrWhiteSpace($rollbackAuthorizationToken)) {
            throw 'Rollback legacy MD5 login succeeded without returning an Authorization token.'
        }
        $rollbackAuthHeaders = @{ Authorization = $rollbackAuthorizationToken }

        if ($ProductionCopyAcceptance) {
            $rollbackHistoricalProblem = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$backendPort/api/get-problem-detail?problemId=$([Uri]::EscapeDataString($problemDisplayId))" `
                -Headers $rollbackAuthHeaders -TimeoutSec 10
            Assert-CommonResultSuccess -Result $rollbackHistoricalProblem -Description 'Rollback read of historical production problem'
            $rollbackHistoricalSubmission = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$backendPort/api/get-submission-detail?submitId=$historicalSubmitId" `
                -Headers $rollbackAuthHeaders -TimeoutSec 10
            Assert-CommonResultSuccess -Result $rollbackHistoricalSubmission -Description 'Rollback read of historical production submission'
        }

        $rollbackReadExpectations = [ordered]@{
            'Accepted' = 0
            'Wrong Answer' = -1
            'Compile Error' = -2
            'Time Limit Exceeded' = 1
        }
        foreach ($entry in $rollbackReadExpectations.GetEnumerator()) {
            $upgradedSubmitId = [long]$persistentSubmissionResults[$entry.Key]
            $upgradedSubmissionDetail = Invoke-RestMethod `
                -Uri "http://127.0.0.1:$backendPort/api/get-submission-detail?submitId=$upgradedSubmitId" `
                -Headers $rollbackAuthHeaders -TimeoutSec 10
            Assert-CommonResultSuccess -Result $upgradedSubmissionDetail -Description "Rollback read of upgraded $($entry.Key) submission"
            if ([int]$upgradedSubmissionDetail.data.submission.status -ne [int]$entry.Value) {
                throw "Rollback backend could not read the $($entry.Key) result written by the upgraded services."
            }
        }

        $rollbackAcceptedCode = "#include <iostream>`nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b;return 0;}"
        if ($ProductionCopyAcceptance) {
            $rollbackAcceptedCode = $historicalAcceptedCode
        }
        $rollbackSubmitBody = @{
            pid = $problemDisplayId
            language = 'C++'
            code = $rollbackAcceptedCode
            cid = 0
            isRemote = $false
        } | ConvertTo-Json -Depth 5 -Compress
        $rollbackSubmitResponse = Invoke-RestMethod -Method Post `
            -Uri "http://127.0.0.1:$backendPort/api/submit-problem-judge" `
            -Headers $rollbackAuthHeaders -ContentType 'application/json; charset=utf-8' `
            -Body $rollbackSubmitBody -TimeoutSec 20
        Assert-CommonResultSuccess -Result $rollbackSubmitResponse -Description 'Rollback Accepted submission'
        $rollbackAcceptedSubmitId = [long]$rollbackSubmitResponse.data.submitId
        if ($rollbackAcceptedSubmitId -le 0) {
            throw 'Rollback Accepted submission did not return a valid submitId.'
        }

        $rollbackFinalStatus = $null
        for ($attempt = 1; $attempt -le 120; $attempt++) {
            $status = [int](Invoke-MySqlScalar -Sql "SELECT status FROM hoj.judge WHERE submit_id=$rollbackAcceptedSubmitId;")
            if (@(5, 6, 7, 9) -notcontains $status) {
                $rollbackFinalStatus = $status
                break
            }
            Start-Sleep -Seconds 1
        }
        if ($null -eq $rollbackFinalStatus) {
            throw 'Rollback Accepted submission remained active for 120 seconds.'
        }
        if ($rollbackFinalStatus -ne 0) {
            $rollbackErrorMessage = Invoke-MySqlScalar -Sql "SELECT COALESCE(error_message, '') FROM hoj.judge WHERE submit_id=$rollbackAcceptedSubmitId;"
            throw "Rollback Accepted submission returned status $rollbackFinalStatus instead of 0: $rollbackErrorMessage"
        }
        $rollbackSubmissionDetail = Invoke-RestMethod `
            -Uri "http://127.0.0.1:$backendPort/api/get-submission-detail?submitId=$rollbackAcceptedSubmitId" `
            -Headers $rollbackAuthHeaders -TimeoutSec 10
        Assert-CommonResultSuccess -Result $rollbackSubmissionDetail -Description 'Rollback Accepted submission detail'
        if ([int]$rollbackSubmissionDetail.data.submission.status -ne 0) {
            throw 'Rollback submission detail did not expose the persisted Accepted status.'
        }
    }

    $schemaSignatureAfter = Invoke-MySqlScalar -Sql "SET SESSION group_concat_max_len=10000000; SELECT SHA2(GROUP_CONCAT(CONCAT(table_name, ':', column_name, ':', column_type, ':', is_nullable, ':', IFNULL(column_default, '<NULL>'), ':', extra) ORDER BY table_name, ordinal_position SEPARATOR '|'), 256) FROM information_schema.columns WHERE table_schema='hoj';"
    $tableCountAfter = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hoj' AND table_type='BASE TABLE';")
    $rootPasswordAfter = Invoke-MySqlScalar -Sql "SELECT password FROM hoj.user_info WHERE username='root';"
    $sessionCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.session;')
    $judgeServerCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge_server;')
    $problemCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.problem;')
    $judgeCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge;')
    $problemCaseCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.problem_case;')
    $judgeCaseCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.judge_case;')
    $userAcProblemCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COUNT(*) FROM hoj.user_acproblem;')
    $judgeTaskCountAfter = -1
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $judgeTaskCountAfter = [int](Invoke-MySqlScalar -Sql 'SELECT COALESCE(SUM(task_number), 0) FROM hoj.judge_server;')
        if ($judgeTaskCountAfter -eq 0) {
            break
        }
        Start-Sleep -Seconds 1
    }

    if ($tableCountBefore -ne 48 -or $tableCountAfter -ne $tableCountBefore) {
        throw "Database table count changed or was unexpected: before=$tableCountBefore, after=$tableCountAfter."
    }
    if ($schemaSignatureAfter -ne $schemaSignatureBefore) {
        throw 'Database column structure changed while running the stage-three smoke test.'
    }
    if ($rootPasswordAfter -ne $rootPasswordBefore) {
        throw 'Legacy root password hash changed even though password migration was disabled.'
    }
    $expectedSessionCount = $sessionCountBefore + 1
    if ($IncludeRollback) {
        $expectedSessionCount++
    }
    if ($sessionCountAfter -ne $expectedSessionCount) {
        throw "Login session write was unexpected: before=$sessionCountBefore, after=$sessionCountAfter."
    }
    if ($judgeServerCountAfter -ne ($judgeServerCountBefore + 1)) {
        throw "Judge server startup write was unexpected: before=$judgeServerCountBefore, after=$judgeServerCountAfter."
    }
    $expectedProblemCount = $problemCountBefore
    if ($IncludeSandboxJudge -and -not $ProductionCopyAcceptance) {
        $expectedProblemCount++
    }
    if ($problemCountAfter -ne $expectedProblemCount) {
        throw "Problem seed write was unexpected: before=$problemCountBefore, after=$problemCountAfter."
    }
    $expectedJudgeCount = $judgeCountBefore
    if ($IncludePersistentJudge) {
        $expectedJudgeCount += 4
    }
    if ($IncludeRollback) {
        $expectedJudgeCount++
    }
    if ($judgeCountAfter -ne $expectedJudgeCount) {
        throw "Persistent judge row count was unexpected: before=$judgeCountBefore, after=$judgeCountAfter."
    }
    $expectedProblemCaseCount = $problemCaseCountBefore
    $expectedJudgeCaseCount = $judgeCaseCountBefore
    $expectedUserAcProblemCount = $userAcProblemCountBefore
    if ($IncludePersistentJudge) {
        if (-not $ProductionCopyAcceptance) {
            $expectedProblemCaseCount++
            $expectedJudgeCaseCount += 3
        }
        $expectedUserAcProblemCount++
    }
    if ($IncludeRollback) {
        if (-not $ProductionCopyAcceptance) {
            $expectedJudgeCaseCount++
        }
        $expectedUserAcProblemCount++
    }
    $newProductionJudgeCaseCount = $null
    if ($ProductionCopyAcceptance) {
        $productionSubmitIds = @($persistentSubmissionResults.Values) + @($rollbackAcceptedSubmitId)
        $submitIdList = ($productionSubmitIds | ForEach-Object { [long]$_ }) -join ','
        $newProductionJudgeCaseCount = [int](Invoke-MySqlScalar -Sql "SELECT COUNT(*) FROM hoj.judge_case WHERE submit_id IN ($submitIdList);")
        if ($newProductionJudgeCaseCount -le 0) {
            throw 'Production-copy judging did not persist any real test-case results.'
        }
        $expectedJudgeCaseCount += $newProductionJudgeCaseCount
    }
    if ($problemCaseCountAfter -ne $expectedProblemCaseCount) {
        throw "Problem-case seed count was unexpected: before=$problemCaseCountBefore, after=$problemCaseCountAfter."
    }
    if ($judgeCaseCountAfter -ne $expectedJudgeCaseCount) {
        throw "Persisted judge-case count was unexpected: before=$judgeCaseCountBefore, after=$judgeCaseCountAfter."
    }
    if ($userAcProblemCountAfter -ne $expectedUserAcProblemCount) {
        throw "Accepted-problem count was unexpected: before=$userAcProblemCountBefore, after=$userAcProblemCountAfter."
    }
    if ($IncludePersistentJudge) {
        $acceptedSubmitId = [long]$persistentSubmissionResults['Accepted']
        $acceptedUid = '1'
        if ($ProductionCopyAcceptance) {
            $acceptedUid = $stagingUid
        }
        $acceptedRelationSql = "SELECT COUNT(*) FROM hoj.user_acproblem WHERE uid='$acceptedUid' AND pid=$problemId AND submit_id=$acceptedSubmitId;"
        if ($IncludeRollback) {
            $acceptedRelationSql = "SELECT COUNT(*) FROM hoj.user_acproblem uap INNER JOIN hoj.judge j ON j.submit_id=uap.submit_id WHERE uap.uid='$acceptedUid' AND uap.pid=$problemId AND j.uid='$acceptedUid' AND j.pid=$problemId AND j.status=0;"
        }
        $acceptedRelationCount = [int](Invoke-MySqlScalar -Sql $acceptedRelationSql)
        $expectedAcceptedRelationCount = 1
        if ($IncludeRollback) {
            $expectedAcceptedRelationCount++
        }
        if ($acceptedRelationCount -ne $expectedAcceptedRelationCount) {
            throw 'Accepted submission did not create the expected user_acproblem relation.'
        }
        $compileErrorMessageLength = [int](Invoke-MySqlScalar -Sql "SELECT CHAR_LENGTH(COALESCE(error_message, '')) FROM hoj.judge WHERE submit_id=$($persistentSubmissionResults['Compile Error']);")
        if ($compileErrorMessageLength -le 0) {
            throw 'Compile Error submission did not persist compiler diagnostics.'
        }
    }
    if ($judgeTaskCountAfter -ne 0) {
        throw "Judge server task counters were not released after smoke judging: total=$judgeTaskCountAfter."
    }

    $sqlHashAfter = (Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash
    if ($sqlHashAfter -ne $sqlHashBefore) {
        throw 'The checked-in hoj.sql file changed during stage-three verification.'
    }
    if ($ProductionCopyAcceptance) {
        $databaseBackupHashAfter = (Get-FileHash -LiteralPath $DatabaseBackup -Algorithm SHA256).Hash
        if ($databaseBackupHashAfter -ne $databaseBackupHashBefore) {
            throw 'The production database backup changed during isolated acceptance testing.'
        }
        $productionTestCaseSignatureAfter = Get-DirectoryContentSignature -Directory $selectedProductionTestCaseDirectory
        if ($productionTestCaseSignatureAfter -ne $productionTestCaseSignatureBefore) {
            throw 'The selected production test-case directory changed during read-only acceptance testing.'
        }
    }

    if ($ProductionCopyAcceptance) {
        Write-Host 'Production-copy isolated upgrade and rollback acceptance passed.'
        Write-Host 'Verified historical reads, real Accepted rejudging, WA/CE/TLE writes, rollback reads, and another real Accepted submission through the copied baseline JARs.'
        Write-Host "Expected disposable writes: no schema/problem/problem_case changes, five judge rows, $newProductionJudgeCaseCount judge_case rows, two user_acproblem rows, two sessions, and one replace-in-place judge_server row."
        Write-Host 'Verified the production database backup and selected production test cases remained byte-for-byte unchanged.'
    }
    elseif ($IncludeRollback) {
        Write-Host 'Stage-six isolated deployment rollback verification passed.'
        Write-Host 'Verified upgraded AC/WA/CE/TLE writes, rollback reads, legacy login, and a new Accepted submission through the baseline backend and judge server.'
        Write-Host 'Expected disposable writes: one problem, one problem_case, five judge rows, four judge_case rows, two user_acproblem rows, two sessions, and one replace-in-place judge_server row.'
    }
    elseif ($IncludePersistentJudge) {
        Write-Host 'Stage-five persistent submission verification passed.'
        Write-Host 'Verified Accepted, Wrong Answer, Compile Error, and Time Limit Exceeded through shared file test cases.'
        Write-Host 'Expected disposable writes: one problem, one problem_case, four judge rows, three judge_case rows, one user_acproblem row, one session, and one judge_server row.'
    }
    elseif ($IncludeSandboxJudge) {
        Write-Host 'Stage-four isolated sandbox judge verification passed.'
        Write-Host "Verified C++ compile/run output 42 through backend, Redis dispatch, Nacos, judge server, and Go Judge sandbox."
        Write-Host 'Expected disposable writes: one problem seed, one login session, and one judge_server registration row.'
    }
    else {
        Write-Host 'Stage-three isolated runtime smoke verification passed.'
        Write-Host "Verified 48 unchanged tables, legacy root login, $(@($languages.data).Count) languages, judge health, and Nacos registration."
        Write-Host 'Expected disposable writes: one login session and one judge_server registration row.'
    }
}
catch {
    Write-Warning $_.Exception.Message
    Show-LogTail -Path $backendStdout
    Show-LogTail -Path $backendStderr
    Show-LogTail -Path $judgeStdout
    Show-LogTail -Path $judgeStderr
    Show-LogTail -Path $rollbackBackendStdout
    Show-LogTail -Path $rollbackBackendStderr
    if ($startedContainers.Contains($judgeRuntimeContainer)) {
        Write-Warning "Last 600 lines from Docker logs for $judgeRuntimeContainer"
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker logs --tail 600 $judgeRuntimeContainer 2>&1 | ForEach-Object { Write-Host $_.ToString() }
        $ErrorActionPreference = $previousErrorAction
    }
    if ($startedContainers.Contains($rollbackJudgeRuntimeContainer)) {
        Write-Warning "Last 600 lines from Docker logs for $rollbackJudgeRuntimeContainer"
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker logs --tail 600 $rollbackJudgeRuntimeContainer 2>&1 | ForEach-Object { Write-Host $_.ToString() }
        $ErrorActionPreference = $previousErrorAction
    }
    if ($startedContainers.Contains($sandboxContainer)) {
        Write-Warning "Last 80 lines from Docker logs for $sandboxContainer"
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker logs --tail 80 $sandboxContainer 2>&1 | ForEach-Object { Write-Host $_.ToString() }
        $ErrorActionPreference = $previousErrorAction
    }
    if ($startedContainers.Contains($nacosContainer)) {
        Write-Warning "Last 80 lines from Docker logs for $nacosContainer"
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker logs --tail 80 $nacosContainer 2>&1 | ForEach-Object { Write-Host $_.ToString() }
        $ErrorActionPreference = $previousErrorAction
    }
    throw
}
finally {
    foreach ($process in @($judgeProcess, $backendProcess)) {
        if ($null -ne $process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            try {
                $process.WaitForExit(10000) | Out-Null
            }
            catch {
                # Process cleanup is best effort after validation.
            }
        }
    }

    $cleanupErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        foreach ($container in @($rollbackJudgeRuntimeContainer, $judgeRuntimeContainer, $sandboxContainer, $nacosContainer, $redisContainer, $mysqlContainer)) {
            # A failed `docker run` can create a stopped container before returning an error.
            # Every generated name includes a random run id, so unconditional best-effort removal is safe.
            & docker rm --force $container 2>$null | Out-Null
        }

        if ($networkCreated) {
            & docker network rm $networkName 2>$null | Out-Null
        }
    }
    finally {
        $ErrorActionPreference = $cleanupErrorActionPreference
    }

    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }

    if (Test-Path -LiteralPath $workDirectory) {
        $resolvedCleanupPath = [IO.Path]::GetFullPath($workDirectory)
        if (-not $resolvedCleanupPath.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean a path outside the system temp directory: $resolvedCleanupPath"
        }
        Remove-Item -LiteralPath $resolvedCleanupPath -Recurse -Force
    }
}
