#Requires -Version 5.1
<#
.SYNOPSIS
    Livraison reproductible d'un APK Android Ledger-hub-mobile : build, tag Git et publication
    GitHub Releases en un seul appel.

.DESCRIPTION
    Chaine complete de release mobile :
      1. Controles prealables (gh installe et authentifie, arbre Git propre, branche main
         synchronisee avec origin/main, tag et release encore libres) ;
      2. Compilation de l'APK via le wrapper Gradle ;
      3. Copie de l'artefact vers release/ledgerhub-<Version>.apk ;
      4. Pose et push du tag v<Version> ;
      5. gh release create avec l'APK attache et des notes generees depuis le journal Git ;
      6. Affichage de l'URL de telechargement de l'APK.

    Le depot etant prive, l'URL affichee exige une session GitHub authentifiee cote navigateur
    mobile. Rendre le depot public est necessaire pour un telechargement anonyme.

.PARAMETER Version
    Version semantique sans le prefixe "v" (ex: "1.0.0-rc2", "1.0.0"). Obligatoire.

.PARAMETER BuildType
    Debug (defaut) : assembleDebug, APK signe avec la cle de debug, installable immediatement.
    Release        : assembleRelease, minifie ; sans signingConfig le binaire sort non signe et
                     n'est pas installable tel quel sur un appareil.

.PARAMETER Notes
    Notes de release. Si omis, elles sont generees a partir des commits depuis le tag precedent.

.PARAMETER SkipBuild
    Reutilise l'APK deja present dans composeApp/build/outputs (aucun appel Gradle).

.PARAMETER Draft
    Publie la release en brouillon (invisible tant qu'elle n'est pas publiee manuellement).

.PARAMETER NoDaemon
    Ajoute --no-daemon a l'appel Gradle (defaut : active, cf. scripts/run-qa.ps1).

.EXAMPLE
    ./scripts/release.ps1 -Version 1.0.0-rc2

.EXAMPLE
    ./scripts/release.ps1 -Version 1.0.0 -BuildType Release -Notes "Version stable initiale"
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$')]
    [string] $Version,

    [ValidateSet('Debug', 'Release')]
    [string] $BuildType = 'Debug',

    [string] $Notes,

    [switch] $SkipBuild,

    [switch] $Draft,

    [switch] $NoDaemon = $true
)

$ErrorActionPreference = 'Stop'

function Write-Step ([string] $Message) { Write-Host "`n==> $Message" -ForegroundColor Cyan }
function Write-Ok   ([string] $Message) { Write-Host "    OK  $Message" -ForegroundColor Green }
function Write-Warn ([string] $Message) { Write-Host "    /!\ $Message" -ForegroundColor Yellow }
function Fail       ([string] $Message) { Write-Host "    ERR $Message" -ForegroundColor Red; exit 1 }

# Le script vit dans scripts/ : la racine du depot est son parent.
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$Tag        = "v$Version"
$ReleaseDir = Join-Path $RepoRoot 'release'
$TargetApk  = Join-Path $ReleaseDir "ledgerhub-$Version.apk"
$VariantDir = $BuildType.ToLower()

# ------------------------------------------------------------------ 1. Prerequis
Write-Step "Controles prealables ($Tag, build $BuildType)"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Fail 'GitHub CLI (gh) introuvable dans le PATH. Installer depuis https://cli.github.com/.'
}
gh auth status *> $null
if (-not $?) { Fail "gh non authentifie. Lancer 'gh auth login' puis relancer." }
Write-Ok 'gh installe et authentifie'

$dirty = git status --porcelain
if ($dirty) {
    Write-Host $dirty
    Fail 'Arbre de travail non propre. Commiter ou remiser les changements avant de publier.'
}
Write-Ok 'Arbre de travail propre'

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne 'main') { Fail "Branche courante '$branch' : la release se pose depuis main." }

git fetch origin main --tags --quiet
$local  = (git rev-parse HEAD).Trim()
$remote = (git rev-parse origin/main).Trim()
if ($local -ne $remote) {
    Fail "main local ($($local.Substring(0,7))) desynchronise d'origin/main ($($remote.Substring(0,7))). Faire git pull / git push."
}
Write-Ok "main synchronise sur origin/main ($($local.Substring(0,7)))"

if (git tag --list $Tag) {
    Fail "Le tag $Tag existe deja en local. Choisir une autre version ou supprimer le tag."
}
gh release view $Tag *> $null
if ($?) { Fail "Une release $Tag existe deja sur GitHub. Choisir une autre version." }
Write-Ok "$Tag disponible"

