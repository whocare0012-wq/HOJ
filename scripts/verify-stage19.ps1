[CmdletBinding()]
param(
    [switch]$SkipCleanInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$auditScript = Join-Path $PSScriptRoot 'audit-stage19-cropper.mjs'
$stage18Verifier = Join-Path $PSScriptRoot 'verify-stage18.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 19 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $stage18Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 19 verification dependency is missing: $requiredFile"
    }
}

& $stage18Verifier -SkipCleanInstall:$SkipCleanInstall
if ($LASTEXITCODE -ne 0) {
    throw "Stage-eighteen base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-nineteen cropper audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json

foreach ($property in @(
    'forbiddenNextSourceUsages',
    'malformedSfcFiles'
)) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-nineteen forbidden or malformed boundary remains: $property=$($auditResult.summary.$property)"
    }
}
foreach ($expectation in @(
    @{ Name = 'cropperInstances'; Value = 2 },
    @{ Name = 'validCropperTemplates'; Value = 2 },
    @{ Name = 'adapterImports'; Value = 2 },
    @{ Name = 'directPackageImports'; Value = 1 },
    @{ Name = 'cropperCssImports'; Value = 1 },
    @{ Name = 'cropDataCalls'; Value = 2 },
    @{ Name = 'cropBlobCalls'; Value = 2 },
    @{ Name = 'rotateLeftCalls'; Value = 2 },
    @{ Name = 'rotateRightCalls'; Value = 2 },
    @{ Name = 'userUploadEndpoints'; Value = 1 },
    @{ Name = 'groupUploadEndpoints'; Value = 1 },
    @{ Name = 'cropperJsImports'; Value = 1 }
)) {
    $actual = $auditResult.summary.($expectation.Name)
    if ($actual -ne $expectation.Value) {
        throw "Stage-nineteen boundary count mismatch: $($expectation.Name)=$actual, expected $($expectation.Value)."
    }
}
foreach ($property in @($auditResult.boundaries.PSObject.Properties)) {
    if (-not [bool]$property.Value) {
        throw "Stage-nineteen cropper boundary failed: $($property.Name)"
    }
}

Push-Location $frontendRoot
try {
    & npm ls vue-cropper cropperjs --depth=0
    if ($LASTEXITCODE -ne 0) {
        throw "Installed cropper dependency verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'Stage-nineteen native Vue 3 avatar cropper verification passed.'
Write-Host 'Both avatar flows retain their preview, rotation, Base64, Blob, multipart, and endpoint contracts.'
