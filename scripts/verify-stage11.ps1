[CmdletBinding()]
param(
    [switch]$SkipCleanInstall,
    [string]$ExpectedVueI18nVersion = '9.14.5',
    [int]$MaxCriticalVulnerabilities = 0,
    [int]$MaxHighVulnerabilities = 0,
    [int]$MaxModerateVulnerabilities = 0,
    [int]$MaxLowVulnerabilities = 0
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$packageJsonPath = Join-Path $frontendRoot 'package.json'
$viteConfigPath = Join-Path $frontendRoot 'vite.config.mjs'
$mainPath = Join-Path $frontendRoot 'src\main.js'
$loginPath = Join-Path $frontendRoot 'src\components\oj\common\Login.vue'
$distRoot = Join-Path $frontendRoot 'dist'
$registry = 'https://registry.npmjs.org'

function Assert-CommandSucceeded {
    param(
        [Parameter(Mandatory = $true)][int]$ExitCode,
        [Parameter(Mandatory = $true)][string]$Description
    )
    if ($ExitCode -ne 0) {
        throw "$Description failed with exit code $ExitCode."
    }
}

function Get-DependencyNames {
    param([AllowNull()][object]$Dependencies)
    if ($null -eq $Dependencies) {
        return @()
    }
    return @($Dependencies.PSObject.Properties | ForEach-Object { $_.Name })
}

function Assert-PackageVersion {
    param(
        [Parameter(Mandatory = $true)][object]$PackageJson,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedVersion
    )
    $property = $PackageJson.dependencies.PSObject.Properties[$Name]
    if ($null -eq $property) {
        $property = $PackageJson.devDependencies.PSObject.Properties[$Name]
    }
    if ($null -eq $property -or [string]$property.Value -ne $ExpectedVersion) {
        throw "Stage 11 requires $Name $ExpectedVersion."
    }
}

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'SQL worktree status check'
if ($sqlStatus.Count -gt 0) {
    throw "Stage 11 does not permit SQL changes: $($sqlStatus -join ', ')"
}

$nodeVersion = (& node --version).Trim()
Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Node.js version check'
if ($nodeVersion -notmatch '^v(?<major>\d+)\.') {
    throw "Unable to parse Node.js version: $nodeVersion"
}
if ([int]$Matches.major -lt 20) {
    throw "Stage 11 requires Node.js 20 or newer but found $nodeVersion."
}

$packageJson = Get-Content -LiteralPath $packageJsonPath -Raw | ConvertFrom-Json
if ($packageJson.scripts.build -ne 'vite build') {
    throw 'Frontend build script must use Vite.'
}

$requiredVersions = [ordered]@{
    'vue' = '3.5.39'
    '@vue/compat' = '3.5.39'
    '@vue/compiler-sfc' = '3.5.39'
    'vite' = '6.4.3'
    '@vitejs/plugin-vue' = '5.2.4'
    'vue-router' = '4.6.4'
    'vuex' = '4.1.0'
    'vue-i18n' = $ExpectedVueI18nVersion
    'element-plus' = '2.14.3'
    '@element-plus/icons-vue' = '2.3.2'
    'echarts' = '6.1.0'
    'vue-echarts' = '8.0.1'
    'vxe-table' = '4.9.17'
    'vxe-pc-ui' = '4.3.18'
}
foreach ($entry in $requiredVersions.GetEnumerator()) {
    Assert-PackageVersion -PackageJson $packageJson -Name $entry.Key -ExpectedVersion $entry.Value
}

$dependencyNames = @(Get-DependencyNames $packageJson.dependencies) + @(Get-DependencyNames $packageJson.devDependencies)
$forbiddenPackages = @(
    '@vue/cli-service',
    '@vue/cli-plugin-babel',
    '@vue/cli-plugin-router',
    '@vue/cli-plugin-vuex',
    '@vitejs/plugin-vue2',
    'compression-webpack-plugin',
    'element-ui',
    'particles.js',
    'vue-monoplasty-slide-verify',
    'vue-particles',
    'vue-template-compiler',
    'vuex-router-sync',
    'webpack-bundle-analyzer'
)
foreach ($forbiddenPackage in $forbiddenPackages) {
    if ($dependencyNames -contains $forbiddenPackage) {
        throw "Retired frontend package remains in package.json: $forbiddenPackage"
    }
}

$viteConfig = Get-Content -LiteralPath $viteConfigPath -Raw
if ($viteConfig -notmatch "from '@vitejs/plugin-vue'" -or
    $viteConfig -notmatch "vue:\s*'@vue/compat'" -or
    $viteConfig -notmatch 'MODE:\s*2') {
    throw 'Vite must compile through @vue/compat in the reviewed MODE 2 boundary.'
}

$mainSource = Get-Content -LiteralPath $mainPath -Raw
if ($mainSource -notmatch 'configureCompat\(' -or
    $mainSource -notmatch 'createApp\(App\)' -or
    $mainSource -notmatch "app\.use\(Element\)" -or
    $mainSource -notmatch "app\.use\(VXETable\)" -or
    $mainSource -notmatch "\^\(El\|Vxe\)" -or
    $mainSource -notmatch 'MODE:\s*3') {
    throw 'Vue 3 bootstrap or the native Element/VXE compatibility boundary is missing.'
}

$loginSource = Get-Content -LiteralPath $loginPath -Raw
if ($loginSource -notmatch '<template\s+#reference>' -or
    $loginSource -match 'slot="reference"') {
    throw 'The login verification popover must use the Vue 3 reference slot.'
}

$vueFiles = @(Get-ChildItem -LiteralPath (Join-Path $frontendRoot 'src') -Recurse -File -Filter '*.vue')
$sourceRules = [ordered]@{
    '<el-submenu\b' = 'Legacy <el-submenu> tag remains'
    ':visible\.sync' = 'Legacy :visible.sync binding remains'
    '<el-[^>]*\bsize\s*=\s*"(?:mini|medium)"' = 'Retired Element size remains'
    '<el-[^>]*\b(?::)?icon\s*=\s*"el-icon-' = 'Legacy Element icon property remains'
    '<el-dropdown[\s\S]*?slot\s*=\s*"dropdown"' = 'Legacy Element dropdown slot remains'
    '(?m)^\s*[A-Za-z_$][\w$]*\s*:\s*\(\)\s*=>\s*import\(' = 'Async SFC component is not wrapped with defineAsyncComponent'
}
foreach ($vueFile in $vueFiles) {
    $source = Get-Content -LiteralPath $vueFile.FullName -Raw
    foreach ($rule in $sourceRules.GetEnumerator()) {
        if ([regex]::IsMatch($source, $rule.Key)) {
            throw "$($rule.Value): $($vueFile.FullName)"
        }
    }
}

Push-Location $frontendRoot
try {
    if (-not $SkipCleanInstall) {
        & npm ci "--registry=$registry"
        Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Clean frontend dependency installation'
    }

    & npm ls --depth=0
    Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Installed dependency tree check'

    $auditOutput = @(& npm audit --json "--registry=$registry" 2>$null)
    $auditText = [string]::Join("`n", $auditOutput)
    if ([string]::IsNullOrWhiteSpace($auditText)) {
        throw 'npm audit did not return JSON.'
    }
    $audit = $auditText | ConvertFrom-Json
    $vulnerabilities = $audit.metadata.vulnerabilities
    $auditSummary = [ordered]@{
        critical = [int]$vulnerabilities.critical
        high = [int]$vulnerabilities.high
        moderate = [int]$vulnerabilities.moderate
        low = [int]$vulnerabilities.low
        total = [int]$vulnerabilities.total
    }
    $auditSummary | ConvertTo-Json
    if ($auditSummary.critical -gt $MaxCriticalVulnerabilities -or
        $auditSummary.high -gt $MaxHighVulnerabilities -or
        $auditSummary.moderate -gt $MaxModerateVulnerabilities -or
        $auditSummary.low -gt $MaxLowVulnerabilities) {
        throw 'Frontend vulnerability counts exceeded the stage-eleven zero-vulnerability baseline.'
    }

    & npm run build
    Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Vue 3 Vite production build'
}
finally {
    Pop-Location
}

foreach ($requiredFile in @('index.html', 'favicon.ico')) {
    $requiredPath = Join-Path $distRoot $requiredFile
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Vite output is missing $requiredFile."
    }
}
if (Test-Path -LiteralPath (Join-Path $distRoot 'particles.js')) {
    throw 'The retired particles.js runtime remains in the production output.'
}

