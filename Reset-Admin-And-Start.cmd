@echo off
setlocal
cd /d "%~dp0"
echo Stop the existing Cashflow window with Ctrl+C before running this file.
echo Resetting the admin login and starting Neptune Cashflow...
set "CASHFLOW_RESET_ADMIN=true"
set "CASHFLOW_BOOTSTRAP_ADMIN_USERNAME=admin"
set "CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD=Neptune-7mK4-92qR"
java -jar target\neptune-cashflow.war --spring.profiles.active=cashflow-standalone --spring.config.additional-location=./config/
set "cashflowExit=%ERRORLEVEL%"
endlocal & exit /b %cashflowExit%
