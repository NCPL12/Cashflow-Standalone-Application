$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\')
$war = [Regex]::Escape((Join-Path $root 'app\neptune-cashflow.war'))
$processes = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^javaw?\.exe$' -and $_.CommandLine -match $war
})
foreach ($process in $processes) { Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue }
if ($processes.Count) { Write-Host 'Neptune Cashflow stopped.' } else { Write-Host 'Neptune Cashflow was not running from this folder.' }
