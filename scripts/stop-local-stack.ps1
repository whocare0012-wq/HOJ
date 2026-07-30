[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$stackRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'output\local-stack'))
$outputRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'output')).TrimEnd('\')
$stateFile = Join-Path $stackRoot 'state.json'
if (-not (Test-Path -LiteralPath $stateFile -PathType Leaf)) {
    throw "Local stack state was not found: $stateFile"
}
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if ($state.projectName -ne 'hojlocal') {
    throw "Refusing to stop an unexpected Compose project: $($state.projectName)"
}
foreach ($file in @($state.composeFile, $state.envFile)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Local stack input was not found: $file"
    }
}

& docker compose --env-file $state.envFile -p $state.projectName -f $state.composeFile down --volumes --remove-orphans
if ($LASTEXITCODE -ne 0) {
    throw "Stopping the local container stack failed with exit code $LASTEXITCODE."
}

$resolved = [IO.Path]::GetFullPath($stackRoot)
if (-not $resolved.StartsWith($outputRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean a path outside the repository output directory: $resolved"
}
Remove-Item -LiteralPath $resolved -Recurse -Force
Write-Host 'Local container acceptance stack stopped and disposable data removed.'
