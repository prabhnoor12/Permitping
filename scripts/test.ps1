$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& mvn test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
