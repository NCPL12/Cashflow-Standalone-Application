$ErrorActionPreference = 'Stop'
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process -FilePath 'powershell.exe' -Verb RunAs -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "' + $PSCommandPath + '"') -Wait
    exit
}
$ruleName = 'NeptuneCashflow8081Private'
if (Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue) {
    Set-NetFirewallRule -Name $ruleName -Enabled True -Profile Private,Domain
    Get-NetFirewallRule -Name $ruleName | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress LocalSubnet
} else {
    New-NetFirewallRule -Name $ruleName -DisplayName 'Neptune Cashflow - office network' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8081 -Profile Private,Domain -RemoteAddress LocalSubnet | Out-Null
}
Write-Host 'Cashflow network access is enabled on TCP 8081 for the local private/domain network.' -ForegroundColor Green
Write-Host 'Other PCs can open http://HOST-IP:8081/login. Keep Cashflow running on this PC.'
Read-Host 'Press Enter to close'
