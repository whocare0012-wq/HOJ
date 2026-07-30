[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatabaseBackup,
    [Parameter(Mandatory = $true)][string]$JudgeDataDirectory,
    [Parameter(Mandatory = $true)][string]$DeployedBackendJar,
    [Parameter(Mandatory = $true)][string]$DeployedJudgeJar,
    [Parameter(Mandatory = $true)][string]$SandboxRuntimeImage,
    [string]$ExpectedDatabaseBackupSha256,
    [string]$ExpectedJudgeDataSha256,
    [string]$ExpectedBackendJarSha256,
    [string]$ExpectedJudgeJarSha256,
    [string]$ExpectedSandboxImageId,
    [string]$ExpectedSandboxRepositoryDigest,
    [switch]$InventoryOnly,
    [switch]$AllowRepositorySeedFixture
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Resolve-ExistingFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Description was not found: $Path"
    }
    return [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path)
}

function Resolve-ExistingDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Description was not found: $Path"
    }
    return [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $Path).Path).TrimEnd('\')
}

function Normalize-Sha256 {
    param(
        [AllowEmptyString()][string]$Value,
        [Parameter(Mandatory = $true)][string]$Description,
        [switch]$Required
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        if ($Required) {
            throw "$Description is required outside -InventoryOnly mode."
        }
        return $null
    }
    $normalized = $Value.Trim().ToUpperInvariant()
    if ($normalized -notmatch '^[0-9A-F]{64}$') {
        throw "$Description must contain exactly 64 hexadecimal characters."
    }
    return $normalized
}

function Assert-ExpectedValue {
    param(
        [Parameter(Mandatory = $true)][string]$Actual,
        [Parameter(Mandatory = $true)][string]$Expected,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not $Actual.Equals($Expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description did not match the independently recorded value. Expected $Expected but found $Actual."
    }
}

function Normalize-RepositoryDigest {
    param(
        [AllowEmptyString()][string]$Value,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }
    $normalized = $Value.Trim()
    if ($normalized -notmatch '^[^@\s]+@sha256:[0-9A-Fa-f]{64}$') {
        throw "$Description must use the immutable repository@sha256:<64 hexadecimal characters> form."
    }
    return $normalized
}

function Get-JarIdentity {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ApplicationClass,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if ([IO.Path]::GetExtension($Path) -ne '.jar') {
        throw "$Description must be a .jar file: $Path"
    }
    $file = Get-Item -LiteralPath $Path
    if ($file.Length -lt 1MB) {
        throw "$Description is unexpectedly small and is not accepted as a deployable Spring Boot JAR: $Path"
    }

    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $requiredEntry = "BOOT-INF/classes/$ApplicationClass"
        $entry = $archive.Entries | Where-Object { $_.FullName -eq $requiredEntry } | Select-Object -First 1
        if ($null -eq $entry) {
            throw "$Description does not contain $requiredEntry."
        }
    }
    finally {
        $archive.Dispose()
    }

    return [pscustomobject]@{
        path = $Path
        bytes = $file.Length
        sha256 = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    }
}

