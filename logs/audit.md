# Journal d'audit — LedgerHub Mobile

## Initialisation
- **Stack :** Kotlin Multiplatform (KMP), Compose Multiplatform, Ktor, SQLDelight
- **Environnement de dev :** Windows 11 / WSL2 (Cible Android locale)
- **Chaîne CI/CD :** GitHub Actions (macOS runner pour validation iOS)
- **Conformité :** Réforme fiscale Factur-X 2026

---

## Sprint 1 — US-01 : Socle Réseau Ktor, Sérialisation & Modèles Factur-X 2026
- **Date :** 2026-08-28
- **Statut :** ✅ Clos — suite complète verte (167/167 tests), compilation Windows validée

### Décision d'architecture (validée par le PO en Étape 1)
Réutilisation du modèle domaine existant (`domain/invoice/Invoice.kt`, `InvoiceLine.kt`, `Party.kt`,
`Money.kt`, `VatRate.kt`, `InvoiceStatus.kt`, `FiscalValidation.kt`) hérité des sprints précédents,
plutôt que la création d'un second modèle métier parallèle (`domain/models/`) qui aurait divergé
avec le temps — risque jugé inacceptable en contexte Factur-X (un seul lieu de vérité pour le
calcul TVA/TTC au centime). Seuls les éléments réellement nouveaux du sprint (couche réseau,
DTOs de transport, repository distant) ont été créés.

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `data/remote/HttpClientFactory.kt` (commonMain + `actual` Android/iOS) | Fabrique de `HttpClient` Ktor partagée (OkHttp/Darwin) + `authBaseUrl` et `ledgerApiBaseUrl` |
| `data/remote/dto/InvoiceDto.kt` | DTOs `@Serializable` (`InvoiceDto`, `InvoiceItemDto`, `TaxSummaryDto`) + mappers `toDomain()` vers `domain.invoice.Invoice` |
| `domain/repository/LedgerRepository.kt` | Contrat du repository distant : `fetchInvoices`, `getInvoiceDetail`, `healthCheck` |
| `data/repository/LedgerRepositoryImpl.kt` | Implémentation Ktor de `LedgerRepository`, cible `ledgerApiBaseUrl` |
| `androidMain/res/xml/network_security_config.xml` | *(déjà présent, sprint précédent)* — commentaire mis à jour pour référencer `HttpClientFactory` |

### Fichiers modifiés
| Fichier | Modification |
|---|---|
| `data/network/AuthHttpClient.kt` (commonMain + actuals) | **Supprimé** — généralisé vers `data/remote/HttpClientFactory.kt` |
| `data/auth/KtorAuthRepository.kt` | Import mis à jour (`data.network` → `data.remote`) |
| `domain/invoice/FiscalValidation.kt` | Ajout `validateEditable(invoice)` — immutabilité fiscale hors statut `DRAFT` |
| `gradle/libs.versions.toml` | Ajout `ktor-client-mock` (tests uniquement) |
| `composeApp/build.gradle.kts` | `commonTest` : ajout dépendance `ktor-client-mock` |

### Tests
- `FiscalValidationTest.kt` : +7 tests (5 `validateEditable` par statut, 2 non-régression centime-strict sur `totalTtc`)
- `LedgerRepositoryImplTest.kt` (nouveau) : 6 tests via `MockEngine` Ktor (`fetchInvoices`, `getInvoiceDetail`, `healthCheck` — succès/échec/404), aucun accès réseau réel
- **Résultat global :** `./gradlew :composeApp:testDebugUnitTest` → **167/167 tests verts**, `compileDebugKotlinAndroid` OK

### Matrice RCA — incident de compilation rencontré durant l'Étape 2
| Champ | Détail |
|---|---|
| **Symptôme** | `Syntax error: Unclosed comment` dans `HttpClientFactory.kt` (Android + iOS `actual`) |
| **Cause racine** | KDoc contenant le texte `` `/api/invoices/*` `` — Kotlin autorise les commentaires de bloc **imbriqués** ; la séquence `/*` dans le texte a ouvert un commentaire imbriqué, refermé prématurément par le `*/` de fin de ligne, laissant le `/**` englobant non fermé |
| **Détection** | Échec immédiat de `compileDebugKotlinAndroid` (Étape 2, avant tout run de tests) |
| **Correctif** | Reformulation du commentaire (`/api/invoices` sans le `/*` final) dans les deux fichiers `actual` |
| **Action préventive** | Éviter toute séquence `/*` dans un commentaire KDoc — écrire les chemins avec un `{id}` ou sans wildcard `*` |
| **Impact** | Aucun — détecté avant tout commit, aucun test n'a tourné sur du code cassé |

---

## Sprint 1 — US-02 : ViewModel, Gestion d'état & Écrans Factur-X (Liste & Détail)
- **Date :** 2026-08-28
- **Statut :** ✅ Clos — suite complète verte (188/188 tests), compilation Windows validée
- **Objectif :** Connecter l'UI Compose Multiplatform au `LedgerRepository` distant (US-01).

### Décision d'architecture (validée par le PO en Étape 1)
Création d'un package dédié `presentation/invoices/` **sans impacter l'existant**
`presentation/invoicedetail/` (module Avoir, données locales SQLDelight). Le nouveau package
consomme exclusivement l'API distante `LedgerRepository`. Convention ViewModel maison respectée
à l'identique (`CoroutineScope(SupervisorJob() + dispatcher)` + `MutableStateFlow` + `processIntent`
+ `onCleared()`), aucun `androidx.lifecycle` dans `commonMain`.

