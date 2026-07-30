[CmdletBinding()]
param(
    [switch]$SkipInstall,
    [switch]$SkipAudit
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$auditScript = Join-Path $PSScriptRoot 'audit-stage22-native-vue3.mjs'

foreach ($command in @('node', 'npm')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command is unavailable: $command"
    }
}

$auditJson = & node $auditScript
if ($LASTEXITCODE -ne 0) {
    throw 'Stage 22 native Vue 3 source audit failed.'
}
$audit = ($auditJson -join "`n") | ConvertFrom-Json
if (-not $audit.boundaries.noCompatDependency -or
        -not $audit.boundaries.nativeViteCompiler -or
        -not $audit.boundaries.nativeApplicationBootstrap -or
        -not $audit.boundaries.noLegacySourceApis) {
    throw 'Stage 22 native Vue 3 boundaries were not satisfied.'
}

Push-Location $frontendRoot
try {
    if (-not $SkipInstall) {
        & npm ci --ignore-scripts
        if ($LASTEXITCODE -ne 0) {
            throw "npm ci failed with exit code $LASTEXITCODE."
        }
    }
    if (-not $SkipAudit) {
        & npm audit --registry=https://registry.npmjs.org --audit-level=high
        if ($LASTEXITCODE -ne 0) {
            throw "npm audit failed with exit code $LASTEXITCODE."
        }
    }
    & npm ls vue '@vue/compiler-sfc' --depth=0
    if ($LASTEXITCODE -ne 0) {
        throw "npm ls failed with exit code $LASTEXITCODE."
    }
    & npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "Native Vue 3 production build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host 'Stage 22 native Vue 3 verification passed.'
Write-Host "Audited $($audit.summary.sourceFiles) application source files with no @vue/compat dependency, MODE 2 compiler, compatibility runtime, or reviewed Vue 2 source APIs."
