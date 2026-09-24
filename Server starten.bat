@echo off
setlocal
title Casino - Server
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0plugin\start-server.ps1"
echo.
echo Der Server wurde beendet oder konnte nicht gestartet werden.
pause
endlocal
