param()

$ErrorActionPreference = "Stop"

function Write-Step($message) {
    Write-Host "[Voice Punish ASR] $message"
}

$sourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$installRoot = if ([string]::IsNullOrWhiteSpace($env:VOICE_PUNISH_ASR_INSTALL_ROOT)) {
    Join-Path $env:LOCALAPPDATA "VoicePunishASR"
} else {
    $env:VOICE_PUNISH_ASR_INSTALL_ROOT
}
$appDir = Join-Path $installRoot "app"
$runtimeDir = Join-Path $installRoot "runtime"
$miniforgeDir = Join-Path $runtimeDir "miniforge3"
$portablePythonDir = Join-Path $runtimeDir "python310"
$venvDir = Join-Path $installRoot "venv"
$logsDir = Join-Path $installRoot "logs"
$cacheDir = Join-Path $installRoot "model-cache"
$startupDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup"
$startupCmd = Join-Path $startupDir "Voice Punish ASR Service.cmd"
$miniforgeInstaller = Join-Path $runtimeDir "Miniforge3-Windows-x86_64.exe"
$miniforgeUrl = "https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-Windows-x86_64.exe"
$bundledMiniforgeDir = Join-Path $sourceDir "runtime\miniforge3"
$bundledPortablePythonDir = Join-Path $sourceDir "runtime\python310"
$bundledCacheDir = Join-Path $sourceDir "model-cache"
$registerStartupValue = if ($null -eq $env:VOICE_PUNISH_ASR_REGISTER_STARTUP) { "" } else { $env:VOICE_PUNISH_ASR_REGISTER_STARTUP.ToLowerInvariant() }
$autoStartValue = if ($null -eq $env:VOICE_PUNISH_ASR_AUTO_START) { "" } else { $env:VOICE_PUNISH_ASR_AUTO_START.ToLowerInvariant() }
$registerStartup = @("", "1", "true", "yes") -contains $registerStartupValue
$autoStart = @("", "1", "true", "yes") -contains $autoStartValue

New-Item -ItemType Directory -Force -Path $appDir, $runtimeDir, $logsDir, $cacheDir | Out-Null

$resolvedSourceDir = [System.IO.Path]::GetFullPath($sourceDir)
$resolvedAppDir = [System.IO.Path]::GetFullPath($appDir)
if ($resolvedSourceDir -ne $resolvedAppDir) {
    Write-Step "Copying service files to $appDir"
    Get-ChildItem -LiteralPath $sourceDir -File | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $appDir $_.Name) -Force
    }
} else {
    Write-Step "Using embedded service files already extracted to $appDir"
}

if (Test-Path (Join-Path $bundledPortablePythonDir "python.exe")) {
    if (-not (Test-Path (Join-Path $portablePythonDir "python.exe"))) {
        Write-Step "Copying bundled portable Python runtime"
        Copy-Item -LiteralPath $bundledPortablePythonDir -Destination $runtimeDir -Recurse -Force
    } else {
        Write-Step "Portable Python runtime already installed"
    }
} elseif (-not (Test-Path (Join-Path $miniforgeDir "python.exe"))) {
    if (Test-Path (Join-Path $bundledMiniforgeDir "python.exe")) {
        Write-Step "Copying bundled Miniforge runtime"
        Copy-Item -LiteralPath $bundledMiniforgeDir -Destination $runtimeDir -Recurse -Force
    } else {
        Write-Step "Downloading Miniforge runtime"
        Invoke-WebRequest -Uri $miniforgeUrl -OutFile $miniforgeInstaller

        Write-Step "Installing Miniforge silently"
        $installArgs = @(
            "/InstallationType=JustMe"
            "/RegisterPython=0"
            "/AddToPath=0"
            "/S"
            "/D=$miniforgeDir"
        )
        $proc = Start-Process -FilePath $miniforgeInstaller -ArgumentList $installArgs -PassThru -Wait
        if ($proc.ExitCode -ne 0) {
            throw "Miniforge install failed with exit code $($proc.ExitCode)"
        }
    }
} elseif (Test-Path (Join-Path $miniforgeDir "python.exe")) {
    Write-Step "Miniforge runtime already installed"
}

