[CmdletBinding()]
param(
    [switch]$SkipCleanInstall,
    [string]$ExpectedVueI18nVersion = '9.14.5'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$packageJsonPath = Join-Path $frontendRoot 'package.json'
$sourceRoot = Join-Path $frontendRoot 'src'
$stage11Verifier = Join-Path $PSScriptRoot 'verify-stage11.ps1'

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
    throw "Stage 12 does not permit SQL changes: $($sqlStatus -join ', ')"
}

$packageJson = Get-Content -LiteralPath $packageJsonPath -Raw | ConvertFrom-Json
$dependencyNames = @(Get-DependencyNames $packageJson.dependencies) + @(Get-DependencyNames $packageJson.devDependencies)
foreach ($retiredPackage in @('vue-avatar', 'vue-clipboard2', 'vue-m-message')) {
    if ($dependencyNames -contains $retiredPackage) {
        throw "Retired compatibility package remains in package.json: $retiredPackage"
    }
}

$requiredLocalFiles = @(
    'common\clipboard.js',
    'components\common\Avatar.vue',
    'components\common\SlideVerify.vue',
    'components\common\VueParticles.vue'
)
foreach ($relativePath in $requiredLocalFiles) {
    $path = Join-Path $sourceRoot $relativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Stage 12 local replacement is missing: $path"
    }
}

$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File | Where-Object {
        $_.Extension -in @('.js', '.vue')
    })
$sourceRules = [ordered]@{
    '\.native(?:=|\s)' = 'Legacy .native event modifier remains'
    '\.sync(?:=|\s)' = 'Legacy .sync binding remains'
    '/deep/|>>>' = 'Legacy deep CSS combinator remains'
    'Vue\.prototype' = 'Legacy Vue.prototype access remains'
    '<template\b(?:(?!>).)*(?<!v-)\bslot(?:\s*=|\s*>)' = 'Legacy template slot syntax remains'
    '\|\s*(?:submissionMemory|submissionTime|localtime|fromNow|parseContestType|parseRole|parseProblemLevel|ellipsis)\b' = 'Legacy template filter expression remains'
    'from\s+[''"](?:vue-avatar|vue-clipboard2|vue-m-message)[''"]' = 'Retired compatibility import remains'
}
foreach ($sourceFile in $sourceFiles) {
    $source = Get-Content -LiteralPath $sourceFile.FullName -Raw
    foreach ($rule in $sourceRules.GetEnumerator()) {
        if ([regex]::IsMatch($source, $rule.Key, [Text.RegularExpressions.RegexOptions]::Singleline)) {
            throw "$($rule.Value): $($sourceFile.FullName)"
        }
    }
}

$mainSource = Get-Content -LiteralPath (Join-Path $sourceRoot 'main.js') -Raw
if ($mainSource -notmatch 'globalProperties\.\$filters\s*=\s*filters' -or
    $mainSource -notmatch "directive\('clipboard',\s*clipboardDirective\)" -or
    $mainSource -match 'Object\.(?:setPrototypeOf|assign)\([^\r\n]*Vue\.prototype') {
    throw 'Stage 12 native global-property or clipboard registration is missing.'
}

& $stage11Verifier -SkipCleanInstall:$SkipCleanInstall `
    -ExpectedVueI18nVersion $ExpectedVueI18nVersion
if ($LASTEXITCODE -ne 0) {
    throw "Stage-eleven base verification failed with exit code $LASTEXITCODE."
}

Write-Host 'Stage-twelve Vue 3 compatibility-debt verification passed.'
if ($dependencyNames -contains 'muse-ui') {
    Write-Host 'The reviewed stage-twelve runtime boundary is Muse UI GLOBAL_EXTEND in mobile navigation.'
} else {
    Write-Host 'Muse UI has been retired by a later stage; stage-twelve base checks remain valid.'
}
