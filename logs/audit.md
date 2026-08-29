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

---

## Sprint 1 — US-03 : Thème sombre premium, shell responsive, Dashboard & Formulaire (parité Web)
- **Date :** 2026-08-28
- **Statut :** ✅ Clos — suite complète verte (195/195 tests), `compileDebugKotlinAndroid` + `assembleDebug` OK sur Windows
- **Objectif :** Porter dans le codebase mobile/tablette le design, la responsivité et la logique métier de l'app Web de référence (Prompt 1) : thème sombre premium, navigation responsive, écran « Vue d'ensemble » et formulaire « Créer une Facture ».

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Extension du domaine assumée** (plutôt que champs présentation-only) : `Party.email`, `Invoice.dueDate`, `Invoice.facturX`, tous avec valeur par défaut ⇒ aucun call-site cassé, égalité `data class` préservée dans les tests. Propagé de bout en bout : domaine → DTO (`InvoiceDto`) → schéma SQLDelight (`Customer.email`, `Invoice.dueDate`, `Invoice.facturX`) → repositories (`SqlDelight{Invoice,Quote,CreditNote}Repository`, mocks). Pas de fichier de migration : pré-release, aucun `migrations/`, tests JVM sur driver en mémoire (schéma neuf), installs app neuves — édition directe des `CREATE TABLE`.
2. **Formulaire = une seule section « Informations Client »** (parité Web) : l'émetteur devient l'identité fixe du cabinet (`presentation/invoiceform/CabinetIdentity`, SIREN/SIRET valides en longueur). `InvoiceFormUiState` / `InvoiceFormField` / `InvoiceFormIntent` / `InvoiceFormViewModel` réécrits (client `name`/`siret`/`email`, `dueDate`, toggle `generateFacturX`). Le SIREN du destinataire est dérivé des 9 premiers chiffres du SIRET (règle INSEE).
3. **3ᵉ KPI = « Factures émises »** (compte, `DashboardAnalytics.issuedCount = invoices.size`) au lieu de « En retard » (montant toujours 0). `overdueRevenue` conservé à 0 (aucune logique de retard câblée).
4. **Calcul TVA/TTC inchangé** : le formulaire réutilise **exclusivement** `totalHtOf` / `totalVatOf` / `totalTtcOf` → `computeVatBreakdown` (arithmétique `Long` au centime). Aucune re-implémentation côté présentation.
5. **Colle de câblage** `data/repository/LocalLedgerRepository` : adapte le `InvoiceRepository` local (SQLDelight, écrit par le formulaire) vers le contrat `LedgerRepository` (liste/détail). Les 4 écrans partagent les mêmes repositories ⇒ « Émettre et Persister » est immédiatement visible dans « Factures » et sur le tableau de bord. `LedgerRepositoryImpl` (Ktor) reste la cible distante finale.
6. **Shell à 4 destinations** (`Vue d'ensemble`, `Factures`, `Clients`, `Paramètres`). Les flux de démo `Devis` / `Avoir` ne sont plus dans la barre de nav (l'avoir reste atteignable depuis le détail d'une facture) ; code non supprimé. Icônes = glyphes/emoji (cohérent avec l'existant, évite d'ajouter `material-icons-extended`).

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `presentation/theme/Theme.kt` | `LedgerHubTheme` : `darkColorScheme` mappé sur les tokens Tailwind (`LedgerHubColors`), appliqué à la racine (`App.kt`). `InvoiceStatusTone` + `statusColors()` : source unique des couleurs de badge (emerald/amber/slate). |
| `presentation/dashboard/RevenueChart.kt` | Graphe **ligne + aire** (remplace `RevenueBarChart.kt`) : polyligne + aire dégradée + points + repères + annotations Min/Max, 100 % Canvas natif, couleurs issues de `MaterialTheme.colorScheme`. `testTag` `REVENUE_CHART` conservé. |
| `presentation/invoiceform/CabinetIdentity.kt` | Identité fiscale fixe de l'émetteur (cabinet de l'utilisateur). |
| `data/repository/LocalLedgerRepository.kt` | Adaptateur `InvoiceRepository` (local SQLDelight) → `LedgerRepository` (liste/détail). |
| `presentation/placeholder/PlaceholderScreen.kt` | Écrans « Bientôt disponible » — `ClientsScreen`, `SettingsScreen`. |
| `androidUnitTest/.../AppShellRobolectricTest.kt` | 3 tests IHM du shell : démarrage sur « Vue d'ensemble », navigation barre du bas → « Clients », bouton « Créer une facture » → formulaire. Base SQLDelight en mémoire. |

