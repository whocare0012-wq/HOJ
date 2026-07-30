[CmdletBinding()]
param(
    [string]$JavaPath = 'C:\Program Files\Java\jdk1.8.0_202\bin\java.exe',
    [switch]$SkipIsolatedRuntime
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'hoj-springboot'
$rootPom = Join-Path $backendRoot 'pom.xml'
$backendPom = Join-Path $backendRoot 'DataBackup\pom.xml'
$judgePom = Join-Path $backendRoot 'JudgeServer\pom.xml'
$sqlFile = Join-Path $repoRoot 'sqlAndsetting\hoj.sql'
$backendJar = Join-Path $backendRoot 'DataBackup\target\hoj-backend-4.6.jar'
$judgeJar = Join-Path $backendRoot 'JudgeServer\target\hoj-judgeServer-4.6.jar'

function Assert-CommandExists {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command is not available: $Name"
    }
}

function Invoke-CheckedNative {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Command,
        [Parameter(Mandatory = $true)][string]$Description
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Assert-PomProperty {
    param(
        [Parameter(Mandatory = $true)]$Properties,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Expected
    )

    $actual = [string]$Properties.$Name
    if ($actual -ne $Expected) {
        throw "Expected Maven property $Name=$Expected but found '$actual'."
    }
}

function Assert-NacosClientPin {
    param([Parameter(Mandatory = $true)][string]$PomPath)

    [xml]$pom = Get-Content -LiteralPath $PomPath -Raw -Encoding UTF8
    $namespace = New-Object Xml.XmlNamespaceManager($pom.NameTable)
    $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    $versionNode = $pom.SelectSingleNode(
        "/m:project/m:dependencies/m:dependency[m:groupId='com.alibaba.nacos' and m:artifactId='nacos-client']/m:version",
        $namespace
    )
    if ($null -eq $versionNode -or $versionNode.InnerText.Trim() -ne '1.4.2') {
        throw "Nacos client must remain explicitly pinned to 1.4.2 in $PomPath."
    }
}

function Assert-PackagedLibraries {
    param(
        [Parameter(Mandatory = $true)][string]$JarPath,
        [Parameter(Mandatory = $true)][string]$JarTool
    )

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "Expected packaged service JAR was not found: $JarPath"
    }
    $entries = @(& $JarTool tf $JarPath)
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect packaged service JAR: $JarPath"
    }
    foreach ($requiredEntry in @(
            'BOOT-INF/lib/spring-boot-2.7.18.jar',
            'BOOT-INF/lib/spring-cloud-starter-alibaba-nacos-config-2021.0.6.0.jar',
            'BOOT-INF/lib/nacos-client-1.4.2.jar'
        )) {
        if ($entries -notcontains $requiredEntry) {
            throw "Packaged service JAR does not contain the required dependency $requiredEntry`: $JarPath"
        }
    }
    $unexpectedNacosClients = @($entries | Where-Object {
            $_ -like 'BOOT-INF/lib/nacos-client-*.jar' -and $_ -ne 'BOOT-INF/lib/nacos-client-1.4.2.jar'
        })
    if ($unexpectedNacosClients.Count -gt 0) {
        throw "Packaged service JAR contains an unexpected Nacos client: $($unexpectedNacosClients -join ', ')"
    }
}

Assert-CommandExists -Name 'git'
Assert-CommandExists -Name 'mvn'
if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) {
    throw "Java 8 executable was not found: $JavaPath"
}

$trackedSqlChanges = @(& git -C $repoRoot diff --name-only -- '*.sql')
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect tracked SQL changes.'
}
$untrackedSqlChanges = @(& git -C $repoRoot ls-files --others --exclude-standard -- '*.sql')
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to inspect untracked SQL changes.'
}
$sqlChanges = @($trackedSqlChanges + $untrackedSqlChanges | Where-Object { $_ })
if ($sqlChanges.Count -gt 0) {
    throw "SQL changes are not allowed in phase 8: $($sqlChanges -join ', ')"
}

[xml]$parentPom = Get-Content -LiteralPath $rootPom -Raw -Encoding UTF8
$properties = $parentPom.project.properties
Assert-PomProperty -Properties $properties -Name 'spring-boot.version' -Expected '2.7.18'
Assert-PomProperty -Properties $properties -Name 'spring-cloud.version' -Expected '2021.0.9'
Assert-PomProperty -Properties $properties -Name 'spring-cloud-alibaba.version' -Expected '2021.0.6.0'
Assert-NacosClientPin -PomPath $backendPom
Assert-NacosClientPin -PomPath $judgePom

$javaHome = Split-Path -Parent (Split-Path -Parent ([IO.Path]::GetFullPath($JavaPath)))
$jarTool = Join-Path $javaHome 'bin\jar.exe'
if (-not (Test-Path -LiteralPath $jarTool -PathType Leaf)) {
    throw "Java archive tool was not found: $jarTool"
}
$previousErrorAction = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& $JavaPath -version 2>&1 | Out-String)
$javaVersionExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorAction
if ($javaVersionExitCode -ne 0 -or $javaVersion -notmatch 'version "1\.8\.') {
    throw "Phase 8 must be built with Java 8, but Java reported: $($javaVersion.Trim())"
}

$savedJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
$savedPath = [Environment]::GetEnvironmentVariable('Path', 'Process')
$sqlHashBefore = (Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash
try {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaHome, 'Process')
    [Environment]::SetEnvironmentVariable('Path', "$javaHome\bin;$savedPath", 'Process')

    Write-Host 'Building the final Java 8-compatible Spring framework line and running safe tests...'
    Invoke-CheckedNative -Description 'Stage-eight Java 8 backend verification' -Command {
        & mvn -B -ntp -f $rootPom clean verify '-DskipTests=false'
    }

    Assert-PackagedLibraries -JarPath $backendJar -JarTool $jarTool
    Assert-PackagedLibraries -JarPath $judgeJar -JarTool $jarTool

    if (-not $SkipIsolatedRuntime) {
        Write-Host 'Running the isolated MySQL, Redis, and Nacos runtime smoke test...'
        & (Join-Path $PSScriptRoot 'verify-stage3.ps1') -JavaPath $JavaPath
    }
}
finally {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $savedJavaHome, 'Process')
    [Environment]::SetEnvironmentVariable('Path', $savedPath, 'Process')
}

$sqlHashAfter = (Get-FileHash -LiteralPath $sqlFile -Algorithm SHA256).Hash
if ($sqlHashAfter -ne $sqlHashBefore) {
    throw 'The checked-in hoj.sql file changed during stage-eight verification.'
}

Write-Host 'Stage-eight Java 8 framework-line verification passed without SQL changes.'
