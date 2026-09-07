#Requires -Version 5.1
<#
.SYNOPSIS
    Scanner anti-fuite de secrets pour LedgerHub Mobile (PowerShell).

.DESCRIPTION
    Analyse les fichiers suivis par Git et le diff recent a la recherche de cles privees,
    tokens JWT, credentials cloud ou mots de passe codes en dur.
#>
[CmdletBinding()]
param(
    [switch]$AllHistory
)

$ErrorActionPreference = "Continue"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host ""
Write-Host "==> Execution du scan de secrets LedgerHub Mobile (PowerShell)" -ForegroundColor Cyan

# 1. Verification si Gitleaks est disponible sur la machine
$gitleaksCmd = Get-Command gitleaks -ErrorAction SilentlyContinue
if ($null -ne $gitleaksCmd) {
    Write-Host "[INFO] Gitleaks detecte - execution de l'audit Gitleaks..." -ForegroundColor Cyan
    if ($AllHistory) {
        & gitleaks detect --verbose --redact
    } else {
        & gitleaks git --verbose --redact
    }
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) {
        Write-Host "[OK] Audit Gitleaks termine : aucun secret detecte." -ForegroundColor Green
        exit 0
    } else {
        Write-Host "[ERREUR] Gitleaks a detecte des fuites de securite." -ForegroundColor Red
        exit $exitCode
    }
}

Write-Host "[INFO] Gitleaks absent - bascule sur le moteur de detection regex autonome." -ForegroundColor DarkGray

# 2. Verification des fichiers sensibles presents dans l'arborescence suivie
$forbiddenFilePatterns = @(
    '\.(jks|keystore|pem|key)$',
    '^\.env',
    'google-services\.json$',
    'local\.properties$'
)

$trackedFiles = git ls-files
$leaksFound = 0

foreach ($file in $trackedFiles) {
    foreach ($pattern in $forbiddenFilePatterns) {
        if ($file -match $pattern) {
            Write-Host "[ERREUR] Fichier sensible interdit suivi dans Git : $file" -ForegroundColor Red
            $leaksFound++
        }
    }
}

# 3. Analyse du contenu des fichiers texte
$secretPatterns = @(
    'BEGIN (RSA|EC|DSA|OPENSSH|PGP|ENCRYPTED)? ?PRIVATE KEY',
    '(AKIA|ASIA)[0-9A-Z]{16}',
    'ey[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9._-]{15,}',
    '(api_key|apikey|secret_key|client_secret|auth_token|access_token)\s*[:=]\s*["\x27][A-Za-z0-9_\-]{16,}["\x27]',
    '(password|passwd|pwd)\s*[:=]\s*["\x27][^"\x27\s]{6,}["\x27]'
)

$textExtensions = @(".kt", ".kts", ".xml", ".json", ".properties", ".gradle", ".md", ".sh", ".ps1", ".sq")

foreach ($rawFile in $trackedFiles) {
    $file = $rawFile.Trim('"')
    if (-not (Test-Path $file)) { continue }
    $ext = [System.IO.Path]::GetExtension($file).ToLower()
    if ($textExtensions -contains $ext) {
        $lines = Get-Content -Path $file -ErrorAction SilentlyContinue
        $lineNum = 1
        foreach ($line in $lines) {
            if ($file -match 'scan-secrets|pre-commit' -or $line -match '^\s*//\s*regex') {
                $lineNum++
                continue
            }
            foreach ($pattern in $secretPatterns) {
                if ($line -match $pattern) {
                    Write-Host "[ERREUR] Secret potentiel detecte dans $file (Ligne $lineNum) : $($line.Trim())" -ForegroundColor Red
                    $leaksFound++
                }
            }
            $lineNum++
        }
    }
}

Write-Host ""
if ($leaksFound -eq 0) {
    Write-Host "[OK] Scan termine : Aucun secret ni fichier sensible detecte." -ForegroundColor Green
    exit 0
} else {
    Write-Host "[ERREUR] $leaksFound probleme(s) de securite detecte(s) !" -ForegroundColor Red
    exit 1
}
