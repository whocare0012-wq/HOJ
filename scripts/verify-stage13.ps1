[CmdletBinding()]
param(
    [switch]$SkipCleanInstall,
    [string]$ExpectedVueI18nVersion = '9.14.5'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$sourceRoot = Join-Path $frontendRoot 'src'
$packageJsonPath = Join-Path $frontendRoot 'package.json'
$packageLockPath = Join-Path $frontendRoot 'package-lock.json'
$stage12Verifier = Join-Path $PSScriptRoot 'verify-stage12.ps1'

function Get-DependencyNames {
    param([AllowNull()][object]$Dependencies)
    if ($null -eq $Dependencies) {
        return @()
    }
    return @($Dependencies.PSObject.Properties | ForEach-Object { $_.Name })
}

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
if ($LASTEXITCODE -ne 0) {
    throw 'SQL worktree status check failed.'
}
if ($sqlStatus.Count -gt 0) {
    throw "Stage 13 does not permit SQL changes: $($sqlStatus -join ', ')"
}

$packageJson = Get-Content -LiteralPath $packageJsonPath -Raw | ConvertFrom-Json
$dependencyNames = @(Get-DependencyNames $packageJson.dependencies) + @(Get-DependencyNames $packageJson.devDependencies)
if ($dependencyNames -contains 'muse-ui') {
    throw 'Muse UI remains in package.json.'
}

$packageLockSource = Get-Content -LiteralPath $packageLockPath -Raw
if ($packageLockSource -match '"muse-ui"|node_modules/muse-ui') {
    throw 'Muse UI remains in package-lock.json.'
}

$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File | Where-Object {
        $_.Extension -in @('.js', '.vue')
    })
foreach ($sourceFile in $sourceFiles) {
    $source = Get-Content -LiteralPath $sourceFile.FullName -Raw
    if ($source -match '(?i)muse-ui|\bMuseUI\b|<\s*/?\s*mu-') {
        throw "Muse UI source usage remains: $($sourceFile.FullName)"
    }
}

$mainSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'main.js') -Raw
if ($mainSource -match "app\.use\(MuseUI\)|muse-ui") {
    throw 'Muse UI bootstrap remains in main.js.'
}

$frontendNavSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'components\oj\common\NavBar.vue') -Raw
$adminNavSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'views\admin\Home.vue') -Raw
foreach ($navigationSource in @($frontendNavSource, $adminNavSource)) {
    if ($navigationSource -notmatch '<el-drawer\b' -or
        $navigationSource -notmatch '<el-menu\b' -or
        $navigationSource -notmatch '@select="closeMobileDrawer"' -or
        $navigationSource -notmatch 'window\.innerWidth' -or
        $navigationSource -match 'window\.screen\.width') {
        throw 'A responsive Element Plus navigation boundary is incomplete.'
    }
}

$paginationSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'components\oj\common\Pagination.vue') -Raw
if ($paginationSource -match '\b:small\s*=' -or
    $paginationSource -notmatch ":size=""mobilePagination \? 'small' : 'default'""") {
    throw 'Element Plus pagination must use the size API.'
}

& $stage12Verifier -SkipCleanInstall:$SkipCleanInstall `
    -ExpectedVueI18nVersion $ExpectedVueI18nVersion
if ($LASTEXITCODE -ne 0) {
    throw "Stage-twelve base verification failed with exit code $LASTEXITCODE."
}

Write-Host 'Stage-thirteen Muse UI retirement and responsive-navigation verification passed.'
Write-Host 'Runtime browser checks should report no Vue GLOBAL_EXTEND compatibility warning.'