### Fichiers modifiés (principaux)
| Fichier | Modification |
|---|---|
| `presentation/theme/Color.kt` | + tokens badges de statut (emerald/amber/slate), `Border`, `PrimaryText`, `PositiveText`, `ErrorText`. |
| `App.kt` | Réécrit : `LedgerHubTheme` + `BoxWithConstraints` (seuil 840.dp) → `NavigationBar` (Compact) / sidebar permanente + `Card` canvas central (Expanded). Overlays `CreateInvoice` / `InvoiceDetail` avec retour. Seed de démo au 1ᵉʳ lancement si base vide. ViewModels d'onglets hissés (`remember`), overlays transitoires avec `DisposableEffect { onCleared() }`. |
| `domain/invoice/{Party,Invoice}.kt`, `data/remote/dto/InvoiceDto.kt` | + `email` / `dueDate` / `facturX` (défauts). |
| `sqldelight/.../{Customer,Invoice}.sq` | + colonnes `email` / `dueDate` / `facturX` (+ `DEFAULT`), signatures `insertOrReplace` mises à jour. |
| `data/{invoice,quote,creditnote}/SqlDelight*Repository.kt` | lecture/écriture des nouvelles colonnes ; `facturX` = `INTEGER` 1/0 ↔ `Boolean`. |
| `domain/dashboard/DashboardAnalytics.kt`, `presentation/dashboard/DashboardUiState.kt` | + `issuedCount`. |
| `presentation/dashboard/DashboardScreen.kt` | Redesign parité mockup : titre + sous-titre, 3 cartes KPI (fond `surface`, bordure fine, pastille d'icône), section CA (graphe + cumul), table « Factures récentes » (colonnes Nº / CLIENT / DATE / TTC + badges tonaux). Tag `OVERDUE_CARD` → `ISSUED_CARD`. |
| `presentation/invoiceform/InvoiceFormScreen.kt` | Réécrit en 4 `SectionCard` sombres (Informations Client / Détails de la Facture / Lignes de prestation / Récapitulatif), champs `OutlinedTextField` stylés sombres, TVA en menu déroulant (`DropdownMenu`), carte toggle Factur-X (`Switch`), bouton « Émettre et Persister ». Tags stables conservés (`SCREEN`, `TOTAL_TTC`, `SUBMIT_BUTTON`, `ADD_LINE_BUTTON`, tags de ligne) ; nouveaux tags client/échéance/toggle. |
| `presentation/invoiceform/InvoicePaperCanvas.kt` | Aperçu WYSIWYG conservé, adapté : bloc émetteur = cabinet en lecture seule ; bloc destinataire = champs client. Tags `ISSUER_*`/`RECIPIENT_*` → `CABINET_*`/`CLIENT_*`. |
| Mocks (`MockInvoiceRepository`, `MockLedgerRepository`) | Données d'exemple enrichies (`email`, `dueDate`). |

### Tests
| Fichier | Évolution |
|---|---|
| `domain/dashboard/DashboardAnalyticsTest.kt` | + `issuedCount` (base vide = 0, tous statuts confondus). |
| `presentation/dashboard/DashboardViewModelTest.kt` | assertion `issuedCount == 8` (jeu du mock) ajoutée. |
| `androidUnitTest/.../dashboard/DashboardScreenRobolectricTest.kt` | `OVERDUE_CARD` → `ISSUED_CARD` + `performScrollTo()` (cartes désormais empilées). |
| `presentation/invoiceform/InvoiceFormViewModelTest.kt` | Réécrit : en-tête client (au lieu d'émetteur+destinataire), + SIRET 14 chiffres, + email invalide, + échéance requise, + toggle Factur-X porté sur `submittedInvoice.facturX`, + émetteur = `CabinetIdentity`. Couverture existante conservée (cycle Idle→Loading→Success/Error, multi-lignes, agrégation TVA au centime `20110`). |
| `presentation/invoiceform/InvoiceFormScreenTest.kt` (commonTest) + Robolectric | `ISSUER_SIREN` → `CLIENT_SIRET` ; reste inchangé (aperçu WYSIWYG, ajout/suppression de ligne, totaux). |
| `androidUnitTest/.../AppShellRobolectricTest.kt` | nouveau (3 tests). |
- **Résultat global :** `cmd /c gradlew.bat :composeApp:testDebugUnitTest` → **195/195 tests verts** (188 → 195), `compileDebugKotlinAndroid` + `assembleDebug` OK.
- **iOS :** `compileKotlinIosSimulatorArm64` **SKIPPED** sur hôte Windows (tâche Kotlin/Native non exécutable hors macOS) — validation déléguée au runner macOS de la CI (`mobile-ci.yml`, inchangé).
- **Contrôle KMP :** zéro import `android.*` / `Context` / `androidx.lifecycle` / `androidx.compose.material.*` dans `commonMain`.

### Matrice RCA — incidents rencontrés en cours d'implémentation
| Champ | Détail |
|---|---|
| **Symptôme 1** | `Syntax error: Expecting an expression` dans `RevenueChart.kt` |
| **Cause racine** | Une substitution d'édition a inséré `${…}` **à l'intérieur** d'une interpolation `${…}` existante (`"Min ${${formatCentsGrouped(min)} €}"`) |
| **Correctif** | Interpolation aplatie : `"Min ${formatCentsGrouped(min)} €"` |
| **Détection** | Échec de `compileDebugKotlinAndroid` avant tout run de tests |
| **Impact** | Aucun — corrigé avant commit |

### Points d'attention transmis
- **Colonnes SQLDelight ajoutées sans migration** : acceptable en pré-release uniquement. Dès la 1ʳᵉ version distribuée, toute évolution de schéma exigera un fichier de migration versionné.
- **`LocalLedgerRepository`** est une colle temporaire : la cible reste `LedgerRepositoryImpl` (Ktor) quand le backend local sera lancé. Le `healthCheck` local renvoie toujours succès.
- **`InvoiceListScreen` / `InvoiceDetailScreen`** héritent automatiquement du thème sombre global ; leur palette de pastilles de statut (`InvoiceStatusUi.tagColor()`) reste celle livrée en US-02 — homogénéisation avec `statusColors()` hors périmètre US-03.
- **Cycle de vie des ViewModels d'onglets** : conservés en `remember` au niveau `App` (pas de `onCleared` au changement d'onglet, comme le câblage précédent) ; les overlays formulaire/détail, eux, sont bien libérés (`DisposableEffect`).
- **Seuil responsive** : 840.dp (Material 3 « Expanded »). En-deçà → barre de navigation en bas ; au-delà → sidebar permanente + canvas central.

---

## Sprint 2 — US-02 : Support multilingue dynamique (FR/EN) & règles de formatage
- **Date :** 2026-08-28
- **Statut :** ✅ Clos — 208/208 tests unitaires verts, **`connectedDebugAndroidTest` vert sur l'émulateur** (`Pixel_5_API_35`, 1/1, 0 échec)
- **Objectif :** Porter dans le codebase KMP le dictionnaire bilingue, les règles de date et de devise validées sur le Web (Prompt 2), avec bascule de langue instantanée et réactive.

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Devise EN = `€1,234.56`** (symbole € en tête, virgule milliers, point décimal) — devise EUR conservée dans les deux langues, seule la présentation change. Le prompt mentionnait aussi `$1,234.56` (task 4) : arbitrage en faveur de la règle explicite de task 3.
2. **Date EN = `Jun 24, 2026`** (`MMM d, yyyy`, mois abrégés au dictionnaire). FR reste `24/06/2026`.
3. **Erreurs de validation par clé** : `InvoiceFormViewModel` émet des `ValidationErrorKey` (enum, indépendant de la langue), résolus côté UI via `LocalAppLanguage`. Un message déjà affiché change de langue sans re-validation.
4. **Couverture** : shell (nav/header/sidebar), Dashboard, Formulaire de facture, Liste/Détail (dates + montants + filtres/statuts), écrans placeholder. **Hors périmètre, restent en français** : l'aperçu WYSIWYG `InvoicePaperCanvas` (tests texte-exact, absent du Web), `LoginScreen` (avant navigation, thème imbriqué propre), et le corps prosaïque du détail facture (émetteur/ventilation/boutons d'action).
5. **`compositionLocalOf`** (et non `staticCompositionLocalOf`) pour `LocalAppLanguage` : seuls les composables lecteurs recomposent à la bascule.

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `domain/i18n/AppLanguage.kt` | `enum AppLanguage(FR, EN)` (code ISO, drapeau, libellé court) + `toggled()`. Pur Kotlin. |
| `domain/i18n/StringKey.kt` | Clés du dictionnaire (une par chaîne UI), regroupées par zone (NAV, DASHBOARD, FORM, TOAST, LIST, STATUS, VALIDATION, MONTH_ABBR…). |
| `domain/i18n/AppTranslations.kt` | Dictionnaire `Map<StringKey,String>` FR + EN, `get(key, language)`. Invariant testé : `FR.keys == EN.keys == StringKey.entries`. |
| `domain/i18n/ValidationErrorKey.kt` | Erreurs de validation indépendantes de la langue → `StringKey`. |
| `presentation/i18n/LocalAppLanguage.kt` | `val LocalAppLanguage = compositionLocalOf { AppLanguage.FR }` + `@Composable fun tr(key)`. |
| `presentation/i18n/DateFormat.kt` | `formatIsoDate(iso, language)` — parsing par découpage de chaîne, aucune dépendance `kotlinx-datetime`. |
| `presentation/components/LangToggle.kt` | Sélecteur segmenté `🇫🇷 FR` \| `🇬🇧 EN` (tokens slate-950), `LangToggleTags.{ROOT,FR,EN}`. |
| `androidInstrumentedTest/.../LanguageUiTest.kt` | Test instrumenté : démarrage FR (« Vue d'ensemble » + `1 234,56 €`) → clic segment EN → bascule instantanée (« Dashboard » + `€1,234.56`) → aller-retour FR. Base SQLDelight en mémoire (`AndroidSqliteDriver(name=null)`), pré-semée d'une facture PAID TVA exonérée à 123 456 cts. |
| `commonTest/domain/i18n/AppTranslationsTest.kt`, `commonTest/presentation/i18n/DateFormatTest.kt` | complétude dictionnaire, valeurs sentinelles, formats de date FR/EN + entrée malformée. |

### Fichiers modifiés (principaux)
| Fichier | Modification |
|---|---|
| `presentation/invoices/MoneyFormat.kt` | `+ formatMoney(cents, language)` / `Money.format(language)` (FR `1 234,56 €` / EN `€1,234.56`, arithmétique `Long` stricte). `formatEuros()` conservé = `format(FR)` ; `formatCentsGrouped` conservé pour `InvoicePaperCanvas`. |
| `App.kt` | `var language` hissé + `CompositionLocalProvider(LocalAppLanguage provides language)` sous `LedgerHubTheme`. Nouveau `LedgerHeader` (nom app + `LangToggle`) en `topBar` du `Scaffold` mobile ; `LangToggle` aussi dans `LedgerSidebar` (tablette). `Destination.label` → `titleKey: StringKey`. Toutes les chaînes FR en dur → `tr(...)`. |
| `presentation/invoiceform/{InvoiceFormUiState,InvoiceLineFormState}.kt` | `errors: Map<…, String>` → `Map<…, ValidationErrorKey>`. |
| `presentation/invoiceform/InvoiceFormViewModel.kt` | `revalidate` / `validateLine` émettent des `ValidationErrorKey` (mêmes conditions : SIRET 14 chiffres via `FiscalValidation`, email regex, dates ISO). |
| `presentation/invoiceform/InvoiceFormScreen.kt` | Tous les libellés via `tr()` ; totaux `.format(lang)` ; erreurs `tr(errorKey.stringKey)` ; bannières `tr(TOAST_*)`. |
| `presentation/dashboard/{DashboardScreen,RevenueChart}.kt` | Titres/KPI/colonnes/« Min/Max » via `tr()` ; montants `formatMoney(…, lang)` ; dates des lignes récentes `formatIsoDate` ; statuts via `statusKey()` (helpers `invoicedetail.label`/`quotes.label` retirés du Dashboard). |
| `presentation/invoices/{InvoiceListScreen,InvoiceStatusUi,InvoiceDetailScreen}.kt` + `components/InvoiceCard.kt` | `+ InvoiceStatus.labelKey()` / `InvoiceStatusFilter.labelKey()` ; `StatusTag` / `FacturXBadge` via `tr()` ; montants `.format(lang)` ; `issueDate` via `formatIsoDate` ; états liste (chargement/vide/réessai) + erreur réseau via `tr()`. |
| `presentation/placeholder/PlaceholderScreen.kt` | titres + « Bientôt disponible » via `tr()`. |
| `composeApp/build.gradle.kts` | `defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` ; nouveau source set `androidInstrumentedTest` (`kotlin("test")`, `coroutines-test`, `compose.uiTest`, `androidx.test:runner/core`, `androidx.test.ext:junit`, driver SQLDelight Android) ; bloc `dependencies { debugImplementation(libs.compose.ui.test.manifest) }`. |
| `gradle/libs.versions.toml` | `androidx-test-{runner,core,junit}`, `compose-ui-test-manifest` (1.7.6). |

### Tests
- **Unitaires** : `cmd /c gradlew.bat :composeApp:testDebugUnitTest --console=plain` → **208/208 verts** (195 → 208 : +3 dictionnaire, +4 date, +6 devise EN). `--rerun-tasks` inclus. Aucune régression : les assertions d'erreur de `InvoiceFormViewModelTest` portaient sur `assertNotNull/assertNull(errors[field])`, insensibles au changement `String` → `ValidationErrorKey`.
- **Instrumentés (émulateur)** : `cmd /c gradlew.bat :composeApp:connectedDebugAndroidTest --console=plain` →
  ```
  > Task :composeApp:connectedDebugAndroidTest
  Starting 1 tests on Pixel_5_API_35(AVD) - 15
  Pixel_5_API_35(AVD) - 15 Tests 1/1 completed. (0 skipped) (0 failed)
  Finished 1 tests on Pixel_5_API_35(AVD) - 15
  BUILD SUCCESSFUL
  ```
  Rapport JUnit : `testsuite tests="1" failures="0" errors="0" skipped="0"` — `languageToggle_switchesEveryUiStringAndCurrencyFormat_instantly` (7,6 s).
- **Contrôle KMP** : `AppLanguage` / `AppTranslations` dans `domain/` sans import Compose ; zéro `android.*` / `androidx.lifecycle` dans `commonMain`.

### Matrice RCA — incidents rencontrés
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | `MoneyFormatTest > nonBreakingSpaceConstant_isCodePointA0` échoue | La réécriture de `MoneyFormat.kt` a remplacé l'espace insécable U+00A0 de `NON_BREAKING_SPACE` par un espace normal U+0020 | Restauration du caractère U+00A0 dans le littéral |
| 2 | `build.gradle.kts` : `Unresolved reference: uiTestManifest` | `compose.uiTestManifest` n'est pas exposé par le DSL Compose Multiplatform 1.7.3 | Coordonnée AndroidX brute `androidx.compose.ui:ui-test-manifest:1.7.6` en `debugImplementation` |
| 3 | `androidInstrumentedTest` : `Unresolved reference 'Test' / 'assertTrue'` | `kotlin("test")` non hérité par le source set instrumenté | Ajout explicite de `kotlin("test")` + `coroutines-test` à `androidInstrumentedTest.dependencies` |
| 4 | `LanguageUiTest` : `ComposeTimeoutException` après clic EN (5 s) | Clic ciblé par **texte** (`onAllNodesWithText("EN")[0]`) — nœud sémantique ambigu du sélecteur | Clic ciblé par **testTag** (`onNodeWithTag(LangToggleTags.EN)`) |

### Points d'attention transmis
- **Dictionnaire complet garanti par test** (`AppTranslationsTest`) : toute nouvelle `StringKey` sans traduction FR **et** EN fait échouer la suite.
- **`compositionLocalOf`** : la bascule recompose tout sous-arbre lisant la langue ; coût acceptable (action rare, déclenchée manuellement).
- **Zones restées FR** (aperçu WYSIWYG, Login, prose du détail facture) : à traiter dans un lot i18n de finition si le besoin est confirmé.
- **`connectedDebugAndroidTest`** dépend d'un émulateur en cours d'exécution sur l'hôte ; non intégré à la CI (`mobile-ci.yml` couvre l'unitaire + build APK + iOS macOS).

