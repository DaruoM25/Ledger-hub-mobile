#Requires -Version 5.1
<#
.SYNOPSIS
    Déploiement et lancement automatisé en un clic de LedgerHub Mobile sur terminal physique Android.

.DESCRIPTION
    Détecte automatiquement le premier terminal actif ayant le statut "device" via ADB
    (filaire USB, Wi-Fi IP ou mDNS TLS), configure les redirections de ports nécessaires,
    compile l'APK debug, l'installe sur le terminal cible et lance l'application.

.PARAMETER Device
    Identifiant ADB explicite de l'appareil à cibler (ex: "adb-R5CW21ZSVQH-SnTJPq._adb-tls-connect._tcp" ou "192.168.1.161:43461").
    Si omis, le premier appareil au statut "device" retourné par `adb devices` est sélectionné automatiquement.

.PARAMETER SkipBuild
    Ignore la phase de compilation Gradle (:composeApp:assembleDebug) et installe l'APK déjà présent.

.PARAMETER NoDaemon
    Exécute Gradle avec le drapeau --no-daemon (activé par défaut pour éviter les verrous de fichiers sous Windows).

.EXAMPLE
    ./scripts/deploy-device.ps1
    ./scripts/deploy-device.ps1 -SkipBuild
    ./scripts/deploy-device.ps1 -Device "adb-R5CW21ZSVQH-SnTJPq._adb-tls-connect._tcp"
#>
[CmdletBinding()]
param(
    [string]$Device = "",
    [switch]$SkipBuild,
    [bool]$NoDaemon = $true
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Continue"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$packageName = "com.ledgerhub.app.debug"
$apkRelativePath = "composeApp/build/outputs/apk/debug/composeApp-debug.apk"
$apkPath = Join-Path $repoRoot $apkRelativePath

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "[!] $Message" -ForegroundColor Yellow
}

function Write-Err([string]$Message) {
    Write-Host "[ERREUR] $Message" -ForegroundColor Red
}

function Get-AdbPath {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    $sdkCandidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        "C:\Android\Sdk",
        "$env:LOCALAPPDATA\Android\Sdk"
    )
    foreach ($sdk in $sdkCandidates) {
        if ($sdk -and (Test-Path $sdk)) {
            $candidate = Join-Path $sdk "platform-tools\adb.exe"
            if (Test-Path $candidate) { return $candidate }
        }
    }
    throw "adb introuvable (ni dans le PATH, ni dans les répertoires SDK standards). Vérifie ton installation du SDK Android."
}

# 1. Vérification ADB et Détection dynamique du terminal actif
$adb = Get-AdbPath
Write-Step "Détection des terminaux Android connectés (adb devices)"

$rawDevices = & $adb devices
$activeDevices = @()

foreach ($line in ($rawDevices -split "`r?`n")) {
    $trimmed = $line.Trim()
    if ($trimmed -match '^(\S+)\s+device$') {
        $activeDevices += $matches[1]
    }
}

if ($activeDevices.Count -eq 0) {
    Write-Err "Aucun terminal Android au statut 'device' n'a été détecté."
    Write-Host ""
    Write-Host "Sortie brute de 'adb devices' :" -ForegroundColor DarkGray
    $rawDevices | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    Write-Host ""
    Write-Warn "Conseils :"
    Write-Warn "1. Vérifie le câble USB ou la connexion Wi-Fi ADB (adb connect <ip>:<port>)."
    Write-Warn "2. Assure-toi que le débogage USB est activé et autorisé sur l'appareil (accepter le prompt RSA)."
    exit 1
}

$targetDevice = ""
if ($Device) {
    if ($activeDevices -contains $Device) {
        $targetDevice = $Device
    } else {
        Write-Err "L'appareil spécifié '$Device' n'est pas dans la liste des terminaux actifs."
        Write-Host "Terminaux actifs disponibles : $($activeDevices -join ', ')" -ForegroundColor Yellow
        exit 1
    }
} else {
    $targetDevice = $activeDevices[0]
}

Write-Ok "Terminal cible sélectionné : $targetDevice"
if ($activeDevices.Count -gt 1) {
    Write-Host "  (Autres terminaux détectés : $(($activeDevices | Where-Object { $_ -ne $targetDevice }) -join ', '))" -ForegroundColor DarkGray
}

# 2. Configuration réseau (Redirections de ports tolérantes)
Write-Step "Configuration des redirections de ports (Reverse TCP)"
try {
    & $adb -s $targetDevice reverse tcp:8080 tcp:8080 2>$null | Out-Null
    Write-Ok "Port tcp:8080 redirigé vers l'appareil."
} catch {
    Write-Warn "Échec de la redirection du port 8080 (non bloquant)."
}

try {
    & $adb -s $targetDevice reverse tcp:3000 tcp:3000 2>$null | Out-Null
    Write-Ok "Port tcp:3000 redirigé vers l'appareil."
} catch {
    Write-Warn "Échec de la redirection du port 3000 (non bloquant)."
}

# 3. Compilation de l'APK debug
if (-not $SkipBuild) {
    Write-Step "Compilation de l'APK debug (:composeApp:assembleDebug)"
    $gradlew = Join-Path $repoRoot "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        throw "gradlew.bat introuvable à la racine ($repoRoot)."
    }

    $gradleArgs = @(":composeApp:assembleDebug")
    if ($NoDaemon) {
        $gradleArgs += "--no-daemon"
    }

    Write-Host "Exécution de : gradlew $($gradleArgs -join ' ')" -ForegroundColor DarkGray
    & $gradlew @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "La compilation Gradle a échoué (code $LASTEXITCODE)."
    }
    Write-Ok "Compilation de l'APK réussie."
} else {
    Write-Step "Compilation ignorée (-SkipBuild spécifié)"
}

if (-not (Test-Path $apkPath)) {
    throw "L'artéfact APK est introuvable : $apkPath"
}

# 4. Déploiement & Installation sur le terminal
Write-Step "Installation de l'APK sur $targetDevice"
Write-Host "Artéfact : $apkRelativePath" -ForegroundColor DarkGray
$installOutput = & $adb -s $targetDevice install -r $apkPath 2>&1
$installExit = $LASTEXITCODE
$hasSuccess = ($installOutput | Where-Object { $_ -match "Success" }).Count -gt 0

if ($installExit -ne 0 -or -not $hasSuccess) {
    Write-Err "L'installation a échoué sur $targetDevice."
    $installOutput | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
Write-Ok "Installation réussie sur $targetDevice."

# 5. Lancement automatique de l'application
Write-Step "Lancement automatique de l'application"
# Réveil et déverrouillage de l'écran si nécessaire
& $adb -s $targetDevice shell input keyevent 82 2>$null

# Lancement de l'activité principale via monkey
$launchOutput = & $adb -s $targetDevice shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>&1
$hasEvent = ($launchOutput | Where-Object { $_ -match "Events injected: 1" }).Count -gt 0

if ($hasEvent) {
    Write-Ok "Activité principale lancée avec succès."
} else {
    Write-Warn "Signal de lancement envoyé via monkey (détail : $($launchOutput -join ' '))"
}

# 6. Bilan & Feedback
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Green
Write-Host " DÉPLOIEMENT TERMINÉ AVEC SUCCÈS !" -ForegroundColor Green
Write-Host " Application : $packageName" -ForegroundColor Green
Write-Host " Terminal    : $targetDevice" -ForegroundColor Green
Write-Host "================================================================================" -ForegroundColor Green
Write-Host ""
