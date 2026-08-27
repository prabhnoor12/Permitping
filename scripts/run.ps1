$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
& mvn javafx:run
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
