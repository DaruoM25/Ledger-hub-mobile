#Requires -Version 5.1
<#
.SYNOPSIS
    Automatisation QA "one-click" pour Ledger-hub-mobile : N1/N2 (unit + Robolectric), N3b
    (instrumenté) et rapatriement des captures d'écran, réutilisable story après story.

.DESCRIPTION
    Windows / Gradle pose deux problèmes récurrents sur ce projet : le daemon Gradle qui garde un
    verrou sur le fichier SQLite / les DLL natives entre deux runs, et l'absence d'émulateur
    démarré en tâche de fond. Ce script encapsule les contournements (--no-daemon, détection puis
    lancement d'AVD, rapatriement adb) pour qu'une story se valide par un seul appel.

.PARAMETER Suite
    Unit         : N1 (commonTest) + N2/N3a (androidUnitTest, dont Robolectric) via testDebugUnitTest.
    Instrumented : N3b (androidInstrumentedTest) via connectedDebugAndroidTest, capture comprise.
    All          : Unit puis Instrumented (défaut).

.PARAMETER ScreenshotPrefix
    Préfixe du nom de fichier des captures à rapatrier depuis /sdcard/Download (ex: "US13").
    Vide = toutes les captures présentes dans /sdcard/Download sont rapatriées.

.PARAMETER AvdName
    Nom de l'AVD à démarrer si aucun appareil n'est détecté par `adb devices`. Si omis, le script
    alerte et s'arrête plutôt que de deviner un émulateur.

.PARAMETER NoDaemon
    Ajoute --no-daemon aux appels Gradle (par défaut : activé — voir la section DESCRIPTION).
    Passer -NoDaemon:$false pour laisser le daemon actif (itération rapide en dev).

.EXAMPLE
    ./scripts/run-qa.ps1 -Suite Unit
    ./scripts/run-qa.ps1 -Suite Instrumented -ScreenshotPrefix "US13" -AvdName "Pixel_5_API_35"
    ./scripts/run-qa.ps1 -Suite All -ScreenshotPrefix "US13" -AvdName "Pixel_5_API_35"
#>
[CmdletBinding()]
param(
    [ValidateSet("Unit", "Instrumented", "All")]
    [string]$Suite = "All",

    [string]$ScreenshotPrefix = "",

    [string]$AvdName = "",

    [bool]$NoDaemon = $true,

    [int]$EmulatorBootTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$screenshotsDir = Join-Path $repoRoot "screenshots"
if (-not (Test-Path $screenshotsDir)) {
    New-Item -ItemType Directory -Path $screenshotsDir | Out-Null
}

$gradlew = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradlew)) {
    throw "gradlew.bat introuvable à la racine du dépôt ($repoRoot). Ce script doit rester dans scripts/."
}

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

function Invoke-Gradle([string[]]$Tasks) {
    $args = @($Tasks)
    if ($NoDaemon) { $args += "--no-daemon" }
    Write-Host "gradlew $($args -join ' ')" -ForegroundColor DarkGray

    & $gradlew @args
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Gradle a échoué (code $exitCode) sur : $($args -join ' ')"
    }
}

function Get-AdbPath {
    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    $sdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_HOME }
    if ($sdkRoot) {
        $candidate = Join-Path $sdkRoot "platform-tools\adb.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    throw "adb introuvable (ni dans PATH, ni sous ANDROID_SDK_ROOT/ANDROID_HOME/platform-tools)."
}

function Get-EmulatorPath {
    $sdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_HOME }
    if ($sdkRoot) {
        $candidate = Join-Path $sdkRoot "emulator\emulator.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $fromPath = Get-Command emulator -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }
    return $null
}

function Test-DeviceOnline([string]$Adb) {
    $raw = & $Adb devices
    # Ligne attendue : "<serial>\tdevice" — on exclut les lignes d'en-tête et "offline"/"unauthorized".
    $devices = $raw | Select-String -Pattern "^\S+\s+device$"
    return $devices.Count -gt 0
}

