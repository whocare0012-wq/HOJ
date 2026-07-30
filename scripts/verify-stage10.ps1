[CmdletBinding()]
param(
    [switch]$SkipCleanInstall,
    [int]$MaxCriticalVulnerabilities = 0,
    [int]$MaxHighVulnerabilities = 1,
    [int]$MaxModerateVulnerabilities = 3,
    [int]$MaxLowVulnerabilities = 9
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'hoj-vue'
$packageJsonPath = Join-Path $frontendRoot 'package.json'
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

$sqlStatus = @(& git -C $repoRoot status --porcelain -- sqlAndsetting)
Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'SQL worktree status check'
if ($sqlStatus.Count -gt 0) {
    throw "Stage 10 does not permit SQL changes: $($sqlStatus -join ', ')"
}

$nodeVersion = (& node --version).Trim()
Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Node.js version check'
if ($nodeVersion -notmatch '^v(?<major>\d+)\.') {
    throw "Unable to parse Node.js version: $nodeVersion"
}
if ([int]$Matches.major -lt 20) {
    throw "Stage 10 requires Node.js 20 or newer but found $nodeVersion."
}

$packageJson = Get-Content -LiteralPath $packageJsonPath -Raw | ConvertFrom-Json
if ($packageJson.scripts.build -ne 'vite build') {
    throw 'Frontend build script must use Vite.'
}
if ($packageJson.devDependencies.vite -ne '6.4.3' -or $packageJson.devDependencies.'@vitejs/plugin-vue2' -ne '2.3.4') {
    throw 'Stage 10 requires the reviewed Vite 6.4.3 and @vitejs/plugin-vue2 2.3.4 bridge.'
}
if ($packageJson.dependencies.vue -ne '^2.7.16') {
    throw 'Stage 10 must remain on the Vue 2.7.16 compatibility boundary.'
}

$dependencyNames = @(Get-DependencyNames $packageJson.dependencies) + @(Get-DependencyNames $packageJson.devDependencies)
$forbiddenPackages = @(
    '@vue/cli-service',
    '@vue/cli-plugin-babel',
    '@vue/cli-plugin-router',
    '@vue/cli-plugin-vuex',
    'compression-webpack-plugin',
    'vue-template-compiler',
    'vue-codemirror-lite',
    'vue-particles',
    'webpack-bundle-analyzer'
)
foreach ($forbiddenPackage in $forbiddenPackages) {
    if ($dependencyNames -contains $forbiddenPackage) {
        throw "Deprecated frontend package remains in package.json: $forbiddenPackage"
    }
}

Push-Location $frontendRoot
try {
    if (-not $SkipCleanInstall) {
        & npm ci "--registry=$registry"
        Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Clean frontend dependency installation'
    }

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
        throw 'Frontend vulnerability counts exceeded the reviewed stage-ten baseline.'
    }

    & npm run build
    Assert-CommandSucceeded -ExitCode $LASTEXITCODE -Description 'Vite production build'
}
finally {
    Pop-Location
}

foreach ($requiredFile in @('index.html', 'favicon.ico', 'particles.js')) {
    $requiredPath = Join-Path $distRoot $requiredFile
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Vite output is missing $requiredFile."
    }
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

Write-Host "Stage-ten Vue 2.7/Vite bridge verification passed on $nodeVersion."
Write-Host 'The remaining reviewed high vulnerability is the Vue-2-only vxe-table line and requires the separate Vue 3 migration.'
