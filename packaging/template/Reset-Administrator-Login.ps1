[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'Launch-Neptune-Cashflow.ps1') -ResetLogin
exit $LASTEXITCODE
