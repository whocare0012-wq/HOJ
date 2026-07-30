[CmdletBinding()]
param(
    [switch]$SkipCleanInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$auditScript = Join-Path $PSScriptRoot 'audit-stage20-katex.mjs'
$stage19Verifier = Join-Path $PSScriptRoot 'verify-stage19.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 20 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $stage19Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 20 verification dependency is missing: $requiredFile"
    }
}

& $stage19Verifier -SkipCleanInstall:$SkipCleanInstall
if ($LASTEXITCODE -ne 0) {
    throw "Stage-nineteen base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-twenty KaTeX audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json

foreach ($property in @(
    'legacyPackageSourceUsages',
    'legacyDirectiveHooks',
    'malformedSfcFiles'
)) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-twenty legacy or malformed boundary remains: $property=$($auditResult.summary.$property)"
    }
}
foreach ($expectation in @(
    @{ Name = 'katexDirectiveInstances'; Value = 7 },
    @{ Name = 'autoRenderImports'; Value = 1 },
    @{ Name = 'katexCssImports'; Value = 1 },
    @{ Name = 'katexPluginImports'; Value = 1 },
    @{ Name = 'katexPluginUses'; Value = 1 }
)) {
    $actual = $auditResult.summary.($expectation.Name)
    if ($actual -ne $expectation.Value) {
        throw "Stage-twenty boundary count mismatch: $($expectation.Name)=$actual, expected $($expectation.Value)."
    }
}
foreach ($property in @($auditResult.boundaries.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-twenty KaTeX boundary failed: $($property.Name)"
    }
}
foreach ($property in @($auditResult.smoke.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-twenty KaTeX smoke test failed: $($property.Name)"
    }
}

Push-Location $frontendRoot
try {
    & npm ls katex '@iktakahiro/markdown-it-katex' --all
    if ($LASTEXITCODE -ne 0) {
        throw "Installed KaTeX dependency verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'Stage-twenty native Vue 3 KaTeX auto-render verification passed.'
Write-Host 'All seven directive sites retain centralized rendering, copied options, safe delimiter priority, and trust disabled.'
