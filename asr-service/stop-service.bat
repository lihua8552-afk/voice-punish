@echo off
setlocal
for /f "tokens=5" %%P in ('netstat -ano ^| findstr :47831 ^| findstr LISTENING') do (
  echo [Voice Punish ASR] Stopping PID %%P
  taskkill /PID %%P /F >nul 2>nul
)
echo [Voice Punish ASR] Stop command finished.