---

## Correctifs de recette — Anomalies D-01 à D-05 (recette Android du 29/08/2026)
- **Date :** 2026-08-29
- **Statut :** ✅ Clos — 217/217 tests unitaires verts (+9), **contre-visite émulateur intégralement conforme** (`Pixel_5_API_35`, API 35), 0 crash / 0 exception applicative
- **Objectif :** Traiter les 4 écarts et la réserve relevés par la recette manuelle du 29/08/2026 sur le build `b980c6b` (13/17 cas conformes), puis revalider chaque cas directement sur l'émulateur.

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Gel de l'identité du destinataire = nom + email** (et non le nom seul). Le SIRET est déjà porté par la facture et le SIREN s'en déduit (règle INSEE, préfixe 9 chiffres) : l'identité affichée du destinataire devient donc intégralement immuable après émission, ce qui couvre la mention Factur-X 2026 de l'e-mail d'acheminement. Option « snapshot JSON » écartée : sérialisation dans la couche data et requêtes SQL moins lisibles pour un gain hypothétique.
2. **Colonnes `NOT NULL DEFAULT ''`, sans fichier de migration `.sqm`.** Le projet n'a aucune migration ni version diffusée ; la procédure de recette désinstalle déjà l'application à froid. Les factures « héritées » (colonnes vides) restent lues correctement via un repli sur la fiche `Customer`, comportement couvert par test.
3. **`INSERT OR IGNORE` plutôt que `ON CONFLICT … DO NOTHING`.** Le dialecte SQLDelight du projet est `sqlite_3_18`, antérieur à la syntaxe UPSERT (SQLite 3.24) — échec de génération constaté puis corrigé. Sémantique identique sur un conflit de clé primaire.
4. **La validation reste intégrale, seule sa présentation devient progressive.** `errors` continue d'être calculé sur tous les champs à chaque frappe (donc `isSubmitEnabled` reste correct sur formulaire vierge) ; l'écran consomme `visibleErrors`, filtré par les champs touchés puis débloqué en totalité à la première tentative d'émission.
5. **Filtres de saisie ≠ validation.** Les filtres normalisent la frappe (chiffres seuls, longueur maximale, séparateur décimal unique) ; `FiscalValidation` et `parseAmountToCents` restent seuls juges de la conformité.

