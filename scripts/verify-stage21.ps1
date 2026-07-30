[CmdletBinding()]
param(
    [switch]$SkipCleanInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$auditScript = Join-Path $PSScriptRoot 'audit-stage21-compat.mjs'
$stage20Verifier = Join-Path $PSScriptRoot 'verify-stage20.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 21 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $stage20Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 21 verification dependency is missing: $requiredFile"
    }
}

& $stage20Verifier -SkipCleanInstall:$SkipCleanInstall
if ($LASTEXITCODE -ne 0) {
    throw "Stage-twenty base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-twenty-one compatibility audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json

foreach ($property in @(
    'localeCompileErrors',
    'legacyNativeModifiers',
    'legacySyncBindings',
    'legacyInstanceEventApis',
    'removedInstanceProperties',
    'legacyGlobalVueApis',
    'legacyLifecycleHooks',
    'functionalComponents',
    'componentModelOptions',
    'malformedSfcFiles'
)) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-twenty-one unresolved compatibility boundary remains: $property=$($auditResult.summary.$property)"
    }
}
foreach ($expectation in @(
    @{ Name = 'localeFiles'; Value = 10 },
    @{ Name = 'localeMessageCount'; Value = 5281 },
    @{ Name = 'localeLiteralBraceFixes'; Value = 5 },
    @{ Name = 'explicitMode3Boundaries'; Value = 5 },
    @{ Name = 'explicitWatchArrayVue3Boundaries'; Value = 1 }
)) {
    $actual = $auditResult.summary.($expectation.Name)
    if ($actual -ne $expectation.Value) {
        throw "Stage-twenty-one boundary count mismatch: $($expectation.Name)=$actual, expected $($expectation.Value)."
    }
}
foreach ($property in @($auditResult.boundaries.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-twenty-one compatibility boundary failed: $($property.Name)"
    }
}

Push-Location $frontendRoot
try {
    & npm ls vue '@vue/compat' vue-i18n --depth=0
    if ($LASTEXITCODE -ne 0) {
        throw "Installed Vue compatibility dependency verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'Stage-twenty-one application compatibility inventory verification passed.'
Write-Host 'All 5,281 application locale messages compile and the reviewed legacy source API inventory remains at zero.'
