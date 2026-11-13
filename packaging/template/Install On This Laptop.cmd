@echo off
setlocal EnableExtensions
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-Neptune-Cashflow.ps1"
if errorlevel 1 pause
endlocal
