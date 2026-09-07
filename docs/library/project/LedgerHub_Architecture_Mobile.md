# LedgerHub Mobile — Document d'Architecture Technique
### Hackathon RevenueCat Shipaton — iOS & Android via Kotlin Multiplatform

---

## 1. Objectifs & Contraintes du projet

| Contrainte | Détail |
|---|---|
| **Cibles** | iOS + Android depuis une base de code unique |
| **Partage de code visé** | ≥ 90% (`commonMain`) |
| **Backend** | API REST existante, exposée par un cluster **Kubernetes** |
| **Monétisation** | Abonnement "Pro Mobile" via **RevenueCat** |
| **Conformité** | Réforme fiscale française **Factur-X 2026** (SIREN/SIRET, TVA, immutabilité facture) |
| **Qualité** | TDD hybride, zéro régression iOS |
| **Communication** | Contenu #BuildInPublic à chaque jalon technique |

Cette architecture est pilotée par 4 rôles internes (Skills) :
1. **Lead Dev KMP/Compose** → structure technique et code partagé
2. **QA Automatisé** → filet de sécurité (kotlin.test / compose-ui-test)
3. **Expert Fiscal Factur-X** → règles métier de facturation
4. **Tech Evangelist** → capitalisation communautaire

---

## 2. Vue d'ensemble de l'architecture

```
                    ┌─────────────────────────────┐
                    │        PRESENTATION         │  Compose Multiplatform
                    │   (Screens, ViewModels,      │  StateFlow / UDF
                    │    UiState, UDF Reducers)    │
                    └───────────────┬─────────────┘
                                    │ dépend de
                    ┌───────────────▼─────────────┐
                    │           DOMAIN            │  Kotlin pur (aucune dépendance
                    │  (UseCases, Entities,        │  plateforme, 100% testable)
                    │   Repository interfaces)     │
                    └───────────────┬─────────────┘
                                    │ implémenté par
                    ┌───────────────▼─────────────┐
                    │            DATA             │  Ktor Client + kotlinx.serialization
                    │ (DTO, Mappers, RepositoryImpl,│  SQLDelight (cache local offline)
                    │  RemoteDataSource,            │  RevenueCat wrapper (expect/actual)
                    │  LocalDataSource)             │
                    └───────────────┬─────────────┘
                                    │ HTTPS (Ktor)
                    ┌───────────────▼─────────────┐
                    │   Backend Kubernetes (API)   │
                    └──────────────────────────────┘
```

**Règle d'or (Clean Architecture stricte) :** la dépendance ne circule que vers l'intérieur.
`presentation → domain ← data`. Le module `domain` ne connaît ni Ktor, ni Compose, ni RevenueCat.

---

## 3. Arborescence des modules Gradle

```
ledgerhub-mobile/
├── gradle/
│   └── libs.versions.toml              # Version Catalog centralisé (DevOps)
├── build-logic/                        # Convention plugins (Gradle)
├── composeApp/                         # Point d'entrée multiplateforme (KMP)
│   ├── src/
│   │   ├── commonMain/kotlin/com/ledgerhub/
│   │   │   ├── App.kt                  # Root Composable + NavHost
│   │   │   └── di/                     # Injection de dépendances (Koin)
│   │   ├── androidMain/kotlin/         # MainActivity, actual implementations
│   │   └── iosMain/kotlin/             # MainViewController, actual implementations
│   └── build.gradle.kts
│
├── core/
│   ├── domain/                         # ⛔ Zéro dépendance Android/iOS
│   │   ├── model/                      # Invoice, Client, VatRate, SirenSiret...
│   │   ├── repository/                 # interfaces (contrats)
│   │   └── usecase/                    # CreateInvoiceUseCase, ValidateSirenUseCase...
│   │
│   ├── data/
│   │   ├── remote/                     # Ktor HttpClient, InvoiceApi, DTO
│   │   ├── local/                      # SQLDelight (.sq files) — cache offline
│   │   ├── mapper/                     # DTO <-> Domain <-> Entity
│   │   └── repository/                 # RepositoryImpl (implémente core/domain)
│   │
│   └── design-system/                  # Design tokens, thème Compose (Material 3)
│
├── feature/
│   ├── invoices/
│   │   ├── domain/                     # UseCases spécifiques à la feature
│   │   ├── presentation/               # ViewModel (StateFlow), UiState, Screens
│   │   └── presentation-test/          # compose-ui-test
│   ├── clients/
│   ├── dashboard/
│   └── subscription/                   # Écran RevenueCat Paywall
│
├── billing/
│   ├── revenuecat-api/                 # expect (interface commune)
│   ├── revenuecat-android/             # actual Android (Purchases SDK Android)
│   └── revenuecat-ios/                 # actual iOS (Purchases SDK iOS via Swift export)
│
├── androidApp/                         # Shell Android (Manifest, Activity, Gradle)
├── iosApp/                             # Shell iOS (Xcode project, SwiftUI wrapper léger)
│
├── .github/workflows/                  # CI/CD (Android sur Linux, iOS sur macOS runner)
├── logs/
│   └── audit.md                        # Journal d'audit (Documentation & RCA)
└── settings.gradle.kts
```

---

## 4. Couche `domain` (métier pur)

- **Entities** : `Invoice`, `InvoiceLine`, `Client`, `VatBreakdown`, `SirenSiret` (value objects avec validation intégrée).
- **UseCases** (un par action métier) :
  - `ValidateSirenUseCase` / `ValidateSiretUseCase` → régule 9 / 14 chiffres
  - `ComputeInvoiceTotalsUseCase` → calcul HT / TVA / TTC avec arrondi bancaire au centime (`RoundingMode.HALF_EVEN`)
  - `DeleteInvoiceUseCase` → refuse si `invoice.status != Draft` (immutabilité Factur-X)
  - `SubmitInvoiceUseCase` → valide avant envoi au backend