# ------------------------------------------------------------------ 2. Compilation
$gradleTask = ":composeApp:assemble$BuildType"
if ($SkipBuild) {
    Write-Step 'Compilation ignoree (-SkipBuild), reutilisation des sorties existantes'
} else {
    Write-Step "Compilation : gradlew.bat $gradleTask"
    $gradleArgs = @($gradleTask)
    if ($NoDaemon) { $gradleArgs += '--no-daemon' }
    & (Join-Path $RepoRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) { Fail "Echec de la compilation Gradle (code $LASTEXITCODE)." }
    Write-Ok 'APK compile'
}

# ------------------------------------------------------------------ 3. Artefact
Write-Step "Recuperation de l'artefact"

$outputDir = Join-Path $RepoRoot "composeApp/build/outputs/apk/$VariantDir"
if (-not (Test-Path $outputDir)) { Fail "Repertoire de sortie introuvable : $outputDir" }

# assembleRelease sans signingConfig produit composeApp-release-unsigned.apk : on prend le plus recent.
$builtApk = Get-ChildItem -Path $outputDir -Filter '*.apk' -File |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
if (-not $builtApk) { Fail "Aucun APK trouve dans $outputDir" }
if ($builtApk.Name -like '*unsigned*') {
    Write-Warn "$($builtApk.Name) n'est pas signe : il ne s'installera pas sur un appareil."
}

if (-not (Test-Path $ReleaseDir)) { New-Item -ItemType Directory -Path $ReleaseDir | Out-Null }
Copy-Item -Path $builtApk.FullName -Destination $TargetApk -Force
$sizeMb = [math]::Round((Get-Item $TargetApk).Length / 1MB, 2)
Write-Ok "release/ledgerhub-$Version.apk ($sizeMb Mo, source $($builtApk.Name))"

# ------------------------------------------------------------------ 4. Tag Git
Write-Step "Pose et push du tag $Tag"
git tag -a $Tag -m "LedgerHub Mobile $Version"
if ($LASTEXITCODE -ne 0) { Fail "Echec de la creation du tag $Tag." }
git push origin $Tag
if ($LASTEXITCODE -ne 0) {
    git tag -d $Tag | Out-Null
    Fail "Echec du push du tag $Tag (tag local supprime, etat restaure)."
}
Write-Ok "$Tag pousse sur origin"

# ------------------------------------------------------------------ 5. Notes
if (-not $Notes) {
    # Tag precedent = le plus recent hors celui qu'on vient de poser.
    $previousTag = git tag --sort=-creatordate | Where-Object { $_ -ne $Tag } | Select-Object -First 1
    $lines = @("LedgerHub Mobile $Version (build $BuildType)", '')
    if ($previousTag) {
        $lines += "## Changements depuis $previousTag"
        $lines += ''
        $lines += (git log "$previousTag..$Tag" --no-merges --pretty=format:'- %s')
        $lines += ''
    }
    $lines += '## Installation'
    $lines += ''
    $lines += "Telecharger l'APK ci-dessous sur l'appareil Android, puis autoriser l'installation depuis cette source."
    $Notes = $lines -join "`n"
}

# ------------------------------------------------------------------ 6. Publication
Write-Step 'Publication de la release GitHub'
$notesFile = Join-Path $env:TEMP "ledgerhub-release-$Version.md"
Set-Content -Path $notesFile -Value $Notes -Encoding utf8

$ghArgs = @('release', 'create', $Tag, $TargetApk,
            '--title', "LedgerHub Mobile $Tag",
            '--notes-file', $notesFile)
if ($Draft) { $ghArgs += '--draft' }

& gh @ghArgs
$ghExit = $LASTEXITCODE
Remove-Item $notesFile -Force -ErrorAction SilentlyContinue
if ($ghExit -ne 0) { Fail "Echec de gh release create (code $ghExit). Le tag $Tag reste pousse sur origin." }

$downloadUrl = gh release view $Tag --json assets --jq '.assets[0].url'
$releaseUrl  = gh release view $Tag --json url --jq '.url'

Write-Host ''
Write-Host '=======================================================================' -ForegroundColor Green
Write-Host " Release $Tag publiee" -ForegroundColor Green
Write-Host '=======================================================================' -ForegroundColor Green
Write-Host " Page release       : $releaseUrl"
Write-Host " Telechargement APK : $downloadUrl"
Write-Host ''
Write-Warn "Depot prive : l'URL exige une session GitHub authentifiee sur le mobile."
Write-Host ''
