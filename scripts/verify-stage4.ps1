[CmdletBinding()]
param(
    [string]$MySqlImage = 'mysql:8.0',
    [string]$RedisImage = 'redis:7-alpine',
    [string]$NacosImage = 'nacos/nacos-server:1.4.2',
    [string]$JavaPath = 'C:\Program Files\Java\jdk1.8.0_202\bin\java.exe',
    [string]$SandboxRuntimeImage = 'judge0/judge0:latest',
    [ValidateRange(1024, 65535)][int]$SandboxPort = 5050
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

& (Join-Path $PSScriptRoot 'verify-stage3.ps1') `
    -MySqlImage $MySqlImage `
    -RedisImage $RedisImage `
    -NacosImage $NacosImage `
    -JavaPath $JavaPath `
    -IncludeSandboxJudge `
    -SandboxRuntimeImage $SandboxRuntimeImage `
    -SandboxPort $SandboxPort