function Ensure-DeviceReady {
    $adb = Get-AdbPath
    Write-Step "Vérification des appareils Android connectés (adb devices)"

    if (Test-DeviceOnline -Adb $adb) {
        Write-Ok "Appareil détecté."
        return $adb
    }

    Write-Warn "Aucun appareil détecté."

    if (-not $AvdName) {
        throw "Aucun appareil et aucun -AvdName fourni. Démarre un émulateur ou passe -AvdName '<nom_avd>'."
    }

    $emulator = Get-EmulatorPath
    if (-not $emulator) {
        throw "Aucun appareil détecté et 'emulator' introuvable pour démarrer '$AvdName'. Vérifie ANDROID_SDK_ROOT."
    }

    Write-Step "Lancement de l'AVD '$AvdName' en arrière-plan"
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName, "-netdelay", "none", "-netspeed", "full") -WindowStyle Minimized

    Write-Host "Attente du démarrage (timeout ${EmulatorBootTimeoutSeconds}s)..."
    $deadline = (Get-Date).AddSeconds($EmulatorBootTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        if (Test-DeviceOnline -Adb $adb) {
            # Le device apparaît avant que le boot soit terminé : on attend sys.boot_completed.
            $booted = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
            if ($booted -eq "1") {
                Write-Ok "Émulateur '$AvdName' prêt."
                return $adb
            }
        }
    }
    throw "L'émulateur '$AvdName' n'a pas terminé son démarrage sous ${EmulatorBootTimeoutSeconds}s."
}

function Invoke-UnitSuite {
    # N1 (commonTest) + N2/N3a (androidUnitTest, dont Robolectric) : une seule tâche Gradle.
    Write-Step "N1 / N2 / N3a — testDebugUnitTest"
    Invoke-Gradle @(":composeApp:testDebugUnitTest")
    Write-Ok "Suite unitaire (N1/N2/N3a) terminée."

    $reportDir = Join-Path $repoRoot "composeApp\build\test-results\testDebugUnitTest"
    if (Test-Path $reportDir) {
        $xmlFiles = Get-ChildItem -Path $reportDir -Filter "*.xml" -File -ErrorAction SilentlyContinue
        $total = 0; $failures = 0; $errors = 0; $skipped = 0
        foreach ($xml in $xmlFiles) {
            [xml]$doc = Get-Content $xml.FullName
            $suite = $doc.testsuite
            if ($suite) {
                $total    += [int]$suite.tests
                $failures += [int]$suite.failures
                $errors   += [int]$suite.errors
                $skipped  += [int]$suite.skipped
            }
        }
        Write-Host ""
        Write-Host "Résultats unitaires : $total tests, $failures échecs, $errors erreurs, $skipped ignorés." -ForegroundColor White
    }
}

function Invoke-InstrumentedSuite {
    Ensure-DeviceReady | Out-Null

    Write-Step "N3b — connectedDebugAndroidTest"
    Invoke-Gradle @(":composeApp:connectedDebugAndroidTest")
    Write-Ok "Suite instrumentée (N3b) terminée."

    Pull-Screenshots
}

function Pull-Screenshots {
    $adb = Get-AdbPath
    Write-Step "Rapatriement des captures depuis /sdcard/Download"

    $remotePattern = if ($ScreenshotPrefix) { "/sdcard/Download/${ScreenshotPrefix}*.png" } else { "/sdcard/Download/*.png" }
    $remoteListRaw = & $adb shell "ls $remotePattern 2>/dev/null"
    $remoteFiles = $remoteListRaw | Where-Object { $_ -and $_.Trim() -ne "" } | ForEach-Object { $_.Trim() }

    if (-not $remoteFiles -or $remoteFiles.Count -eq 0) {
        Write-Warn "Aucune capture trouvée sous /sdcard/Download (motif : $remotePattern)."
        return @()
    }

    $pulled = @()
    foreach ($remote in $remoteFiles) {
        $name = Split-Path -Leaf $remote
        $local = Join-Path $screenshotsDir $name
        & $adb pull $remote $local | Out-Null

        if (-not (Test-Path $local)) {
            Write-Warn "Échec du rapatriement de $remote"
            continue
        }
        $size = (Get-Item $local).Length
        if ($size -le 0) {
            Write-Warn "$name rapatriée mais vide (0 octet) — capture suspecte."
            continue
        }
        Write-Ok "$name rapatriée ($size octets) -> $local"
        $pulled += $local
    }
    return $pulled
}

Write-Host "Ledger-hub-mobile — QA one-click (suite: $Suite)" -ForegroundColor Magenta

$startedAt = Get-Date
switch ($Suite) {
    "Unit" {
        Invoke-UnitSuite
    }
    "Instrumented" {
        Invoke-InstrumentedSuite
    }
    "All" {
        Invoke-UnitSuite
        Invoke-InstrumentedSuite
    }
}
$elapsed = (Get-Date) - $startedAt

Write-Host ""
Write-Host "=====================================================" -ForegroundColor Magenta
Write-Ok "Suite '$Suite' terminée en $([int]$elapsed.TotalSeconds)s."
Write-Host "=====================================================" -ForegroundColor Magenta