$indexHtml = Get-Content -LiteralPath (Join-Path $distRoot 'index.html') -Raw
if ($indexHtml -match '<%|htmlWebpackPlugin|cdnjs|cdn\.jsdelivr') {
    throw 'Vite output still contains a template expression or external runtime CDN reference.'
}

$javascriptFiles = @(Get-ChildItem -LiteralPath $distRoot -Recurse -File -Filter '*.js')
$cssFiles = @(Get-ChildItem -LiteralPath $distRoot -Recurse -File -Filter '*.css')
if ($javascriptFiles.Count -lt 1 -or $cssFiles.Count -lt 1) {
    throw 'Vite output does not contain both JavaScript and CSS assets.'
}
foreach ($javascriptFile in $javascriptFiles) {
    $javascriptText = Get-Content -LiteralPath $javascriptFile.FullName -Raw
    if ($javascriptText -match 'require\(["''](?:codemirror|particles\.js)') {
        throw "Browser-incompatible CommonJS call remains in $($javascriptFile.FullName)."
    }
}

$compressibleExtensions = @('.js', '.css', '.html', '.svg', '.json')
$largeCompressibleFiles = @(Get-ChildItem -LiteralPath $distRoot -Recurse -File | Where-Object {
        $compressibleExtensions -contains $_.Extension.ToLowerInvariant() -and $_.Length -ge 10240
    })
foreach ($asset in $largeCompressibleFiles) {
    if (-not (Test-Path -LiteralPath ($asset.FullName + '.gz') -PathType Leaf)) {
        throw "Precompressed Vite asset is missing: $($asset.FullName).gz"
    }
}

Write-Host "Stage-eleven Vue 3 compatibility verification passed on $nodeVersion."
Write-Host 'Dependency audit baseline: zero critical, high, moderate, and low vulnerabilities.'