Les 4 états d'affichage (`Loading` / `Error` / `Empty` / `Success`) sont exposés par une
projection calculée `InvoiceListUiState.content` (sealed interface) dérivée des champs bruts —
le pattern data class + projections reste homogène avec `QuotesUiState`.

Le verrouillage des actions du détail **dérive exclusivement** des règles métier déjà portées
par `domain/invoice/Invoice.kt` (`isEditable`, `isCancellableByCreditNote`) — jamais recalculé
dans la couche présentation (un seul lieu de vérité fiscale).

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `presentation/invoices/MoneyFormat.kt` | `Money.formatEuros()` — format FR (espace insécable milliers, virgule décimale, `€` suffixé). Centralise les `formatCents` privés dupliqués |
| `presentation/invoices/InvoiceStatusUi.kt` | `InvoiceStatus.displayLabel()` / `.tagColor()` — helpers d'affichage du statut (noms distincts du module Avoir pour éviter les collisions d'import) |
| `presentation/invoices/InvoiceListUiState.kt` | `InvoiceListUiState` (data class + projections `visibleInvoices`, `counts`, `content`), `InvoiceStatusFilter` (filtres par statut), `InvoiceListContent` (sealed : Loading/Error/Empty/Success) |
| `presentation/invoices/InvoiceListViewModel.kt` | Chargement asynchrone via `LedgerRepository.fetchInvoices()`, intents `Load`/`Retry`/`FilterSelected`, mapping erreur réseau → message lisible |
| `presentation/invoices/InvoiceListScreen.kt` | Écran liste : `when (content)` → spinner / erreur+Réessayer / vide / `LazyColumn` de cartes ; rangée de filtres avec compteurs ; navigation déléguée au parent |
| `presentation/invoices/components/InvoiceCard.kt` | Carte facture : n°, pastille statut, destinataire, date, **badge « Conforme Factur-X 2026 »**, TTC formaté. `StatusTag` / `FacturXBadge` réutilisables |
| `presentation/invoices/InvoiceDetailUiState.kt` | État détail : `invoice`, `isLoading`, `errorMessage`, `notFound` + projections `vatBreakdown`, `canEdit`, `canCancelByCreditNote`, `isLocked` |
| `presentation/invoices/InvoiceDetailViewModel.kt` | Chargement via `LedgerRepository.getInvoiceDetail(number)` ; `null` (404) → `notFound`, erreur → `errorMessage` ; `retry()` |
| `presentation/invoices/InvoiceDetailScreen.kt` | Vue détail : en-tête + badge Factur-X, **ventilation TVA par taux (base HT / TVA)**, récap **HT / TVA / TTC**, bandeau « lecture seule » si annulée, boutons Modifier / Annuler par un avoir **désactivés selon le statut fiscal** |
| `data/repository/MockLedgerRepository.kt` | Implémentation en mémoire de `LedgerRepository` (6 factures, statuts variés) — valeur par défaut des ViewModels + jeu de `@Preview` |

### Fichiers de test créés (`composeApp/src/commonTest/`)
| Fichier | Couverture |
|---|---|
| `presentation/invoices/FakeLedgerRepository.kt` | Double de test mutable (succès↔échec entre appels) + fabrique `testInvoice(...)` |
| `presentation/invoices/InvoiceListViewModelTest.kt` | 7 tests : Loading→Success, échec→Error, liste vide→Empty, filtre par statut (sans rechargement), tri date décroissante, compteurs par statut, `Retry` après erreur |
| `presentation/invoices/InvoiceDetailViewModelTest.kt` | 7 tests : succès + ventilation TVA au centime, 404→`notFound`, échec→`errorMessage`, verrouillage DRAFT / PAID / CANCELLED, `retry()` après échec |
| `presentation/invoices/MoneyFormatTest.kt` | 7 tests : zéro, < 1 €, virgule décimale, groupement milliers/millions (U+00A0), signe négatif |

### Fichiers modifiés
| Fichier | Modification |
|---|---|
| *(aucun)* | Câblage dans `App.kt` volontairement hors périmètre US-02 (navigation traitée au démarrage du module Dashboard) — package existant `presentation/invoicedetail/` non touché |

### Tests
- **Nouveaux :** +21 tests unitaires de ViewModel/formateur (`FakeLedgerRepository`, `StandardTestDispatcher`, aucun accès réseau réel)
- **Résultat global :** `./gradlew :composeApp:testDebugUnitTest --console=plain` → **188/188 tests verts** (167 → 188), `compileDebugKotlinAndroid` OK
- **Contrôle KMP :** zéro import `android.*` / `Context` / Jetpack natif dans `commonMain` (Compose Multiplatform uniquement)

### Points d'attention transmis
- Tri des factures par `issueDate` : comparaison lexicographique, valide tant que le format ISO `YYYY-MM-DD` est garanti côté DTO (pas de `kotlinx-datetime` en v1)
- `MoneyFormat.NON_BREAKING_SPACE` : constante partagée U+00A0 référencée aussi par les tests, pour lever toute ambiguïté d'encodage du séparateur de milliers
