param(
    [string]$TaskName = 'PermitPing'
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runScript = Join-Path $PSScriptRoot 'run.ps1'
if (-not (Test-Path -LiteralPath $runScript -PathType Leaf)) {
    throw "PermitPing launcher was not found at $runScript"
}

$userId = "$env:USERDOMAIN\$env:USERNAME"
$arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$runScript`""
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $userId
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Description 'Starts the local PermitPing compliance workspace when this Windows user signs in.' -Force | Out-Null
Write-Host "Installed the PermitPing startup task '$TaskName' for $userId." -ForegroundColor Green
Write-Host 'Remove it later with .\scripts\remove-startup-task.ps1'
