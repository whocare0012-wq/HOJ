[CmdletBinding()]
param(
    [switch]$SkipCleanInstall,
    [string]$ExpectedVueI18nVersion = '9.14.5'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot 'hoj-vue\src'
$auditScript = Join-Path $PSScriptRoot 'audit-stage15-deprecations.mjs'
$migrationScript = Join-Path $PSScriptRoot 'migrate-stage15-element-plus.mjs'
$stage14Verifier = Join-Path $PSScriptRoot 'verify-stage14.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 15 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $migrationScript, $stage14Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 15 verification dependency is missing: $requiredFile"
    }
}

$groupDetailsPath = Join-Path $sourceRoot 'views\oj\group\GroupDetails.vue'
$groupDetailsSource = Get-Content -LiteralPath $groupDetailsPath -Raw
if ($groupDetailsSource -notmatch '<router-view\s+v-slot="\{ Component \}">\s*<transition' -or
    $groupDetailsSource -notmatch '<component\s+:is="Component"') {
    throw 'The Vue Router transition slot boundary is incomplete in GroupDetails.vue.'
}

$userInfoPath = Join-Path $sourceRoot 'components\oj\setting\UserInfo.vue'
$userInfoSource = Get-Content -LiteralPath $userInfoPath -Raw
foreach ($radioValue in @('male', 'female', 'secrecy')) {
    $radioPattern = '<el-radio\s+value="{0}"' -f [regex]::Escape($radioValue)
    if ($userInfoSource -notmatch $radioPattern) {
        throw "The Element Plus radio value '$radioValue' is missing in UserInfo.vue."
    }
}

$problemPath = Join-Path $sourceRoot 'views\oj\problem\Problem.vue'
$problemSource = Get-Content -LiteralPath $problemPath -Raw
if ($problemSource -notmatch 'underline="never"') {
    throw 'The Element Plus link underline enum is missing in Problem.vue.'
}

& $stage14Verifier -SkipCleanInstall:$SkipCleanInstall `
    -ExpectedVueI18nVersion $ExpectedVueI18nVersion
if ($LASTEXITCODE -ne 0) {
    throw "Stage-fourteen base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-fifteen AST audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json
foreach ($property in @('directTransitionRouterViews', 'legacyRadioValues', 'booleanLinkUnderlines')) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-fifteen deprecated boundary remains: $property=$($auditResult.summary.$property)"
    }
}

$migrationOutput = @(& node $migrationScript --expect-radio=0 --expect-link=0)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-fifteen Element Plus migration audit failed with exit code $LASTEXITCODE."
}
$migrationResult = ($migrationOutput -join [Environment]::NewLine) | ConvertFrom-Json
if ($migrationResult.mode -ne 'dry-run' -or
    $migrationResult.summary.changedFiles -ne 0) {
    throw 'Stage-fifteen Element Plus migration is not idempotent.'
}

Write-Host 'Stage-fifteen Vue Router and Element Plus deprecation verification passed.'
Write-Host 'Direct transition RouterViews, legacy radio values, and boolean link underlines remain at zero.'
