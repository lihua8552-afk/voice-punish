@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-voice-punish-offline.ps1"
exit /b %ERRORLEVEL%
