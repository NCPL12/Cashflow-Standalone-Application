$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd('\')
try {
    $desktop = [Environment]::GetFolderPath('Desktop')
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut((Join-Path $desktop 'Neptune Cashflow.lnk'))
    $shortcut.TargetPath = Join-Path $root 'Start Neptune Cashflow.cmd'
    $shortcut.WorkingDirectory = $root
    $shortcut.IconLocation = Join-Path $root 'app\neptune-logo.ico'
    $shortcut.Description = 'Neptune Cashflow Analyzer - Finance'
    $shortcut.Save()
} catch {
    Write-Host 'Desktop shortcut could not be created. Use Start Neptune Cashflow.cmd in this folder.' -ForegroundColor Yellow
}
Write-Host 'This folder is now the active Neptune Cashflow installation. Keep it in this location.'
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'Launch-Neptune-Cashflow.ps1') -Activate
exit $LASTEXITCODE
