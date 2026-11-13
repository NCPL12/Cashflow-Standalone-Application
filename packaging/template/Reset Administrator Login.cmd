@echo off
setlocal EnableExtensions
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Launch-Neptune-Cashflow.ps1" -ResetLogin
if errorlevel 1 (
  echo.
  echo The login reset did not complete.
  pause
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$shortcut=Join-Path ([Environment]::GetFolderPath('Desktop')) 'Reset Neptune Cashflow Login.lnk'; Remove-Item -LiteralPath $shortcut -Force -ErrorAction SilentlyContinue"
exit /b 0
