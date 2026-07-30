[CmdletBinding()]
param(
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command,
        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

$trackedSqlChanges = @(& git -C $repoRoot diff --name-only -- '*.sql')
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect tracked SQL changes."
}

$untrackedSqlChanges = @(& git -C $repoRoot ls-files --others --exclude-standard -- '*.sql')
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect untracked SQL changes."
}

$sqlChanges = @($trackedSqlChanges + $untrackedSqlChanges | Where-Object { $_ })
if ($sqlChanges.Count -gt 0) {
    throw "SQL changes are not allowed in phase 1: $($sqlChanges -join ', ')"
}

Push-Location (Join-Path $repoRoot 'hoj-springboot')
try {
    Invoke-CheckedCommand -Description 'Backend verification' -Command {
        & mvn -B -ntp clean verify '-DskipTests=false'
    }
}
finally {
    Pop-Location
}

if (-not $SkipFrontend) {
    Push-Location (Join-Path $repoRoot 'hoj-vue')
    try {
        Invoke-CheckedCommand -Description 'Frontend dependency installation' -Command {
            & npm ci
        }
        Invoke-CheckedCommand -Description 'Frontend critical vulnerability audit' -Command {
            & npm audit --omit=dev --audit-level=critical --registry=https://registry.npmjs.org/
        }
        Invoke-CheckedCommand -Description 'Frontend production build' -Command {
            & npm run build
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host 'Phase 1 verification completed without SQL changes or external integration tests.'