- **Repository interfaces** : `InvoiceRepository`, `ClientRepository`, `SubscriptionRepository` (aucune implémentation ici).
- **Aucune dépendance externe** hormis `kotlinx.coroutines` et `kotlinx.datetime`.

---

## 5. Couche `data`

| Composant | Techno | Rôle |
|---|---|---|
| `RemoteDataSource` | **Ktor Client** (`HttpClient` avec `ContentNegotiation` + `kotlinx.serialization`) | Appels HTTPS vers l'API Kubernetes |
| `LocalDataSource` | **SQLDelight** | Cache offline (factures en brouillon, file d'attente de sync) |
| `RepositoryImpl` | Kotlin pur | Orchestration remote/local, mapping DTO ↔ domain |
| `AuthInterceptor` | Ktor Plugin | Injection du token JWT sur chaque requête |

**Exemple de configuration Ktor (commonMain)** :
```kotlin
val httpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(Logging) { level = LogLevel.INFO }
    defaultRequest { url("https://api.ledgerhub.k8s.internal/v1/") }
}
```

---

## 6. Couche `presentation` (UDF / MVVM)

- **Compose Multiplatform** exclusivement (aucun XML Android, aucun SwiftUI pour la logique métier).
- Pattern **UDF strict** :

```
Event (UI) → ViewModel.onEvent() → UseCase → Repository
                                        │
                                        ▼
                  ViewModel.uiState (StateFlow<UiState>) → Compose recomposition
```

- Chaque écran expose :
  - `data class InvoiceListUiState(val invoices: List<InvoiceUi>, val isLoading: Boolean, val error: String?)`
  - `sealed interface InvoiceListEvent { data object Refresh, data class DeleteInvoice(val id: String) }`
- **Aucune logique métier dans les Composables** — uniquement rendu + émission d'events.
- Navigation : `Navigation Compose Multiplatform` (partagée commonMain).

---

## 7. Monétisation — RevenueCat

- Module `billing/revenuecat-api` : interface commune `expect class BillingManager`.
- `actual` Android → SDK `com.revenuecat:purchases-kmp` ou wrapper natif.
- `actual` iOS → `RevenueCat` via CocoaPods/SPM, exposé au commonMain.
- Le `SubscriptionRepository` (domain) masque RevenueCat derrière une interface métier (`hasProAccess: Boolean`, `purchasePro(): Result<Unit>`), pour ne jamais faire fuiter le SDK dans les features.

---

## 8. Conformité fiscale — Expert Factur-X

| Règle | Implémentation |
|---|---|
| SIREN = 9 chiffres | Validation Regex + clé de Luhn côté `domain` avant tout appel réseau |
| SIRET = 14 chiffres | Idem, dérivé du SIREN |
| Immutabilité facture | `DeleteInvoiceUseCase` / `EditInvoiceUseCase` lèvent une exception métier si `status ∈ {Sent, Paid, Cancelled}` |
| Arrondi TVA | Calcul en centimes (Long/Int), jamais en `Double` — utilisation de `BigDecimal`-like ou entiers pour éviter les erreurs de flottant |
| Validation avant payload | Toute requête `POST /invoices` passe par un `Validator` métier côté client **avant** sérialisation JSON |

---

## 9. Stratégie QA (TDD hybride)

| Type de test | Outil | Couvre |
|---|---|---|
| Logique métier pure | `kotlin.test` | UseCases, calculs TVA, validations SIREN/SIRET |
| UI Compose | `compose-ui-test` | États des composants (ex: bouton "Modifier" désactivé si facture "Payée") via `testTag`/`contentDescription` |
| Contrat réseau | `Ktor MockEngine` | Simule les réponses du backend K8s sans dépendance réseau réelle |

Règle : **aucune fonction réseau ou composant UI n'est "fini" sans son test associé**, écrit avant ou en parallèle du code (TDD hybride).

---

## 10. CI/CD & DevOps

- **Version Catalog** (`gradle/libs.versions.toml`) : source unique de vérité pour toutes les dépendances (Ktor, SQLDelight, Compose, RevenueCat, kotlin.test).
- **GitHub Actions** :
  - Job `android-build-test` → runner `ubuntu-latest`, build + `kotlin.test` + `compose-ui-test` (Robolectric).
  - Job `ios-build-test` → runner `macos-latest`, compilation du framework `iosSimulatorArm64`, exécution des tests communs.
- **`.gitignore` strict** : aucun cache Gradle, `build/`, `.xcworkspace` dérivé, ou `DerivedData` versionné.
- **JDK 17** imposé, arguments daemon Gradle contrôlés pour éviter les fuites mémoire en CI.

---

## 11. Synchronisation avec `ledgerhub-web`

Les règles métier (validation SIREN/SIRET, immutabilité, calculs TVA) doivent rester **strictement identiques** entre le mobile (KMP `domain`) et le web, pour éviter toute divergence de conformité fiscale entre les deux plateformes.

---

## 12. Workflow de développement (rappel Orchestrateur)

Pour chaque nouvelle fonctionnalité :
1. **Plan d'action** → liste des fichiers impactés, validation PO requise.
2. **Exécution** → code modulaire typé + commandes terminal exactes.
3. **Clôture** → consignation dans `logs/audit.md` (matrice RCA).

---

*Document généré comme socle de référence — à faire évoluer à chaque sprint du Shipaton.*
