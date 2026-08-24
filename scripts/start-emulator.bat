@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-emulator.ps1" %*
exit /b %ERRORLEVEL%