function Get-JudgeDataIdentity {
    param([Parameter(Mandatory = $true)][string]$Root)

    $rootItem = Get-Item -LiteralPath $Root -Force
    if (($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Judge data root must not be a symbolic link or reparse point: $Root"
    }
    $testCaseDirectory = Join-Path $Root 'test_case'
    if (-not (Test-Path -LiteralPath $testCaseDirectory -PathType Container)) {
        throw "Judge data does not contain the required test_case directory: $Root"
    }

    $entries = @(Get-ChildItem -LiteralPath $Root -Recurse -Force)
    $reparsePoints = @($entries | Where-Object {
            ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0
        })
    if ($reparsePoints.Count -gt 0) {
        throw "Judge data contains symbolic links or reparse points; refusing a bind mount that could escape the copied tree: $($reparsePoints[0].FullName)"
    }

    $files = @($entries | Where-Object { -not $_.PSIsContainer })
    $testCaseFiles = @($files | Where-Object {
            $_.FullName.StartsWith($testCaseDirectory + '\', [StringComparison]::OrdinalIgnoreCase)
        })
    if ($testCaseFiles.Count -lt 1) {
        throw "Judge data test_case directory contains no files: $testCaseDirectory"
    }

    $manifestLines = New-Object System.Collections.Generic.List[string]
    [long]$totalBytes = 0
    foreach ($file in $files) {
        $relativePath = $file.FullName.Substring($Root.Length).TrimStart('\').Replace('\', '/')
        $fileHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        $manifestLines.Add("$relativePath`t$($file.Length)`t$fileHash")
        $totalBytes += $file.Length
    }
    # Use an explicit ordinal path order so the same copied tree has the same
    # identity on Windows and Linux regardless of locale or case-folding rules.
    $manifestArray = $manifestLines.ToArray()
    [Array]::Sort($manifestArray, [StringComparer]::Ordinal)
    $manifestText = [string]::Join("`n", $manifestArray)
    $manifestBytes = [Text.Encoding]::UTF8.GetBytes($manifestText)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $treeHash = ([BitConverter]::ToString($sha256.ComputeHash($manifestBytes))).Replace('-', '')
    }
    finally {
        $sha256.Dispose()
    }

    return [pscustomobject]@{
        path = $Root
        fileCount = $files.Count
        testCaseFileCount = $testCaseFiles.Count
        bytes = $totalBytes
        sha256 = $treeHash
    }
}

function Assert-SqlBackupShape {
    param([Parameter(Mandatory = $true)][string]$Path)

    $lowerName = $Path.ToLowerInvariant()
    if (-not ($lowerName.EndsWith('.sql') -or $lowerName.EndsWith('.sql.gz'))) {
        throw "Database backup must be an unencrypted .sql or .sql.gz file: $Path"
    }

    $fileStream = [IO.File]::OpenRead($Path)
    $contentStream = $fileStream
    $gzipStream = $null
    $reader = $null
    try {
        if ($lowerName.EndsWith('.gz')) {
            $gzipStream = New-Object IO.Compression.GZipStream($fileStream, [IO.Compression.CompressionMode]::Decompress)
            $contentStream = $gzipStream
        }
        $reader = New-Object IO.StreamReader($contentStream, [Text.Encoding]::UTF8, $true, 65536, $true)
        $buffer = New-Object char[] (4MB)
        $readCount = $reader.ReadBlock($buffer, 0, $buffer.Length)
        if ($readCount -le 0) {
            throw "Database backup is empty after decompression: $Path"
        }
        $sampleBuilder = New-Object Text.StringBuilder
        $sampleBuilder.Append($buffer, 0, $readCount) | Out-Null
        $sample = $sampleBuilder.ToString()
        if ($sample -notmatch '(?i)(CREATE\s+TABLE|INSERT\s+INTO|MySQL\s+dump)') {
            throw "Database backup does not resemble a MySQL logical dump in its first 4 MiB: $Path"
        }
    }
    catch [IO.InvalidDataException] {
        throw "Database backup is not a valid gzip stream: $Path"
    }
    finally {
        if ($null -ne $reader) {
            $reader.Dispose()
        }
        if ($null -ne $gzipStream) {
            $gzipStream.Dispose()
        }
        $fileStream.Dispose()
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Required command is not available: docker'
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$databaseBackupPath = Resolve-ExistingFile -Path $DatabaseBackup -Description 'Production database backup copy'
$judgeDataPath = Resolve-ExistingDirectory -Path $JudgeDataDirectory -Description 'Production judge-data copy'
$backendJarPath = Resolve-ExistingFile -Path $DeployedBackendJar -Description 'Currently deployed backend JAR copy'
$judgeJarPath = Resolve-ExistingFile -Path $DeployedJudgeJar -Description 'Currently deployed judge-server JAR copy'

Assert-SqlBackupShape -Path $databaseBackupPath
$databaseBackupFile = Get-Item -LiteralPath $databaseBackupPath
$databaseBackupHash = (Get-FileHash -LiteralPath $databaseBackupPath -Algorithm SHA256).Hash
$seedSql = Join-Path $repoRoot 'sqlAndsetting\hoj.sql'
$seedSqlHash = (Get-FileHash -LiteralPath $seedSql -Algorithm SHA256).Hash
if (-not $AllowRepositorySeedFixture -and $databaseBackupHash -eq $seedSqlHash) {
    throw 'The supplied database backup is the checked-in repository seed, not an independently copied production backup.'
}

$judgeDataIdentity = Get-JudgeDataIdentity -Root $judgeDataPath
$backendIdentity = Get-JarIdentity -Path $backendJarPath `
    -ApplicationClass 'top/hcode/hoj/DataBackupApplication.class' -Description 'Currently deployed backend JAR copy'
$judgeIdentity = Get-JarIdentity -Path $judgeJarPath `
    -ApplicationClass 'top/hcode/hoj/JudgeServerApplication.class' -Description 'Currently deployed judge-server JAR copy'

$sandboxImageIdOutput = & docker image inspect --format '{{.Id}}' $SandboxRuntimeImage 2>$null
$sandboxInspectExitCode = $LASTEXITCODE
$sandboxImageId = [string]($sandboxImageIdOutput | Select-Object -First 1)
if ($sandboxInspectExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($sandboxImageId)) {
    throw "The exact sandbox runtime image is not available locally: $SandboxRuntimeImage"
}
$sandboxImageId = ([string]$sandboxImageId).Trim()
$sandboxRepoDigests = @(& docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' $SandboxRuntimeImage 2>$null | Where-Object {
        -not [string]::IsNullOrWhiteSpace($_)
    })

$requireExpected = -not $InventoryOnly
$expectedDatabaseHash = Normalize-Sha256 -Value $ExpectedDatabaseBackupSha256 `
    -Description 'Expected database backup SHA-256' -Required:$requireExpected
$expectedJudgeDataHash = Normalize-Sha256 -Value $ExpectedJudgeDataSha256 `
    -Description 'Expected judge-data tree SHA-256' -Required:$requireExpected
$expectedBackendHash = Normalize-Sha256 -Value $ExpectedBackendJarSha256 `
    -Description 'Expected deployed backend JAR SHA-256' -Required:$requireExpected
$expectedJudgeHash = Normalize-Sha256 -Value $ExpectedJudgeJarSha256 `
    -Description 'Expected deployed judge-server JAR SHA-256' -Required:$requireExpected
$expectedSandboxRepoDigest = Normalize-RepositoryDigest -Value $ExpectedSandboxRepositoryDigest `
    -Description 'Expected sandbox repository digest'

if (-not $InventoryOnly) {
    if ([string]::IsNullOrWhiteSpace($ExpectedSandboxImageId) -and $null -eq $expectedSandboxRepoDigest) {
        throw 'Expected sandbox repository digest or image ID is required outside -InventoryOnly mode.'
    }
    Assert-ExpectedValue -Actual $databaseBackupHash -Expected $expectedDatabaseHash -Description 'Database backup SHA-256'
    Assert-ExpectedValue -Actual $judgeDataIdentity.sha256 -Expected $expectedJudgeDataHash -Description 'Judge-data tree SHA-256'
    Assert-ExpectedValue -Actual $backendIdentity.sha256 -Expected $expectedBackendHash -Description 'Deployed backend JAR SHA-256'
    Assert-ExpectedValue -Actual $judgeIdentity.sha256 -Expected $expectedJudgeHash -Description 'Deployed judge-server JAR SHA-256'
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSandboxImageId)) {
        Assert-ExpectedValue -Actual $sandboxImageId -Expected $ExpectedSandboxImageId.Trim() -Description 'Sandbox image ID'
    }
    if ($null -ne $expectedSandboxRepoDigest) {
        $matchingRepoDigest = @($sandboxRepoDigests | Where-Object {
                ([string]$_).Trim().Equals($expectedSandboxRepoDigest, [StringComparison]::OrdinalIgnoreCase)
            })
        if ($matchingRepoDigest.Count -lt 1) {
            throw "Sandbox repository digest did not match the independently recorded value: $expectedSandboxRepoDigest"
        }
    }
}

$result = [ordered]@{
    mode = $(if ($InventoryOnly) { 'inventory-only' } else { 'verified' })
    databaseBackup = [ordered]@{
        path = $databaseBackupPath
        bytes = $databaseBackupFile.Length
        sha256 = $databaseBackupHash
        repositorySeedFixture = ($databaseBackupHash -eq $seedSqlHash)
    }
    judgeData = $judgeDataIdentity
    deployedBackendJar = $backendIdentity
    deployedJudgeJar = $judgeIdentity
    sandboxRuntime = [ordered]@{
        image = $SandboxRuntimeImage
        id = $sandboxImageId
        repoDigests = $sandboxRepoDigests
    }
}

$result | ConvertTo-Json -Depth 6
