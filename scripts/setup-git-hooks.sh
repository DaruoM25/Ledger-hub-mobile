#!/usr/bin/env bash
#
# scripts/setup-git-hooks.sh - Activation des hooks Git versionnés (.githooks).
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

CYAN=$'\033[36m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'

printf '\n%s==> Configuration des Git Hooks (.githooks)%s\n' "$CYAN" "$RESET"

if [ ! -f ".githooks/pre-commit" ]; then
    printf '%s[ERREUR] .githooks/pre-commit introuvable.%s\n' "$RED" "$RESET" >&2
    exit 1
fi

# Rendre exécutables les hooks versionnés
chmod +x .githooks/*
printf '    %s[OK] Droits d'\''exécution positionnés sur .githooks/*%s\n' "$GREEN" "$RESET"

# Configurer Git pour utiliser le dossier .githooks
git config core.hooksPath .githooks
printf '    %s[OK] git config core.hooksPath configuré sur .githooks%s\n' "$GREEN" "$RESET"

# Copie de sécurité dans .git/hooks/
if [ -d ".git/hooks" ]; then
    cp .githooks/pre-commit .git/hooks/pre-commit
    chmod +x .git/hooks/pre-commit
    printf '    %s[OK] Hook synchronisé dans .git/hooks/pre-commit (fallback)%s\n' "$GREEN" "$RESET"
fi

printf '\n%sProtection anti-fuite de secrets activée avec succès !%s\n\n' "$GREEN" "$RESET"
