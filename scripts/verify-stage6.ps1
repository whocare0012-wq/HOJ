[CmdletBinding()]
param(
    [string]$RollbackCommit = '57d8b5280d96c0aa2d836b6dd1c8d8d1cd216962',
    [string]$MySqlImage = 'mysql:8.0',
    [string]$RedisImage = 'redis:7-alpine',
    [string]$NacosImage = 'nacos/nacos-server:1.4.2',
    [string]$JavaPath = 'C:\Program Files\Java\jdk1.8.0_202\bin\java.exe',
    [string]$SandboxRuntimeImage = 'judge0/judge0:latest',
    [ValidateRange(1024, 65535)][int]$SandboxPort = 5050,
    [string]$JudgeRuntimeImage = 'nacos/nacos-server:1.4.2',
    [string]$JudgeContainerJavaPath = '/usr/lib/jvm/java-1.8.0-openjdk/bin/java'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Assert-CommandExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available: $Name"
    }
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

Assert-CommandExists -Name 'git'
Assert-CommandExists -Name 'mvn'
if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) {
    throw "Java 8 executable was not found: $JavaPath"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$rollbackBuildPatch = Join-Path $PSScriptRoot 'rollback-baseline-build.patch'
if (-not (Test-Path -LiteralPath $rollbackBuildPatch -PathType Leaf)) {
    throw "Rollback baseline build patch was not found: $rollbackBuildPatch"
}

$resolvedCommitOutput = & git -C $repoRoot rev-parse --verify "$RollbackCommit`^{commit}"
$resolveCommitExitCode = $LASTEXITCODE
$resolvedCommit = [string]($resolvedCommitOutput | Select-Object -First 1)
if ($resolveCommitExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($resolvedCommit)) {
    throw "Rollback commit could not be resolved: $RollbackCommit"
}
$resolvedCommit = $resolvedCommit.Trim()

$tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')
$runId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$baselineDirectory = [IO.Path]::GetFullPath((Join-Path $tempRoot "hoj-stage6-baseline-$runId"))
$archivePath = [IO.Path]::GetFullPath("$baselineDirectory.zip")
foreach ($path in @($baselineDirectory, $archivePath)) {
    if (-not $path.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to use a stage-six path outside the system temp directory: $path"
    }
}

$savedJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
$savedPath = [Environment]::GetEnvironmentVariable('Path', 'Process')
$javaHome = Split-Path -Parent (Split-Path -Parent ([IO.Path]::GetFullPath($JavaPath)))

try {
    Write-Host "Exporting rollback baseline commit $resolvedCommit..."
    Invoke-CheckedNative -Description 'Exporting rollback baseline source' -Command {
        & git -C $repoRoot archive --format=zip "--output=$archivePath" $resolvedCommit
    }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $baselineDirectory

    Write-Host 'Pinning the historical Spring Boot build plugin inside the disposable source export...'
    Push-Location $baselineDirectory
    try {
        Invoke-CheckedNative -Description 'Checking rollback baseline build patch' -Command {
            & git apply --check $rollbackBuildPatch
        }
        Invoke-CheckedNative -Description 'Applying rollback baseline build patch' -Command {
            & git apply $rollbackBuildPatch
        }
    }
    finally {
        Pop-Location
    }

    [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'Process')
    [Environment]::SetEnvironmentVariable('Path', "$javaHome\bin;$savedPath", 'Process')

    $baselinePom = Join-Path $baselineDirectory 'hoj-springboot\pom.xml'
    Write-Host 'Building rollback backend and judge-server JAR files with Java 8...'
    Invoke-CheckedNative -Description 'Building rollback baseline JAR files' -Command {
        & mvn -f $baselinePom -pl 'DataBackup,JudgeServer' -am `
            '-DskipTests' '-Dproject.build.sourceEncoding=UTF-8' package
    }

    $rollbackBackendJar = Join-Path $baselineDirectory 'hoj-springboot\DataBackup\target\hoj-backend-4.6.jar'
    $rollbackJudgeJar = Join-Path $baselineDirectory 'hoj-springboot\JudgeServer\target\hoj-judgeServer-4.6.jar'
    foreach ($jar in @($rollbackBackendJar, $rollbackJudgeJar)) {
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
            throw "Rollback build did not produce the expected JAR: $jar"
        }
    }
    Write-Host "Rollback backend SHA-256: $((Get-FileHash -LiteralPath $rollbackBackendJar -Algorithm SHA256).Hash)"
    Write-Host "Rollback judge SHA-256: $((Get-FileHash -LiteralPath $rollbackJudgeJar -Algorithm SHA256).Hash)"

    & (Join-Path $PSScriptRoot 'verify-stage3.ps1') `
        -MySqlImage $MySqlImage `
        -RedisImage $RedisImage `
        -NacosImage $NacosImage `
        -JavaPath $JavaPath `
        -IncludeRollback `
        -RollbackBackendJar $rollbackBackendJar `
        -RollbackJudgeJar $rollbackJudgeJar `
        -SandboxRuntimeImage $SandboxRuntimeImage `
        -SandboxPort $SandboxPort `
        -JudgeRuntimeImage $JudgeRuntimeImage `
        -JudgeContainerJavaPath $JudgeContainerJavaPath
}
finally {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $savedJavaHome, 'Process')
    [Environment]::SetEnvironmentVariable('Path', $savedPath, 'Process')

    if (Test-Path -LiteralPath $archivePath) {
        Remove-Item -LiteralPath $archivePath -Force
    }
    if (Test-Path -LiteralPath $baselineDirectory) {
        $resolvedCleanupPath = [IO.Path]::GetFullPath($baselineDirectory)
        if (-not $resolvedCleanupPath.StartsWith($tempRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean a path outside the system temp directory: $resolvedCleanupPath"
        }
        Remove-Item -LiteralPath $resolvedCleanupPath -Recurse -Force
    }
}
