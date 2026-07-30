[CmdletBinding()]
param(
    [string]$MySqlImage = 'mysql:8.0',
    [string]$DatabaseBackup,
    [string]$ExpectedDatabaseBackupSha256,
    [switch]$AllowRepositorySeedFixture
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$repositorySqlFile = [IO.Path]::GetFullPath((Join-Path $repoRoot 'sqlAndsetting\hoj.sql'))
$sqlPath = $repositorySqlFile.Replace('\', '/')
$backendPath = Join-Path $repoRoot 'hoj-springboot'
$containerName = 'hoj-db-compat-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$password = [guid]::NewGuid().ToString('N')
$containerStarted = $false
$temporaryRestoreDirectory = $null
$backupHash = $null

function Assert-CommandExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available: $Name"
    }
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Command,
        [Parameter(Mandatory = $true)][string]$Description
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Invoke-MySqlScalar {
    param(
        [Parameter(Mandatory = $true)][string]$Port,
        [Parameter(Mandatory = $true)][string]$Sql
    )

    $output = & mysql --connect-timeout=5 --protocol=tcp --host=127.0.0.1 `
        "--port=$Port" --user=root --batch --skip-column-names --execute=$Sql
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    return (($output | Select-Object -First 1).ToString().Trim())
}

Assert-CommandExists -Name 'docker'
Assert-CommandExists -Name 'mysql'
Assert-CommandExists -Name 'mvn'

$savedEnvironment = @{
    MYSQL_PWD = $env:MYSQL_PWD
    HOJ_DB_COMPAT_URL = $env:HOJ_DB_COMPAT_URL
    HOJ_DB_COMPAT_USERNAME = $env:HOJ_DB_COMPAT_USERNAME
    HOJ_DB_COMPAT_PASSWORD = $env:HOJ_DB_COMPAT_PASSWORD
}

try {
if (-not [string]::IsNullOrWhiteSpace($DatabaseBackup)) {
    if (-not (Test-Path -LiteralPath $DatabaseBackup -PathType Leaf)) {
        throw "Database backup copy was not found: $DatabaseBackup"
    }
    $databaseBackupPath = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $DatabaseBackup).Path)
    $lowerBackupName = $databaseBackupPath.ToLowerInvariant()
    if (-not ($lowerBackupName.EndsWith('.sql') -or $lowerBackupName.EndsWith('.sql.gz'))) {
        throw "Database backup must be an unencrypted .sql or .sql.gz file: $databaseBackupPath"
    }
    $backupHash = (Get-FileHash -LiteralPath $databaseBackupPath -Algorithm SHA256).Hash
    if ([string]::IsNullOrWhiteSpace($ExpectedDatabaseBackupSha256)) {
        throw 'ExpectedDatabaseBackupSha256 is required when restoring a supplied database backup.'
    }
    $expectedBackupHash = $ExpectedDatabaseBackupSha256.Trim().ToUpperInvariant()
    if ($expectedBackupHash -notmatch '^[0-9A-F]{64}$') {
        throw 'ExpectedDatabaseBackupSha256 must contain exactly 64 hexadecimal characters.'
    }
    if ($backupHash -ne $expectedBackupHash) {
        throw "Database backup SHA-256 mismatch. Expected $expectedBackupHash but found $backupHash."
    }
    $repositorySqlHash = (Get-FileHash -LiteralPath $repositorySqlFile -Algorithm SHA256).Hash
    if (-not $AllowRepositorySeedFixture -and $backupHash -eq $repositorySqlHash) {
        throw 'The supplied database backup is the checked-in repository seed, not an independently copied production backup.'
    }

    $tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')
    $temporaryRestoreDirectory = [IO.Path]::GetFullPath((Join-Path $tempRoot ("hoj-db-restore-" + [guid]::NewGuid().ToString('N').Substring(0, 8))))
    if (-not $temporaryRestoreDirectory.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to use a restore directory outside the system temp directory: $temporaryRestoreDirectory"
    }
    New-Item -ItemType Directory -Path $temporaryRestoreDirectory | Out-Null
    $preparedSqlFile = Join-Path $temporaryRestoreDirectory 'backup.sql'
    if ($lowerBackupName.EndsWith('.gz')) {
        $sourceStream = [IO.File]::OpenRead($databaseBackupPath)
        $gzipStream = $null
        $destinationStream = $null
        try {
            $gzipStream = New-Object IO.Compression.GZipStream($sourceStream, [IO.Compression.CompressionMode]::Decompress)
            $destinationStream = [IO.File]::Create($preparedSqlFile)
            $gzipStream.CopyTo($destinationStream)
        }
        catch [IO.InvalidDataException] {
            throw "Database backup is not a valid gzip stream: $databaseBackupPath"
        }
        finally {
            if ($null -ne $destinationStream) {
                $destinationStream.Dispose()
            }
            if ($null -ne $gzipStream) {
                $gzipStream.Dispose()
            }
            $sourceStream.Dispose()
        }
    }
    else {
        Copy-Item -LiteralPath $databaseBackupPath -Destination $preparedSqlFile
    }
    if ((Get-Item -LiteralPath $preparedSqlFile).Length -le 0) {
        throw 'Prepared database restore file is empty.'
    }
    $sqlPath = $preparedSqlFile.Replace('\', '/')
}
else {
    $backupHash = (Get-FileHash -LiteralPath $repositorySqlFile -Algorithm SHA256).Hash
}

    $env:MYSQL_PWD = $password
    Invoke-CheckedCommand -Description 'Starting isolated MySQL' -Command {
        & docker run --name $containerName --detach `
            --env "MYSQL_ROOT_PASSWORD=$password" `
            --publish '127.0.0.1::3306' `
            $MySqlImage `
            --character-set-server=utf8mb4 `
            --collation-server=utf8mb4_unicode_ci | Out-Null
    }
    $containerStarted = $true

    $containerReady = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & docker exec --env "MYSQL_PWD=$password" $containerName `
            mysqladmin ping --user=root --silent 2>$null | Out-Null
        $pingExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
        if ($pingExitCode -eq 0) {
            $containerReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $containerReady) {
        & docker logs $containerName
        throw 'Isolated MySQL did not become healthy.'
    }

    $binding = & docker port $containerName '3306/tcp'
    if ($LASTEXITCODE -ne 0 -or -not $binding) {
        throw 'Unable to resolve the isolated MySQL host port.'
    }
    $port = ($binding -split ':')[-1].Trim()

    $hostReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & mysql --connect-timeout=2 --protocol=tcp --host=127.0.0.1 `
            "--port=$port" --user=root --silent --skip-column-names `
            --execute='SELECT 1' 2>$null | Out-Null
        $pingExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorAction
        if ($pingExitCode -eq 0) {
            $hostReady = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $hostReady) {
        throw 'The isolated MySQL host port did not become ready.'
    }

    Invoke-CheckedCommand -Description 'Creating isolated HOJ database' -Command {
        & mysql --protocol=tcp --host=127.0.0.1 "--port=$port" --user=root `
            --default-character-set=utf8mb4 `
            --execute='CREATE DATABASE IF NOT EXISTS hoj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
    }
    Invoke-CheckedCommand -Description 'Restoring the HOJ database backup' -Command {
        & mysql --protocol=tcp --host=127.0.0.1 "--port=$port" --user=root `
            --database=hoj --default-character-set=utf8mb4 --execute="source $sqlPath"
    }

    $tableCount = & mysql --protocol=tcp --host=127.0.0.1 "--port=$port" --user=root `
        --batch --skip-column-names `
        --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hoj' AND table_type='BASE TABLE'"
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to count restored HOJ tables.'
    }
    Write-Host "Restored $tableCount HOJ tables into isolated MySQL."

    $schemaSignature = Invoke-MySqlScalar -Port $port -Sql "SET SESSION group_concat_max_len=10000000; SELECT SHA2(GROUP_CONCAT(CONCAT(table_name, ':', column_name, ':', column_type, ':', is_nullable, ':', IFNULL(column_default, '<NULL>'), ':', extra) ORDER BY table_name, ordinal_position SEPARATOR '|'), 256) FROM information_schema.columns WHERE table_schema='hoj';"
    $criticalTables = @('user_info', 'problem', 'problem_case', 'judge', 'judge_case', 'user_acproblem', 'contest', 'session', 'judge_server')
    $criticalRowCounts = [ordered]@{}
    foreach ($table in $criticalTables) {
        $criticalRowCounts[$table] = [long](Invoke-MySqlScalar -Port $port -Sql "SELECT COUNT(*) FROM hoj.$table;")
    }
    $passwordProfile = [ordered]@{
        total = [long](Invoke-MySqlScalar -Port $port -Sql 'SELECT COUNT(*) FROM hoj.user_info;')
        legacyMd5 = [long](Invoke-MySqlScalar -Port $port -Sql "SELECT COUNT(*) FROM hoj.user_info WHERE password REGEXP '^[0-9A-Fa-f]{32}$';")
        bcrypt = [long](Invoke-MySqlScalar -Port $port -Sql 'SELECT COUNT(*) FROM hoj.user_info WHERE password LIKE ''$2%'';')
    }
    $profile = [ordered]@{
        backupSha256 = $backupHash
        tableCount = [int]$tableCount
        schemaSignature = $schemaSignature
        criticalRowCounts = $criticalRowCounts
        passwordFormats = $passwordProfile
    }
    Write-Host 'Restored backup aggregate profile (contains no usernames or password hashes):'
    Write-Host ($profile | ConvertTo-Json -Depth 5)

    $env:HOJ_DB_COMPAT_URL = "jdbc:mysql://127.0.0.1:$port/hoj?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
    $env:HOJ_DB_COMPAT_USERNAME = 'root'
    $env:HOJ_DB_COMPAT_PASSWORD = $password

    Push-Location $backendPath
    try {
        Invoke-CheckedCommand -Description 'Read-only database compatibility test' -Command {
            & mvn -B -ntp -pl DataBackup -am test `
                '-DskipTests=false' `
                '-DexcludedTestGroups=' `
                '-Dgroups=database-compatibility'
        }
    }
    finally {
        Pop-Location
    }

    Write-Host 'Database compatibility verification completed without changing repository SQL files.'
}
finally {
    $env:MYSQL_PWD = $savedEnvironment.MYSQL_PWD
    $env:HOJ_DB_COMPAT_URL = $savedEnvironment.HOJ_DB_COMPAT_URL
    $env:HOJ_DB_COMPAT_USERNAME = $savedEnvironment.HOJ_DB_COMPAT_USERNAME
    $env:HOJ_DB_COMPAT_PASSWORD = $savedEnvironment.HOJ_DB_COMPAT_PASSWORD

    if ($containerStarted) {
        & docker rm --force $containerName 2>$null | Out-Null
    }
    if ($null -ne $temporaryRestoreDirectory -and (Test-Path -LiteralPath $temporaryRestoreDirectory)) {
        $tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')
        $resolvedCleanupPath = [IO.Path]::GetFullPath($temporaryRestoreDirectory)
        if (-not $resolvedCleanupPath.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean a path outside the system temp directory: $resolvedCleanupPath"
        }
        Remove-Item -LiteralPath $resolvedCleanupPath -Recurse -Force
    }
}
