param(
    [string]$InstanceDir
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Windows.Forms

function Write-Step($message) {
    Write-Host "[Voice Punish Installer] $message"
}

function Resolve-InstanceDir([string]$ProvidedDir) {
    if (-not [string]::IsNullOrWhiteSpace($ProvidedDir) -and (Test-Path $ProvidedDir)) {
        return (Resolve-Path $ProvidedDir).Path
    }

    $candidates = @(
        (Join-Path $env:APPDATA ".minecraft"),
        (Join-Path $env:USERPROFILE "Desktop\mc\1.21.11生存rpg\versions\1.21.11-Fabric 0.19.1")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "mods")) {
            return (Resolve-Path $candidate).Path
        }
    }

    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
    $dialog.Description = "Select your Minecraft instance folder"
    $dialog.ShowNewFolderButton = $false
    $result = $dialog.ShowDialog()
    if ($result -ne [System.Windows.Forms.DialogResult]::OK) {
        throw "Installation cancelled because no Minecraft instance folder was selected."
    }
    return $dialog.SelectedPath
}

function Backup-IfExists([string]$TargetPath) {
    if (Test-Path $TargetPath) {
        $backup = "${TargetPath}.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Move-Item -LiteralPath $TargetPath -Destination $backup -Force
        Write-Step "Backed up existing file to $backup"
    }
}

function Copy-Tree([string]$SourcePath, [string]$TargetPath) {
    New-Item -ItemType Directory -Force -Path $TargetPath | Out-Null
    $null = robocopy $SourcePath $TargetPath /MIR /NFL /NDL /NJH /NJS /NC /NS
    if ($LASTEXITCODE -gt 7) {
        throw "robocopy failed for $SourcePath -> $TargetPath with exit code $LASTEXITCODE"
    }
}

$packageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$modJar = Join-Path $packageRoot "mods\__ARCHIVES_BASE_NAME__-__VERSION__.jar"
$offlineRoot = Join-Path $packageRoot "offline-asr"
$installRoot = Join-Path $env:LOCALAPPDATA "VoicePunishASR\embedded\__VERSION__"
$instanceRoot = Resolve-InstanceDir $InstanceDir
$modsDir = Join-Path $instanceRoot "mods"

if (-not (Test-Path $modJar)) {
    throw "Missing mod jar in installer package: $modJar"
}
if (-not (Test-Path (Join-Path $offlineRoot "app"))) {
    throw "Missing offline ASR bundle in installer package: $offlineRoot"
}

Write-Step "Target instance: $instanceRoot"
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null

$targetMod = Join-Path $modsDir "__ARCHIVES_BASE_NAME__-__VERSION__.jar"
Backup-IfExists $targetMod
Copy-Item -LiteralPath $modJar -Destination $targetMod -Force
Write-Step "Installed mod jar to $targetMod"

if (Test-Path $installRoot) {
    $backupRoot = "${installRoot}.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    Move-Item -LiteralPath $installRoot -Destination $backupRoot -Force
    Write-Step "Backed up previous offline ASR files to $backupRoot"
}
New-Item -ItemType Directory -Force -Path $installRoot | Out-Null

Write-Step "Copying offline ASR runtime and model files. This can take several minutes."
Copy-Tree (Join-Path $offlineRoot "app") (Join-Path $installRoot "app")
Copy-Tree (Join-Path $offlineRoot "runtime") (Join-Path $installRoot "runtime")
Copy-Tree (Join-Path $offlineRoot "model-cache") (Join-Path $installRoot "model-cache")

$required = @{
    "fabric-api" = "fabric-api*.jar"
    "architectury" = "architectury*.jar"
    "shriek" = "shriek*.jar"
    "simple-voice-chat" = "simple-voice-chat*.jar"
}
$missing = @()
foreach ($pair in $required.GetEnumerator()) {
    if (-not (Get-ChildItem $modsDir -Filter $pair.Value -ErrorAction SilentlyContinue)) {
        $missing += $pair.Key
    }
}

Write-Step "Starting local ASR once so the cache is warmed up"
$env:VOICE_PUNISH_ASR_INSTALL_ROOT = $installRoot
$env:VOICE_PUNISH_ASR_CACHE = Join-Path $installRoot "model-cache"
Start-Process -WindowStyle Hidden -FilePath "cmd.exe" -ArgumentList "/c", "start-service.bat" -WorkingDirectory (Join-Path $installRoot "app") | Out-Null

try {
    Start-Sleep -Seconds 10
    $response = Invoke-RestMethod -Uri "http://127.0.0.1:47831/healthz" -TimeoutSec 20
    if ($response.ok) {
        Write-Step "Offline ASR is ready"
    } else {
        Write-Step "Offline ASR started, but the model is still loading"
    }
} catch {
    Write-Step "Offline ASR files were installed. If first launch is slow, let the game sit for a short while."
}

if ($missing.Count -gt 0) {
    Write-Warning "[Voice Punish Installer] Missing game-side mod dependencies: $($missing -join ', ')"
    Write-Warning "[Voice Punish Installer] Please make sure the target instance already has those mods installed."
}

Write-Step "Installation finished. You can launch the game now."
$noPauseValue = if ($null -eq $env:VOICE_PUNISH_INSTALLER_NOPAUSE) { "" } else { $env:VOICE_PUNISH_INSTALLER_NOPAUSE.ToLowerInvariant() }
if ($noPauseValue -notin @("1", "true", "yes")) {
    pause
}
exit 0
