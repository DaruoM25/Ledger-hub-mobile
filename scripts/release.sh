#!/usr/bin/env bash
#
# release.sh - Livraison reproductible d'un APK Android Ledger-hub-mobile.
# Equivalent POSIX de scripts/release.ps1, pour WSL et CI.
#
# Etapes :
#   1. Controles prealables (gh installe/authentifie, arbre Git propre, main synchronisee,
#      tag et release encore libres) ;
#   2. Compilation via ./gradlew :composeApp:assemble<BuildType> ;
#   3. Copie de l'artefact vers release/ledgerhub-<version>.apk ;
#   4. Pose et push du tag v<version> ;
#   5. gh release create avec l'APK attache et des notes generees depuis le journal Git ;
#   6. Affichage de l'URL de telechargement de l'APK.
#
# Le depot etant prive, l'URL affichee exige une session GitHub authentifiee cote navigateur.
#
# Usage :
#   ./scripts/release.sh 1.0.0-rc2
#   ./scripts/release.sh 1.0.0 --build-type Release --notes "Version stable initiale"
#   ./scripts/release.sh 1.0.0-rc3 --skip-build --draft

set -euo pipefail

VERSION=""
BUILD_TYPE="Debug"
NOTES=""
SKIP_BUILD=0
DRAFT=0

CYAN=$'\033[36m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; RED=$'\033[31m'; RESET=$'\033[0m'
step()  { printf '\n%s==> %s%s\n' "$CYAN" "$1" "$RESET"; }
ok()    { printf '    %sOK  %s%s\n' "$GREEN" "$1" "$RESET"; }
warn()  { printf '    %s/!\\ %s%s\n' "$YELLOW" "$1" "$RESET"; }
fail()  { printf '    %sERR %s%s\n' "$RED" "$1" "$RESET" >&2; exit 1; }

usage() {
  sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)       usage 0 ;;
    --build-type)    BUILD_TYPE="${2:-}"; shift 2 ;;
    --notes)         NOTES="${2:-}"; shift 2 ;;
    --skip-build)    SKIP_BUILD=1; shift ;;
    --draft)         DRAFT=1; shift ;;
    -*)              fail "Option inconnue : $1" ;;
    *)               [[ -n "$VERSION" ]] && fail "Version deja fournie : $VERSION"; VERSION="$1"; shift ;;
  esac
done

[[ -n "$VERSION" ]] || { printf '%sERR Version manquante.%s\n\n' "$RED" "$RESET" >&2; usage 1; }
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
  || fail "Version invalide : '$VERSION' (attendu : 1.0.0 ou 1.0.0-rc2)."
[[ "$BUILD_TYPE" == "Debug" || "$BUILD_TYPE" == "Release" ]] \
  || fail "BuildType invalide : '$BUILD_TYPE' (attendu : Debug ou Release)."

# Le script vit dans scripts/ : la racine du depot est son parent.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

TAG="v$VERSION"
RELEASE_DIR="$REPO_ROOT/release"
TARGET_APK="$RELEASE_DIR/ledgerhub-$VERSION.apk"
VARIANT_DIR="$(printf '%s' "$BUILD_TYPE" | tr '[:upper:]' '[:lower:]')"

# ------------------------------------------------------------------ 1. Prerequis
step "Controles prealables ($TAG, build $BUILD_TYPE)"

command -v gh >/dev/null 2>&1 || fail "GitHub CLI (gh) introuvable dans le PATH. Voir https://cli.github.com/."
gh auth status >/dev/null 2>&1 || fail "gh non authentifie. Lancer 'gh auth login' puis relancer."
ok "gh installe et authentifie"

if [[ -n "$(git status --porcelain)" ]]; then
  git status --short
  fail "Arbre de travail non propre. Commiter ou remiser les changements avant de publier."
fi
ok "Arbre de travail propre"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[[ "$BRANCH" == "main" ]] || fail "Branche courante '$BRANCH' : la release se pose depuis main."

git fetch origin main --tags --quiet
LOCAL="$(git rev-parse HEAD)"
REMOTE="$(git rev-parse origin/main)"
[[ "$LOCAL" == "$REMOTE" ]] \
  || fail "main local (${LOCAL:0:7}) desynchronise d'origin/main (${REMOTE:0:7}). Faire git pull / git push."
ok "main synchronise sur origin/main (${LOCAL:0:7})"

[[ -z "$(git tag --list "$TAG")" ]] \
  || fail "Le tag $TAG existe deja en local. Choisir une autre version ou supprimer le tag."
if gh release view "$TAG" >/dev/null 2>&1; then
  fail "Une release $TAG existe deja sur GitHub. Choisir une autre version."
