[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$DatabaseBackup,
    [Parameter(Mandatory = $true)][string]$JudgeDataDirectory,
    [Parameter(Mandatory = $true)][string]$DeployedBackendJar,
    [Parameter(Mandatory = $true)][string]$DeployedJudgeJar,
    [Parameter(Mandatory = $true)][string]$SandboxRuntimeImage,
    [Parameter(Mandatory = $true)][string]$ExpectedDatabaseBackupSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedJudgeDataSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedBackendJarSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedJudgeJarSha256,
    [string]$ExpectedSandboxImageId,
    [string]$ExpectedSandboxRepositoryDigest,
    [string]$MySqlImage = 'mysql:8.0',
    [switch]$AllowRepositorySeedFixture
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

Write-Host 'Verifying copied backup, judge data, deployed rollback JAR files, and sandbox image identities...'
& (Join-Path $PSScriptRoot 'verify-stage7-inputs.ps1') `
    -DatabaseBackup $DatabaseBackup `
    -JudgeDataDirectory $JudgeDataDirectory `
    -DeployedBackendJar $DeployedBackendJar `
    -DeployedJudgeJar $DeployedJudgeJar `
    -SandboxRuntimeImage $SandboxRuntimeImage `
    -ExpectedDatabaseBackupSha256 $ExpectedDatabaseBackupSha256 `
    -ExpectedJudgeDataSha256 $ExpectedJudgeDataSha256 `
    -ExpectedBackendJarSha256 $ExpectedBackendJarSha256 `
    -ExpectedJudgeJarSha256 $ExpectedJudgeJarSha256 `
    -ExpectedSandboxImageId $ExpectedSandboxImageId `
    -ExpectedSandboxRepositoryDigest $ExpectedSandboxRepositoryDigest `
    -AllowRepositorySeedFixture:$AllowRepositorySeedFixture

Write-Host 'Restoring the verified backup into a new disposable MySQL container for read-only mapper compatibility checks...'
& (Join-Path $PSScriptRoot 'verify-db-compatibility.ps1') `
    -MySqlImage $MySqlImage `
    -DatabaseBackup $DatabaseBackup `
    -ExpectedDatabaseBackupSha256 $ExpectedDatabaseBackupSha256 `
    -AllowRepositorySeedFixture:$AllowRepositorySeedFixture

Write-Host 'Stage-seven copied-input and isolated database compatibility gates passed.'
Write-Host 'Runtime upgrade and rollback acceptance still requires an approved staging account and representative production problem identifiers.'
