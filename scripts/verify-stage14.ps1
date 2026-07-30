[CmdletBinding()]
param(
    [switch]$SkipCleanInstall,
    [string]$ExpectedVueI18nVersion = '9.14.5'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot 'hoj-vue\src'
$slotMigrationScript = Join-Path $PSScriptRoot 'migrate-stage14-slots.mjs'
$stage13Verifier = Join-Path $PSScriptRoot 'verify-stage13.ps1'

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 14 does not permit SQL changes: $($sqlStatus -join ', ')"
}

foreach ($requiredFile in @($slotMigrationScript, $stage13Verifier)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Stage 14 verification dependency is missing: $requiredFile"
    }
}

$requiredSlotBoundaries = [ordered]@{
    'views\oj\Home.vue' = @('#header', '#error')
    'views\oj\group\GroupDetails.vue' = @('#label')
    'components\oj\common\JudgeCase.vue' = @('#content')
    'components\oj\common\Login.vue' = @('#reference')
    'components\oj\setting\Account.vue' = @('#append')
    'components\oj\setting\UserInfo.vue' = @('#footer')
    'views\admin\problem\ImportAndExport.vue' = @('#trigger')
}
foreach ($entry in $requiredSlotBoundaries.GetEnumerator()) {
    $sourcePath = Join-Path $sourceRoot $entry.Key
    $source = Get-Content -LiteralPath $sourcePath -Raw
    foreach ($slotBoundary in $entry.Value) {
        if ($source -notmatch [regex]::Escape($slotBoundary)) {
            throw "Vue 3 slot boundary $slotBoundary is missing: $sourcePath"
        }
    }
}

$judgeCaseSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'components\oj\common\JudgeCase.vue') -Raw
if ($judgeCaseSource -notmatch '<template\s+v-if="!isSubtask"\s+#header>') {
    throw 'The conditional JudgeCase header slot was not promoted to its template boundary.'
}

& $stage13Verifier -SkipCleanInstall:$SkipCleanInstall `
    -ExpectedVueI18nVersion $ExpectedVueI18nVersion
if ($LASTEXITCODE -ne 0) {
    throw "Stage-thirteen base verification failed with exit code $LASTEXITCODE."
}

$migrationOutput = @(& node $slotMigrationScript --expect=0)
if ($LASTEXITCODE -ne 0) {
    throw "Stage-fourteen slot AST verification failed with exit code $LASTEXITCODE."
}
$migrationResult = ($migrationOutput -join [Environment]::NewLine) | ConvertFrom-Json
if ($migrationResult.mode -ne 'dry-run' -or
    $migrationResult.summary.total -ne 0 -or
    $migrationResult.summary.changedFiles -ne 0) {
    throw 'Legacy slot migration is not idempotent or old slot attributes remain.'
}

Write-Host 'Stage-fourteen Vue 3 slot-syntax verification passed.'
Write-Host 'All reviewed legacy element slot attributes remain at zero.'
