@echo off
setlocal
cd /d "%~dp0"

for %%I in ("%~dp0..") do set ROOT_DIR=%%~fI
set PYTHON=%ROOT_DIR%\venv\Scripts\python.exe
if not exist "%PYTHON%" set PYTHON=%ROOT_DIR%\runtime\python310\python.exe
set LOG_DIR=%ROOT_DIR%\logs
set CACHE_DIR=%ROOT_DIR%\model-cache
set CONFIG_PATH=%~dp0voice-punish-asr-service.json

if not exist "%PYTHON%" (
  echo [Voice Punish ASR] Python runtime not found. Please run install.bat first.
  exit /b 1
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

for /f "usebackq delims=" %%A in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$cfg = Get-Content -LiteralPath '%CONFIG_PATH%' -Raw -Encoding UTF8 | ConvertFrom-Json; Write-Output $cfg.host; Write-Output $cfg.port"`) do (
  if not defined VP_HOST (
    set "VP_HOST=%%A"
  ) else (
    set "VP_PORT=%%A"
  )
)

if "%VP_HOST%"=="" set VP_HOST=127.0.0.1
if "%VP_PORT%"=="" set VP_PORT=47831

set VOICE_PUNISH_ASR_CACHE=%CACHE_DIR%
echo [Voice Punish ASR] Starting service on %VP_HOST%:%VP_PORT%
"%PYTHON%" -m uvicorn app:app --host %VP_HOST% --port %VP_PORT% --app-dir "%~dp0" >> "%LOG_DIR%\service.log" 2>&1
