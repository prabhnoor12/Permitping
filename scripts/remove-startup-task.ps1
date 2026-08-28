param(
    [string]$TaskName = 'PermitPing'
)

$ErrorActionPreference = 'Stop'

$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($null -eq $task) {
    Write-Host "Startup task '$TaskName' is not installed."
    exit 0
}

Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
Write-Host "Removed the PermitPing startup task '$TaskName'." -ForegroundColor Green