### Fichiers modifiés
| Fichier | Nature |
|---|---|
| `App.kt` | D-01 : `seedDemoDataIfEmpty()` retourne `Boolean` ; le `LaunchedEffect` redéclenche `DashboardIntent.LoadDashboard` + `InvoiceListIntent.Retry` quand le semis a effectivement eu lieu. Imports `DashboardIntent` / `InvoiceListIntent` ajoutés (suppression de deux appels pleinement qualifiés). |
| `sqldelight/…/Invoice.sq` | D-03 : colonnes `recipientName` / `recipientEmail` (`NOT NULL DEFAULT ''`) + `insertOrReplace` étendu. |
| `sqldelight/…/Customer.sq` | D-03 : `insertOrReplace` → `insertIfAbsent` (`INSERT OR IGNORE`) ; ajout de `updateIdentity` pour une mise à jour délibérée de fiche client. |
| `data/invoice/SqlDelightInvoiceRepository.kt` | D-03 : écriture de la copie gelée à l'émission ; `toDomain()` privilégie la copie gelée et ne retombe sur `Customer` que pour les factures héritées. |
| `data/quote/SqlDelightQuoteRepository.kt`, `data/creditnote/SqlDelightCreditNoteRepository.kt` | D-03 : alignement sur `insertIfAbsent` — l'émission d'un devis ou d'un avoir ne réécrit plus l'identité d'un client connu. |
| `presentation/invoiceform/InvoiceFormUiState.kt` | D-02 : `touchedFields`, `submitAttempted`, propriété dérivée `visibleErrors`. |
| `presentation/invoiceform/InvoiceLineFormState.kt` | D-02 : `touched` + `visibleErrors(revealAll)`. |
| `presentation/invoiceform/InvoiceFormViewModel.kt` | D-02 : marquage des champs touchés (`touch()`), détection par comparaison des champs de ligne réellement modifiés, `submitAttempted = true` à la soumission. |
| `presentation/invoiceform/InvoiceFormScreen.kt` | D-02 : bascule de `errors` vers `visibleErrors` sur les 6 champs d'en-tête et les 3 champs de ligne. D-05 : `FormField` gagne `keyboardType` et `inputFilter` ; filtres `filterSiret` (14 chiffres), `filterQuantity`, `filterAmount` (séparateur décimal unique) ; claviers `Number` / `Decimal` / `Email`. |
| `presentation/dashboard/DashboardScreen.kt` | D-04 : `weight(1f, fill = false)` + gouttière de 12 dp sur le bloc titre, `maxLines`/`Ellipsis` sur le titre et le sous-titre, `maxLines = 1` + `softWrap = false` sur le montant. |

