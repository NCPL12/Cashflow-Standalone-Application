[CmdletBinding()]
param(
    [string]$PackagePath
)

$ErrorActionPreference = 'Stop'
$verificationRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($PackagePath)) {
    $PackagePath = Join-Path (Split-Path -Parent $verificationRoot) 'Neptune Cashflow Finance'
}
$packageRoot = [IO.Path]::GetFullPath($PackagePath).TrimEnd('\')
$checks = New-Object System.Collections.Generic.List[object]

function Assert-InstallerCheck([bool]$Passed, [string]$Name) {
    $checks.Add([pscustomobject]@{ check = $Name; passed = $Passed })
    if (-not $Passed) { throw "Installer verification failed: $Name" }
    Write-Host "PASS: $Name"
}

try {
    foreach ($required in @(
        'Install On This Laptop.cmd', 'Start Neptune Cashflow.cmd',
        'Install-Neptune-Cashflow.ps1', 'Launch-Neptune-Cashflow.ps1', 'Stop-Neptune-Cashflow.ps1',
        'Stop Neptune Cashflow.cmd', 'Reset Administrator Login.cmd',
        'runtime\bin\java.exe', 'runtime\bin\javaw.exe',
        'app\neptune-cashflow.war', 'app\neptune-logo.ico', 'config\application-cashflow-standalone.properties'
    )) {
        Assert-InstallerCheck (Test-Path -LiteralPath (Join-Path $packageRoot $required) -PathType Leaf) "Required package file: $required"
    }

    foreach ($script in Get-ChildItem -LiteralPath $packageRoot -Filter '*.ps1' -File) {
        $parseTokens = $null
        $parseErrors = $null
        [void][System.Management.Automation.Language.Parser]::ParseFile($script.FullName, [ref]$parseTokens, [ref]$parseErrors)
        Assert-InstallerCheck ($parseErrors.Count -eq 0) "PowerShell syntax: $($script.Name)"
    }

    $launcher = Get-Content -LiteralPath (Join-Path $packageRoot 'Launch-Neptune-Cashflow.ps1') -Raw
    Assert-InstallerCheck ($launcher.Contains("'--spring.profiles.active=cashflow-standalone'")) 'Launcher uses standalone profile'
    Assert-InstallerCheck (-not ($launcher -match '--spring\.profiles\.active=[^''\r\n]*(?:dev|cashflow-test)')) 'Launcher does not activate ERP development profiles'
    Assert-InstallerCheck (-not ($launcher -match '--server\.servlet\.context-path=[\x27\x22\s,]')) 'Launcher has no empty command-line context-path argument'
    Assert-InstallerCheck ($launcher.Contains("'--spring.config.additional-location=./config/'")) 'Launcher loads portable external configuration'
    Assert-InstallerCheck ($launcher.Contains('8081/cashflow-health')) 'Readiness checks application health endpoint'
    Assert-InstallerCheck ($launcher.Contains('-WindowStyle Hidden')) 'Background Java process stays hidden'
    Assert-InstallerCheck (-not ($launcher -match 'Remove-Item[^\r\n]*dataRoot')) 'Administrator reset does not delete the Finance database'

    $startCmd = Get-Content -LiteralPath (Join-Path $packageRoot 'Start Neptune Cashflow.cmd') -Raw
    $installCmd = Get-Content -LiteralPath (Join-Path $packageRoot 'Install On This Laptop.cmd') -Raw
    $installer = Get-Content -LiteralPath (Join-Path $packageRoot 'Install-Neptune-Cashflow.ps1') -Raw
    $stopCmd = Get-Content -LiteralPath (Join-Path $packageRoot 'Stop Neptune Cashflow.cmd') -Raw
    $resetCmd = Get-Content -LiteralPath (Join-Path $packageRoot 'Reset Administrator Login.cmd') -Raw
    Assert-InstallerCheck ($startCmd.Contains('-File "%~dp0Launch-Neptune-Cashflow.ps1"')) 'Start command quotes launcher path with spaces'
    Assert-InstallerCheck ($installCmd.Contains('-File "%~dp0Install-Neptune-Cashflow.ps1"')) 'Installer command delegates with quoted file path'
    Assert-InstallerCheck ($installer.Contains("-File (Join-Path `$root 'Launch-Neptune-Cashflow.ps1') -Activate")) 'Installer activates this extracted installation'
    Assert-InstallerCheck ($installer.Contains('WScript.Shell') -and $installer.Contains('Start Neptune Cashflow.cmd') -and $installer.Contains('neptune-logo.ico')) 'Installer contains Desktop shortcut configuration'
    Assert-InstallerCheck (-not $installer.Contains('%~dp0')) 'Installer does not interpolate CMD folder text into PowerShell expressions'
    Assert-InstallerCheck ($stopCmd.Contains('-File "%~dp0Stop-Neptune-Cashflow.ps1"')) 'Stop command delegates owned-process matching'
    Assert-InstallerCheck ($resetCmd.Contains('-File "%~dp0Launch-Neptune-Cashflow.ps1" -ResetLogin')) 'Reset command delegates database-preserving login reset'
    Assert-InstallerCheck (-not (Test-Path -LiteralPath (Join-Path $packageRoot 'runtime\runtime'))) 'Package contains one bundled runtime copy'

    $config = Get-Content -LiteralPath (Join-Path $packageRoot 'config\application-cashflow-standalone.properties') -Raw
    Assert-InstallerCheck ($config -match '(?m)^cashflow\.tally\.server\.url=http://127\.0\.0\.1:9000\s*$') 'Default Tally endpoint is local port 9000'
    Assert-InstallerCheck ($config -match '(?m)^server\.address=0\.0\.0\.0\s*$') 'Cashflow binds the host network interfaces'
    Assert-InstallerCheck ($config -match '(?m)^server\.port=8081\s*$') 'Shared Cashflow port is 8081'

    # Exercise the same WScript.Shell properties as installation without touching Desktop.
    $shortcutRoot = Join-Path $verificationRoot 'installer-shortcut-check'
    New-Item -ItemType Directory -Path $shortcutRoot -Force | Out-Null
    $shortcutPath = Join-Path $shortcutRoot 'Neptune Cashflow.lnk'
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = Join-Path $packageRoot 'Start Neptune Cashflow.cmd'
    $shortcut.WorkingDirectory = $packageRoot
    $shortcut.IconLocation = Join-Path $packageRoot 'app\neptune-logo.ico'
    $shortcut.Description = 'Neptune Cashflow Analyzer - Finance'
    $shortcut.Save()
    $loadedShortcut = $shell.CreateShortcut($shortcutPath)
    Assert-InstallerCheck ([string]::Equals($loadedShortcut.TargetPath, (Join-Path $packageRoot 'Start Neptune Cashflow.cmd'), [StringComparison]::OrdinalIgnoreCase)) 'Created shortcut points at this package Start command'
    Assert-InstallerCheck ([string]::Equals($loadedShortcut.WorkingDirectory.TrimEnd('\'), $packageRoot, [StringComparison]::OrdinalIgnoreCase)) 'Created shortcut preserves working directory'
    Assert-InstallerCheck ($loadedShortcut.IconLocation.StartsWith((Join-Path $packageRoot 'app\neptune-logo.ico'), [StringComparison]::OrdinalIgnoreCase)) 'Created shortcut uses bundled Neptune icon'
    Assert-InstallerCheck ($loadedShortcut.Description -eq 'Neptune Cashflow Analyzer - Finance') 'Created shortcut description matches installer'
    Assert-InstallerCheck ([IO.Path]::GetDirectoryName($shortcutPath) -eq $shortcutRoot) 'Shortcut verification stays inside release verification folder'

    $report = [pscustomobject]@{
        completed = (Get-Date -Format o)
        package = $packageRoot
        passed = $true
        desktopModified = $false
        firewallModified = $false
        checks = $checks.ToArray()
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $verificationRoot 'installer-verification.json') -Encoding UTF8
    Write-Host "Installer verification passed ($($checks.Count) checks). No Desktop or firewall settings were changed."
} catch {
    [pscustomobject]@{ completed = (Get-Date -Format o); package = $packageRoot; passed = $false; error = $_.Exception.Message; checks = $checks.ToArray() } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $verificationRoot 'installer-verification.json') -Encoding UTF8
    throw
}
