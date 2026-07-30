[CmdletBinding()]
param(
    [switch]$SkipCleanInstall
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$sourceRoot = Join-Path $frontendRoot 'src'
$auditScript = Join-Path $PSScriptRoot 'audit-stage16-i18n.mjs'
$migrationScript = Join-Path $PSScriptRoot 'migrate-stage16-i18n.mjs'
$stage15Verifier = Join-Path $PSScriptRoot 'verify-stage15.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 16 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($auditScript, $migrationScript, $stage15Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 16 verification dependency is missing: $requiredFile"
    }
}

$packageJson = Get-Content -LiteralPath (Join-Path $frontendRoot 'package.json') -Raw |
    ConvertFrom-Json
if ($packageJson.dependencies.'vue-i18n' -ne '11.4.2') {
    throw "Vue I18n must be pinned to 11.4.2, found '$($packageJson.dependencies.'vue-i18n')'."
}

$i18nPath = Join-Path $sourceRoot 'i18n\index.js'
$i18nSource = Get-Content -LiteralPath $i18nPath -Raw
if ($i18nSource -notmatch '\blegacy\s*:\s*false\b' -or
    $i18nSource -notmatch '\bglobalInjection\s*:\s*true\b' -or
    $i18nSource -match '\ballowComposition\s*:') {
    throw 'The Vue I18n Composition-mode configuration boundary is incomplete.'
}

$timeSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'common\time.js') -Raw
$storeSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'store\index.js') -Raw
$composerLocaleUses = [regex]::Matches(
    $timeSource + [Environment]::NewLine + $storeSource,
    '\bi18n\.locale\.value\b'
).Count
if ($composerLocaleUses -ne 7) {
    throw "Expected seven Composer locale Ref accesses, found $composerLocaleUses."
}

& $stage15Verifier -SkipCleanInstall:$SkipCleanInstall `
    -ExpectedVueI18nVersion '11.4.2'
if ($LASTEXITCODE -ne 0) {
    throw "Stage-fifteen base verification failed with exit code $LASTEXITCODE."
}

$auditOutput = @(& node $auditScript)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-sixteen Vue I18n audit failed with exit code $LASTEXITCODE."
}
$auditResult = ($auditOutput -join [Environment]::NewLine) | ConvertFrom-Json
foreach ($property in @(
    'legacyComponentTranslations',
    'legacyPluralCalls',
    'legacyDirectiveUsages',
    'legacyModuloMessages',
    'unwrappedComposerLocales'
)) {
    if ($auditResult.summary.$property -ne 0) {
        throw "Stage-sixteen Vue I18n legacy boundary remains: $property=$($auditResult.summary.$property)"
    }
}
if ($auditResult.configuration.dependency -ne '11.4.2' -or
    -not $auditResult.configuration.compositionMode -or
    -not $auditResult.configuration.globalInjection -or
    $auditResult.configuration.allowCompositionOption) {
    throw 'The Vue I18n dependency or Composition-mode audit failed.'
}

$migrationOutput = @(& node $migrationScript --expect=0)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-sixteen Vue I18n migration audit failed with exit code $LASTEXITCODE."
}
$migrationResult = ($migrationOutput -join [Environment]::NewLine) | ConvertFrom-Json
if ($migrationResult.mode -ne 'dry-run' -or
    $migrationResult.summary.changedFiles -ne 0) {
    throw 'Stage-sixteen Vue I18n migration is not idempotent.'
}

Push-Location $frontendRoot
try {
    & npm ls vue-i18n --depth=0
    if ($LASTEXITCODE -ne 0) {
        throw "Installed Vue I18n dependency verification failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}

Write-Host 'Stage-sixteen Vue I18n Composition-mode verification passed.'
Write-Host 'Legacy component translations, plural calls, directives, modulo messages, and locale accesses remain at zero.'