fi
ok "$TAG disponible"

# ------------------------------------------------------------------ 2. Compilation
GRADLE_TASK=":composeApp:assemble$BUILD_TYPE"
if [[ "$SKIP_BUILD" -eq 1 ]]; then
  step "Compilation ignoree (--skip-build), reutilisation des sorties existantes"
else
  step "Compilation : ./gradlew $GRADLE_TASK"
  ./gradlew "$GRADLE_TASK" --no-daemon || fail "Echec de la compilation Gradle."
  ok "APK compile"
fi

# ------------------------------------------------------------------ 3. Artefact
step "Recuperation de l'artefact"

OUTPUT_DIR="$REPO_ROOT/composeApp/build/outputs/apk/$VARIANT_DIR"
[[ -d "$OUTPUT_DIR" ]] || fail "Repertoire de sortie introuvable : $OUTPUT_DIR"

# assembleRelease sans signingConfig produit composeApp-release-unsigned.apk : on prend le plus recent.
BUILT_APK="$(find "$OUTPUT_DIR" -maxdepth 1 -name '*.apk' -type f -printf '%T@ %p\n' \
             | sort -rn | head -1 | cut -d' ' -f2-)"
[[ -n "$BUILT_APK" ]] || fail "Aucun APK trouve dans $OUTPUT_DIR"
case "$BUILT_APK" in
  *unsigned*) warn "$(basename "$BUILT_APK") n'est pas signe : il ne s'installera pas sur un appareil." ;;
esac

mkdir -p "$RELEASE_DIR"
cp -f "$BUILT_APK" "$TARGET_APK"
SIZE_MB="$(awk -v b="$(wc -c < "$TARGET_APK")" 'BEGIN { printf "%.2f", b / 1048576 }')"
ok "release/ledgerhub-$VERSION.apk ($SIZE_MB Mo, source $(basename "$BUILT_APK"))"

# ------------------------------------------------------------------ 4. Tag Git
step "Pose et push du tag $TAG"
git tag -a "$TAG" -m "LedgerHub Mobile $VERSION"
if ! git push origin "$TAG"; then
  git tag -d "$TAG" >/dev/null
  fail "Echec du push du tag $TAG (tag local supprime, etat restaure)."
fi
ok "$TAG pousse sur origin"

# ------------------------------------------------------------------ 5. Notes
NOTES_FILE="$(mktemp -t ledgerhub-release-XXXXXX.md)"
trap 'rm -f "$NOTES_FILE"' EXIT

if [[ -n "$NOTES" ]]; then
  printf '%s\n' "$NOTES" > "$NOTES_FILE"
else
  # Tag precedent = le plus recent hors celui qu'on vient de poser.
  PREVIOUS_TAG="$(git tag --sort=-creatordate | grep -vxF "$TAG" | head -1 || true)"
  {
    printf 'LedgerHub Mobile %s (build %s)\n\n' "$VERSION" "$BUILD_TYPE"
    if [[ -n "$PREVIOUS_TAG" ]]; then
      printf '## Changements depuis %s\n\n' "$PREVIOUS_TAG"
      git log "$PREVIOUS_TAG..$TAG" --no-merges --pretty=format:'- %s'
      printf '\n\n'
    fi
    printf '## Installation\n\n'
    printf "Telecharger l'APK ci-dessous sur l'appareil Android, puis autoriser l'installation depuis cette source.\n"
  } > "$NOTES_FILE"
fi

# ------------------------------------------------------------------ 6. Publication
step "Publication de la release GitHub"
GH_ARGS=(release create "$TAG" "$TARGET_APK"
         --title "LedgerHub Mobile $TAG"
         --notes-file "$NOTES_FILE")
[[ "$DRAFT" -eq 1 ]] && GH_ARGS+=(--draft)

gh "${GH_ARGS[@]}" || fail "Echec de gh release create. Le tag $TAG reste pousse sur origin."

DOWNLOAD_URL="$(gh release view "$TAG" --json assets --jq '.assets[0].url')"
RELEASE_URL="$(gh release view "$TAG" --json url --jq '.url')"

printf '\n%s=======================================================================%s\n' "$GREEN" "$RESET"
printf '%s Release %s publiee%s\n' "$GREEN" "$TAG" "$RESET"
printf '%s=======================================================================%s\n' "$GREEN" "$RESET"
printf ' Page release       : %s\n' "$RELEASE_URL"
printf ' Telechargement APK : %s\n\n' "$DOWNLOAD_URL"
warn "Depot prive : l'URL exige une session GitHub authentifiee sur le mobile."
printf '\n'
