[CmdletBinding()]
param(
    [switch]$SkipCleanInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$auditScript = Join-Path $PSScriptRoot 'audit-stage18-heatmap.mjs'
$stage17Verifier = Join-Path $PSScriptRoot 'verify-stage17.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 18 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $stage17Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 18 verification dependency is missing: $requiredFile"
    }
}

& $stage17Verifier -SkipCleanInstall:$SkipCleanInstall
if ($LASTEXITCODE -ne 0) {
    throw "Stage-seventeen base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-eighteen heatmap audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json

foreach ($property in @(
    'legacySourceUsages',
    'legacyDependencyDeclarations',
    'legacyLockEntries',
    'malformedSfcFiles'
)) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-eighteen legacy heatmap boundary remains: $property=$($auditResult.summary.$property)"
    }
}
if ($auditResult.summary.heatmapInstances -ne 1) {
    throw "Expected one calendar heatmap instance, found $($auditResult.summary.heatmapInstances)."
}
if ($auditResult.summary.nativeComponentImports -ne 1) {
    throw "Expected one native CalendarHeatmap import, found $($auditResult.summary.nativeComponentImports)."
}
foreach ($property in @($auditResult.boundaries.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-eighteen component boundary failed: $($property.Name)"
    }
}
foreach ($property in @($auditResult.smoke.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-eighteen calendar model smoke test failed: $($property.Name)"
    }
}

Push-Location $frontendRoot
try {
    & npm ls vue @vue/compat --depth=0
    if ($LASTEXITCODE -ne 0) {
        throw "Installed Vue dependency verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'Stage-eighteen native Vue 3 calendar heatmap verification passed.'
Write-Host 'The vue-calendar-heatmap, v-tooltip, vue-resize, alternate fork, and Tippy dependency boundaries remain at zero.'