$basePython = if (Test-Path (Join-Path $portablePythonDir "python.exe")) {
    Join-Path $portablePythonDir "python.exe"
} else {
    Join-Path $miniforgeDir "python.exe"
}
if (-not (Test-Path $basePython)) {
    throw "Python runtime not found: $basePython"
}

$venvPython = Join-Path $venvDir "Scripts\python.exe"
if (Test-Path (Join-Path $portablePythonDir "python.exe")) {
    $venvPython = $basePython
} elseif (-not (Test-Path $venvPython)) {
    Write-Step "Creating isolated Python 3.11 runtime"
    & $basePython -m venv $venvDir
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create virtual environment"
    }
} else {
    Write-Step "Virtual environment already exists"
}
$skipDependencyInstall = Test-Path (Join-Path $portablePythonDir "python.exe")

if (-not $skipDependencyInstall) {
    Write-Step "Upgrading pip"
    & $venvPython -m pip install --upgrade pip setuptools wheel
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upgrade pip"
    }
}

$useCuda = $false
$nvidiaSmi = Get-Command "nvidia-smi" -ErrorAction SilentlyContinue
if ($nvidiaSmi) {
    $useCuda = $true
}

if (-not $skipDependencyInstall -and $useCuda) {
    Write-Step "Installing GPU-enabled PyTorch"
    & $venvPython -m pip install --upgrade torch --index-url https://download.pytorch.org/whl/cu124
    if ($LASTEXITCODE -ne 0) {
        Write-Step "GPU PyTorch install failed, falling back to CPU"
        $useCuda = $false
    }
}

if (-not $skipDependencyInstall -and -not $useCuda) {
    Write-Step "Installing CPU PyTorch"
    & $venvPython -m pip install --upgrade torch --index-url https://download.pytorch.org/whl/cpu
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install PyTorch"
    }
}

if (-not $skipDependencyInstall) {
    Write-Step "Installing FunASR service dependencies"
    & $venvPython -m pip install -r (Join-Path $appDir "requirements.txt")
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to install service requirements"
    }
} else {
    Write-Step "Using bundled offline Python dependencies"
}

if (-not $useCuda) {
    $configPath = Join-Path $appDir "voice-punish-asr-service.json"
    $config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $config.device = "cpu"
    $config | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $configPath -Encoding UTF8
}

if (Test-Path $bundledCacheDir) {
    Write-Step "Copying bundled model cache"
    Get-ChildItem -LiteralPath $bundledCacheDir | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $cacheDir -Recurse -Force
    }
}

Write-Step "Downloading / validating FunASR models"
$env:VOICE_PUNISH_ASR_CACHE = $cacheDir
& $venvPython (Join-Path $appDir "download_models.py")
if ($LASTEXITCODE -ne 0) {
    throw "Model download / validation failed"
}

if ($registerStartup) {
    Write-Step "Registering auto-start"
    $startupContent = @"
@echo off
set "VOICE_PUNISH_ASR_CACHE=$cacheDir"
start "" /min wscript.exe //B //Nologo "$appDir\start-service-hidden.vbs"
"@
    $startupContent | Set-Content -LiteralPath $startupCmd -Encoding ASCII
}

if ($autoStart) {
    Write-Step "Starting service in background"
    Start-Process -WindowStyle Hidden -FilePath "wscript.exe" -ArgumentList "//B", "//Nologo", (Join-Path $appDir "start-service-hidden.vbs")

    Start-Sleep -Seconds 4
    try {
        $health = Invoke-RestMethod -Uri "http://127.0.0.1:47831/healthz" -TimeoutSec 10
        if (-not $health.ok) {
            throw "healthz returned not ok"
        }
        Write-Step "Service is running and healthy"
    } catch {
        Write-Warning "[Voice Punish ASR] Service started, but health check did not pass yet. You can retry with start-service.bat."
    }
}

Write-Step "Install complete."
