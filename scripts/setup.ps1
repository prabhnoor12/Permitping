$ErrorActionPreference = 'Stop'

function Require-Command([string]$Name, [string]$InstallHint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name was not found. $InstallHint"
    }
}

Require-Command 'java' 'Install JDK 17 or newer and add it to PATH.'
Require-Command 'mvn' 'Install Maven 3.9 or newer and add it to PATH.'

$javaVersion = (& cmd /c 'java -version 2>&1' | Select-Object -First 1)
Write-Host "Using $javaVersion"

New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot '..\data\documents') | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot '..\.m2\repository') | Out-Null
Write-Host "Using Maven local repository: $((Resolve-Path (Join-Path $PSScriptRoot '..\.m2')).Path)"
& mvn -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { throw 'Dependency download or compilation failed.' }

Write-Host 'PermitPing local environment is ready.' -ForegroundColor Green
Write-Host 'Run .\scripts\test.ps1 to verify or .\scripts\run.ps1 to launch the app.'
