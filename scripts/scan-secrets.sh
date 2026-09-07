#!/usr/bin/env bash
#
# scripts/scan-secrets.sh - Scanner anti-fuite de secrets pour LedgerHub Mobile (Bash / POSIX).
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CYAN=$'\033[36m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'

printf '\n%s==> Exécution du scan de secrets LedgerHub Mobile (Bash)%s\n' "$CYAN" "$RESET"

# 1. Utilisation de Gitleaks si disponible
if command -v gitleaks >/dev/null 2>&1; then
    printf '    %s[INFO] Gitleaks détecté — lancement de l'\''audit gitleaks...%s\n' "$CYAN" "$RESET"
    if gitleaks git --verbose --redact; then
        printf '    %s[OK] Audit Gitleaks terminé : aucun secret détecté.%s\n\n' "$GREEN" "$RESET"
        exit 0
    else
        printf '    %s[ERREUR] Gitleaks a détecté des fuites de sécurité.%s\n\n' "$RED" "$RESET" >&2
        exit 1
    fi
fi

printf '    %s[INFO] Gitleaks non installé — utilisation du scanner regex autonome.%s\n' "$YELLOW" "$RESET"

LEAKS_COUNT=0

# 2. Contrôle des fichiers sensibles suivis dans l'index Git
FORBIDDEN_FILES=$(git ls-files | grep -iE '(\.(jks|keystore|pem|key)$|^\.env|google-services\.json|local\.properties)' || true)
if [ -n "$FORBIDDEN_FILES" ]; then
    printf '%s[ERREUR] Fichiers sensibles suivis dans le dépôt :%s\n' "$RED" "$RESET" >&2
    echo "$FORBIDDEN_FILES" >&2
    LEAKS_COUNT=$((LEAKS_COUNT + 1))
fi

# 3. Contrôle du contenu des fichiers texte
PATTERNS=(
    "BEGIN (RSA|EC|DSA|OPENSSH|PGP|ENCRYPTED)? ?PRIVATE KEY"
    "(AKIA|ASIA)[0-9A-Z]{16}"
    "ey[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9._-]{15,}"
    "(api_key|apikey|secret_key|client_secret|auth_token|access_token)[[:space:]]*[:=][[:space:]]*['\"][A-Za-z0-9_\-]{16,}['\"]"
    "(password|passwd|pwd)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{6,}['\"]"
)

# Liste des fichiers à analyser (filtrer les scripts de scan eux-mêmes pour éviter l'auto-détection)
FILES_TO_SCAN=$(git ls-files | grep -E '\.(kt|kts|xml|json|properties|gradle|md|sh|ps1|sq)$' | grep -vE '(scan-secrets|pre-commit)' || true)

for pattern in "${PATTERNS[@]}"; do
    MATCHES=$(echo "$FILES_TO_SCAN" | xargs grep -nE "$pattern" 2>/dev/null || true)
    if [ -n "$MATCHES" ]; then
        printf '%s[ERREUR] Motif de secret détecté ("%s") :%s\n' "$RED" "$pattern" "$RESET" >&2
        echo "$MATCHES" | head -n 5 >&2
        LEAKS_COUNT=$((LEAKS_COUNT + 1))
    fi
done

printf '\n'
if [ "$LEAKS_COUNT" -eq 0 ]; then
    printf '%s[OK] Scan terminé : Aucun secret ni fichier sensible détecté.%s\n\n' "$GREEN" "$RESET"
    exit 0
else
    printf '%s[ERREUR] %d problème(s) de sécurité détecté(s) !%s\n\n' "$RED" "$LEAKS_COUNT" "$RESET" >&2
    exit 1
fi
