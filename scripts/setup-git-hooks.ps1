#Requires -Version 5.1
<#
.SYNOPSIS
    Configuration et activation des hooks Git versionnés (.githooks) pour LedgerHub Mobile.

.DESCRIPTION
    Configure core.hooksPath sur le dossier versionné .githooks et installe le hook pre-commit
    dans .git/hooks en solution de repli pour garantir la protection anti-fuite sur Windows.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host ""
Write-Host "==> Configuration des Git Hooks (.githooks)" -ForegroundColor Cyan

$githooksDir = Join-Path $repoRoot ".githooks"
$preCommitSrc = Join-Path $githooksDir "pre-commit"

if (-not (Test-Path $preCommitSrc)) {
    Write-Host "[ERREUR] $preCommitSrc introuvable." -ForegroundColor Red
    exit 1
}

# 1. Configuration globale du dépôt pour utiliser .githooks
git config core.hooksPath .githooks
if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] git config core.hooksPath configuré sur .githooks" -ForegroundColor Green
} else {
    Write-Host "[!] Impossible de définir core.hooksPath via git." -ForegroundColor Yellow
}

# 2. Installation en fallback dans .git/hooks/pre-commit
$gitDir = Join-Path $repoRoot ".git"
if (Test-Path $gitDir) {
    $hooksDir = Join-Path $gitDir "hooks"
    if (-not (Test-Path $hooksDir)) {
        New-Item -ItemType Directory -Path $hooksDir | Out-Null
    }
    $preCommitDest = Join-Path $hooksDir "pre-commit"
    Copy-Item -Path $preCommitSrc -Destination $preCommitDest -Force
    Write-Host "[OK] Hook copié dans .git/hooks/pre-commit (fallback actif)" -ForegroundColor Green
}

# 3. Permissions d'exécution via bash si disponible (Git Bash)
$gitBash = "C:\Program Files\Git\bin\bash.exe"
if (Test-Path $gitBash) {
    & $gitBash -c "chmod +x .githooks/pre-commit .git/hooks/pre-commit 2>/dev/null || true"
    Write-Host "[OK] Droits d'exécution positionnés (chmod +x)" -ForegroundColor Green
}

Write-Host ""
Write-Host "Protection anti-fuite de secrets activée avec succès !" -ForegroundColor Green
Write-Host ""
