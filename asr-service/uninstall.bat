@echo off
setlocal
set INSTALL_ROOT=%LOCALAPPDATA%\VoicePunishASR
set STARTUP_CMD=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\Voice Punish ASR Service.cmd

call "%~dp0stop-service.bat" >nul 2>nul

if exist "%STARTUP_CMD%" del /f /q "%STARTUP_CMD%"
if exist "%INSTALL_ROOT%" rmdir /s /q "%INSTALL_ROOT%"

echo [Voice Punish ASR] Uninstall complete.
