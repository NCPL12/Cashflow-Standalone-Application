@echo off
setlocal
cd /d "%~dp0"
if not defined CASHFLOW_BOOTSTRAP_ADMIN_USERNAME (
  echo Set CASHFLOW_BOOTSTRAP_ADMIN_USERNAME in this session first.
  exit /b 1
)
if not defined CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD (
  echo Set CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD in this session first.
  exit /b 1
)
echo Stop the existing Cashflow app before resetting the administrator.
set "CASHFLOW_RESET_ADMIN=true"
java -jar target\neptune-cashflow.war --spring.profiles.active=cashflow-standalone --spring.config.additional-location=./config/
set "cashflowExit=%ERRORLEVEL%"
endlocal & exit /b %cashflowExit%
