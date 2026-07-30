[CmdletBinding()]
param(
    [switch]$SkipCleanInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$auditScript = Join-Path $PSScriptRoot 'audit-stage17-markdown.mjs'
$stage16Verifier = Join-Path $PSScriptRoot 'verify-stage16.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 17 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $stage16Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 17 verification dependency is missing: $requiredFile"
    }
}

& $stage16Verifier -SkipCleanInstall:$SkipCleanInstall
if ($LASTEXITCODE -ne 0) {
    throw "Stage-sixteen base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-seventeen Markdown audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json

foreach ($property in @(
    'legacyMavonUsages',
    'legacyMavonLockEntries',
    'vulnerableTocEntries',
    'invalidEditorModels',
    'externalEditorRuntimeUrls'
)) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-seventeen legacy or unsafe Markdown boundary remains: $property=$($auditResult.summary.$property)"
    }
}
if ($auditResult.summary.editorInstances -ne 21) {
    throw "Expected 21 centralized Editor instances, found $($auditResult.summary.editorInstances)."
}
if ($auditResult.summary.markdownRendererConsumers -ne 7) {
    throw "Expected seven global Markdown renderer consumers, found $($auditResult.summary.markdownRendererConsumers)."
}
if ($auditResult.dependencies.mismatches.Count -ne 0 -or
    $null -ne $auditResult.dependencies.forbiddenMavonDependency -or
    $null -ne $auditResult.dependencies.forbiddenTocDependency) {
    throw 'The stage-seventeen Markdown dependency boundary is incomplete.'
}
foreach ($property in @($auditResult.boundaries.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-seventeen editor boundary failed: $($property.Name)"
    }
}
foreach ($property in @($auditResult.smoke.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-seventeen Markdown rendering smoke test failed: $($property.Name)"
    }
}

Push-Location $frontendRoot
try {
    & npm ls cropperjs md-editor-v3 markdown-it --depth=0
    if ($LASTEXITCODE -ne 0) {
        throw "Installed Markdown dependency verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'Stage-seventeen native Vue 3 Markdown editor verification passed.'
Write-Host 'All 21 editor instances retain their model contract; mavon-editor and unsafe TOC residues remain at zero.'
