# Statut iOS — J3

**Statut : ON HOLD** (décision Product Owner, contrainte d'espace disque)

## Diagnostic

- macOS installé : **12.7.6 (Monterey)**, Intel (x86_64).
- Seules les **Command Line Tools 14.2** sont installées (`/Library/Developer/CommandLineTools`) — **Xcode.app complet absent** de `/Applications`.
- Dernière version d'Xcode compatible avec Monterey : **Xcode 14.x** (les versions 16+ exigent macOS 14/15). Disponible via [developer.apple.com/download/all](https://developer.apple.com/download/all/) (archive), pas via le Mac App Store qui ne propose que la dernière version.
- Espace disque disponible au moment du diagnostic : **42 Go** — insuffisant pour installer Xcode 14.x confortablement (~12-15 Go) en gardant une marge de sécurité saine.

## Tentative de validation "à froid"

Commande testée : `./gradlew :composeApp:linkDebugFrameworkIosX64`

Résultat : **échec**, même sans simulateur en cours d'exécution.

```
Caused by: org.jetbrains.kotlin.konan.KonanExternalToolFailure:
The /usr/bin/xcrun command returned non-zero exit code: 72.
```

**Cause** : le compilateur Kotlin/Native appelle `xcrun --sdk iphonesimulator ...` en interne pour résoudre le SDK iPhoneSimulator, quelle que soit la nature de la compilation (lien de framework simple, sans exécution). Ce SDK n'existe que dans `Xcode.app` — les Command Line Tools seules ne suffisent pas. Il n'existe **aucun contournement** : Xcode complet est une exigence dure pour toute compilation Kotlin/Native ciblant iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`), même hors exécution sur simulateur.

## Ce qui est déjà prêt et validé (ne sera pas à refaire)

- Cibles KMP iOS déclarées dans [`composeApp/build.gradle.kts`](composeApp/build.gradle.kts) (`iosX64`, `iosArm64`, `iosSimulatorArm64`, framework statique `ComposeApp`).
- Pont d'entrée iOS : [`MainViewController.kt`](composeApp/src/iosMain/kotlin/com/ledgerhub/MainViewController.kt) (`ComposeUIViewController { App() }`).
- Stub SwiftUI minimal : [`iosApp/iosApp/iOSApp.swift`](iosApp/iosApp/iOSApp.swift), [`ContentView.swift`](iosApp/iosApp/ContentView.swift).
- Le code partagé (`commonMain`) est déjà validé indirectement : le pipeline de tests **Android/Robolectric est 100 % vert** (8/8 tests : 4 `HelloViewModelTest` + 4 `HelloScreenRobolectricTest`), couvrant la même logique métier (`HelloViewModel`, `HelloUiState`) et la même UI Compose (`HelloScreenContent`) que celles ciblées par iOS — aucun import Android n'existe dans `commonMain` (règle stricte respectée), donc ce code compilera pour iOS dès qu'Xcode sera disponible.

## Reprise (quand débloqué)

1. Installer Xcode 14.x (téléchargement manuel, nécessite un identifiant Apple — hors périmètre automatisable).
2. `sudo xcode-select -s /Applications/Xcode.app` puis `xcodebuild -license accept`.
3. `./gradlew :composeApp:linkDebugFrameworkIosX64` pour la validation à froid.
4. `./gradlew :composeApp:iosX64Test` (ou `iosSimulatorArm64Test` selon l'architecture du Mac) pour exécuter `HelloViewModelTest` + `HelloScreenTest` sur simulateur iOS.
5. Ouvrir `iosApp/` dans Xcode pour valider visuellement l'écran Hello World sur simulateur.

Aucune action Gradle/Kotlin supplémentaire n'est nécessaire avant l'installation d'Xcode — le blocage est strictement environnemental.
