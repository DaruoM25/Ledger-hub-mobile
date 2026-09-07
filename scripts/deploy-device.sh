#!/usr/bin/env bash
#
# deploy-device.sh - Déploiement et lancement automatisé en un clic de LedgerHub Mobile sur terminal Android.
# Équivalent POSIX de scripts/deploy-device.ps1 (WSL / macOS / Linux).
#
# Usage :
#   ./scripts/deploy-device.sh
#   ./scripts/deploy-device.sh --skip-build
#   ./scripts/deploy-device.sh --device "adb-R5CW21ZSVQH-SnTJPq._adb-tls-connect._tcp"
#

set -euo pipefail

TARGET_DEVICE=""
SKIP_BUILD=0
PACKAGE_NAME="com.ledgerhub.app.debug"
APK_RELATIVE_PATH="composeApp/build/outputs/apk/debug/composeApp-debug.apk"

CYAN=$'\033[36m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step() { printf '\n%s==> %s%s\n' "$CYAN" "$1" "$RESET"; }
ok()   { printf '    %s[OK] %s%s\n' "$GREEN" "$1" "$RESET"; }
warn() { printf '    %s[!]  %s%s\n' "$YELLOW" "$1" "$RESET"; }
fail() { printf '    %s[ERREUR] %s%s\n' "$RED" "$1" "$RESET" >&2; exit 1; }

usage() {
  cat << 'EOF'
Usage: ./scripts/deploy-device.sh [OPTIONS]

Options:
  -d, --device <ID>   Spécifie l'identifiant du terminal ADB cible.
  -s, --skip-build    Ignore la compilation Gradle (:composeApp:assembleDebug).
  -h, --help          Affiche cette aide.
EOF
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)       usage ;;
    -d|--device)     TARGET_DEVICE="${2:-}"; shift 2 ;;
    -s|--skip-build) SKIP_BUILD=1; shift ;;
    *)               fail "Option inconnue : $1" ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
APK_PATH="$REPO_ROOT/$APK_RELATIVE_PATH"

# 1. Résolution de la commande adb
if command -v adb >/dev/null 2>&1; then
  ADB="adb"
elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]]; then
  ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
else
  fail "adb introuvable dans le PATH ni dans ANDROID_HOME / ANDROID_SDK_ROOT."
fi

# 2. Détection dynamique des terminaux actifs
step "Détection des terminaux Android connectés ($ADB devices)"
RAW_DEVICES="$($ADB devices)"

# Extraire uniquement les terminaux au statut 'device'
ACTIVE_DEVICES=()
while IFS= read -r line; do
  # Format attendu : "<serial>    device"
  if [[ "$line" =~ ^([[:graph:]]+)[[:space:]]+device$ ]]; then
    ACTIVE_DEVICES+=("${BASH_REMATCH[1]}")
  fi
done <<< "$RAW_DEVICES"

if [[ ${#ACTIVE_DEVICES[@]} -eq 0 ]]; then
  printf '\n%s[ERREUR] Aucun terminal Android au statut "device" n'\''a été détecté.%s\n' "$RED" "$RESET" >&2
  printf 'Sortie de "adb devices" :\n%s\n\n' "$RAW_DEVICES" >&2
  warn "Assure-toi que l'appareil est connecté en USB ou Wi-Fi et que le débogage USB est autorisé."
  exit 1
fi

if [[ -n "$TARGET_DEVICE" ]]; then
  FOUND=0
  for dev in "${ACTIVE_DEVICES[@]}"; do
    if [[ "$dev" == "$TARGET_DEVICE" ]]; then
      FOUND=1
      break
    fi
  done
  [[ $FOUND -eq 1 ]] || fail "L'appareil spécifié '$TARGET_DEVICE' n'est pas actif. Disponibles : ${ACTIVE_DEVICES[*]}"
else
  TARGET_DEVICE="${ACTIVE_DEVICES[0]}"
fi

ok "Terminal cible sélectionné : $TARGET_DEVICE"
if [[ ${#ACTIVE_DEVICES[@]} -gt 1 ]]; then
  printf '    (Autres terminaux détectés : %s)\n' "${ACTIVE_DEVICES[*]}"
fi

# 3. Redirection réseau (Reverse TCP)
step "Configuration des redirections de ports (Reverse TCP)"
$ADB -s "$TARGET_DEVICE" reverse tcp:8080 tcp:8080 >/dev/null 2>&1 && ok "Port tcp:8080 redirigé" || warn "Échec redirection port 8080 (non bloquant)"
$ADB -s "$TARGET_DEVICE" reverse tcp:3000 tcp:3000 >/dev/null 2>&1 && ok "Port tcp:3000 redirigé" || warn "Échec redirection port 3000 (non bloquant)"

# 4. Compilation de l'APK debug
if [[ $SKIP_BUILD -eq 0 ]]; then
  step "Compilation de l'APK debug (:composeApp:assembleDebug)"
  [[ -x "./gradlew" ]] || chmod +x ./gradlew
  ./gradlew :composeApp:assembleDebug --no-daemon
  ok "Compilation réussie"
else
  step "Compilation ignorée (--skip-build)"
fi

[[ -f "$APK_PATH" ]] || fail "L'artéfact APK est introuvable : $APK_PATH"

# 5. Déploiement & Installation
step "Installation de l'APK sur $TARGET_DEVICE"
INSTALL_OUTPUT="$($ADB -s "$TARGET_DEVICE" install -r "$APK_PATH" 2>&1)" || true
if [[ "$INSTALL_OUTPUT" =~ "Success" ]]; then
  ok "Installation réussie sur $TARGET_DEVICE"
else
  printf '%s\n' "$INSTALL_OUTPUT" >&2
  fail "Échec de l'installation sur $TARGET_DEVICE"
fi

# 6. Lancement de l'application
step "Lancement automatique de l'application"
$ADB -s "$TARGET_DEVICE" shell input keyevent 82 >/dev/null 2>&1 || true

LAUNCH_OUTPUT="$($ADB -s "$TARGET_DEVICE" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 2>&1)" || true
if [[ "$LAUNCH_OUTPUT" =~ "Events injected: 1" ]]; then
  ok "Activité principale lancée avec succès."
else
  warn "Signal de lancement transmis via monkey."
fi

# 7. Bilan & Feedback
printf '\n%s================================================================================%s\n' "$GREEN" "$RESET"
printf '%s DÉPLOIEMENT TERMINÉ AVEC SUCCÈS !%s\n' "$GREEN" "$RESET"
printf '%s Application : %s%s\n' "$GREEN" "$PACKAGE_NAME" "$RESET"
printf '%s Terminal    : %s%s\n' "$GREEN" "$TARGET_DEVICE" "$RESET"
printf '%s================================================================================%s\n\n' "$GREEN" "$RESET"