### Fichiers de test modifiés
| Fichier | Cas ajoutés |
|---|---|
| `androidUnitTest/…/SqlDelightInvoiceRepositoryTest.kt` | 4 cas D-03 : historique préservé lors d'une émission sous SIRET connu avec un autre nom ; e-mail gelé par facture ; fiche `Customer` jamais écrasée ; repli sur `Customer` pour une facture héritée sans copie gelée. |
| `commonTest/…/InvoiceFormViewModelTest.kt` | 5 cas D-02 : formulaire vierge calcule ses erreurs mais n'en présente aucune ; la saisie d'un champ ne révèle que son erreur ; la correction efface l'erreur visible ; la tentative d'émission révèle tout ; la saisie d'un champ de ligne ne révèle pas les autres. |

### Tests
- **Unitaires** : `./gradlew :composeApp:testDebugUnitTest --console=plain` → **217/217 verts**, 0 échec / 0 erreur (208 → 217). Aucune régression sur les suites existantes : les assertions historiques portent sur `errors` (le calcul), inchangé, et non sur `visibleErrors` (la présentation).
- **Build** : `./gradlew assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Contre-visite sur émulateur — `Pixel_5_API_35` (Android 15, API 35, google_apis x86_64, swiftshader_indirect)
Procédure : `adb uninstall com.ledgerhub.app.debug` puis `adb install -r`, donc **base de données recréée à neuf** — le premier lancement est bien un vrai premier lancement.

| Cas | Anomalie | Vérification menée | Résultat |
|---|---|---|---|
| TC-03 | D-01 | Premier lancement après installation neuve, sans redémarrage | ✅ CA `25 263,60 €`, encours `2 640,00 €`, **7 factures** affichées immédiatement |
| TC-15 | D-04 | Rendu de la carte « Chiffre d'affaires — 6 derniers mois » | ✅ Titre et montant sur une même ligne, sous-titre replié en dessous, **aucun chevauchement** |
| TC-08 | D-02 | Ouverture du formulaire de création | ✅ **Aucune bordure rouge, aucun message** sur les 6 champs d'en-tête ni sur la ligne 1 |
| TC-08 | D-02 | Saisie d'un SIRET invalide (`12`) | ✅ Erreur affichée **sur le seul champ SIRET** ; raison sociale et e-mail restent neutres |
| TC-09 | D-05 | Injection de `abc12xy` dans le SIRET | ✅ Lettres rejetées, `12` retenu, **clavier numérique** présenté |
| TC-09 | D-05 | Saisie de 20 chiffres dans le SIRET | ✅ Tronqué à **14** (`12345678901234`), surplus refusé |
| TC-09 | D-05 | Saisie de `9ab50,7x5` dans le PU HT | ✅ Normalisé en `950,75`, **clavier décimal** présenté |
| TC-10 | — | Récapitulatif sur `1 × 950,75 € HT` à 20 % | ✅ HT `950,75` / TVA `190,15` / TTC `1 140,90` |
| TC-13 | D-03 | Émission de `FAC-2026-0300` sous le SIRET connu `78410233600021`, nom « Renommee SAS », e-mail `compta@renommee.fr` | ✅ Les **7 factures historiques conservent** « Boulangerie Moreau SARL » / `compta@boulangerie-moreau.fr` ; la nouvelle porte « Renommee SAS » / `compta@renommee.fr` ; la fiche `Customer` **reste intacte**. Vérifié en base (`adb shell run-as … sqlite3`) **et** à l'écran dans la liste |
| TC-17 | — | Buffer `crash` et exceptions `AndroidRuntime` après le parcours complet | ✅ Vides — 0 crash, 0 exception |

### Matrice RCA — incidents rencontrés
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | `Customer.sq:18 ',' expected, got 'ON'` à la génération SQLDelight | Le dialecte du projet est `sqlite_3_18` ; la syntaxe UPSERT `ON CONFLICT … DO NOTHING` n'apparaît qu'en SQLite 3.24 | `INSERT OR IGNORE`, sémantiquement équivalent sur conflit de clé primaire et supporté par tous les dialectes |
| 2 | `DashboardScreen.kt:213 Unresolved reference 'SpaceBetween'` | `Arrangement.spacedBy(dp, alignment)` attend un `Alignment.Horizontal`, pas un `Arrangement.Horizontal` | Retour à `Arrangement.SpaceBetween` et gouttière portée par un `padding(end = 12.dp)` sur le bloc titre |

### Points d'attention transmis
- **Mise à jour d'une fiche client devenue impossible depuis le formulaire.** C'est l'effet recherché (l'émission ne doit plus réécrire l'historique), mais corriger la raison sociale d'un client existant n'a aujourd'hui **aucun point d'entrée** : la requête `Customer.updateIdentity` est en place et inutilisée, en attente de l'écran Clients (actuellement un placeholder). À planifier avec l'US Clients.
- **Factures héritées.** Le repli sur la fiche `Customer` couvre les lignes écrites avant le gel ; il disparaîtra naturellement quand plus aucune facture n'aura `recipientName` vide. Une reprise de données pourra figer rétroactivement l'identité si la question se pose avant la première diffusion.
- **Pas d'outillage de migration.** Le premier build diffusé devra soit repartir d'une base neuve, soit introduire `.sqm` + `verifyMigrations` — décision à prendre avant toute distribution externe.
- **Zones non couvertes par cette contre-visite** (inchangées depuis la recette du 29/08) : layout étendu ≥ 840 dp (sidebar permanente) et parcours d'avoir depuis « Annuler par un avoir ».
