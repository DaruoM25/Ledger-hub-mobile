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

---

## Prompt 3 — UX du formulaire : état de soumission, actions Brouillon / Émission, dates dynamiques
- **Date :** 2026-08-29
- **Branche :** `feature/prompt-03-ux-form-dates`
- **Statut :** ✅ Clos — 226/226 tests unitaires verts (+9), APK `BUILD SUCCESSFUL`, rendu des deux actions vérifié sur émulateur
- **Objectif :** Exposer l'état de soumission au formulaire de facture, distinguer l'enregistrement en brouillon de l'émission validée, et confirmer le formatage des dates selon la locale active.

### Constat d'audit préalable (Étape 1)
1. **Le point « dates » était déjà satisfait.** `formatIsoDate` est branché sur les trois surfaces concernées — `InvoiceCard` (liste), `DashboardScreen` (documents récents) et `InvoiceDetailScreen` — toutes via `LocalAppLanguage`, avec `DateFormatTest` en garde-fou. Comportement confirmé sur émulateur lors de la recette du 29/08 (`28/07/2026` en FR, `Jul 28, 2026` en EN). **Aucune modification de code n'était nécessaire.**
2. **L'état de soumission existait déjà**, porté par `submissionStatus: SubmissionStatus` (`Idle` / `Loading` / `Success` / `Error`) et son dérivé `isFormEnabled`.
3. **`SaveDraft` et `ValidateAndIssue` n'existaient pas.** Le formulaire n'avait qu'un `Submit`, persistant toujours en `DRAFT`, et un seul bouton. La spécification en évoque deux : c'était un manque fonctionnel réel, aucune facture ne pouvant sortir de l'état Brouillon depuis le formulaire.
4. **Défaut de conception mis au jour.** Le bouton était `enabled = uiState.isSubmitEnabled`, qui exige un formulaire valide. `Submit` ne pouvant donc jamais partir sur un formulaire invalide, `submitAttempted` ne passait jamais à `true` depuis l'interface : **la révélation de toutes les erreurs livrée avec D-02 était inatteignable en pratique**. Le `enabled = !uiState.isSubmitting` demandé par la spécification corrige exactement cela.

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Date EN conservée en `MMM d, yyyy`** (`Jun 24, 2026`). La spécification suggérait `MM/DD/YYYY`, ce qui reviendrait sur la décision du Sprint 2 US-02 ; le format retenu relève du « format international » admis par le prompt et lève l'ambiguïté jour/mois sur une pièce comptable.
2. **`isSubmitting` en propriété dérivée**, `submissionStatus == Loading`, et non champ stocké. L'écran obtient l'API demandée (`uiState.isSubmitting`) sans second état à maintenir cohérent à chaque transition.
3. **Deux actions réelles** plutôt qu'un habillage du `Submit` unique : `SaveDraft` → `InvoiceStatus.DRAFT`, `ValidateAndIssue` → `InvoiceStatus.VALIDATED`. Une facture émise devient dès lors non modifiable et annulable par avoir uniquement (`Invoice.isEditable` / `isCancellableByCreditNote`).
4. **Boutons actifs tant qu'aucune écriture n'est en cours.** `isSubmitEnabled` reste calculé et testé — il exprime la validité du formulaire — mais ne pilote plus l'activation : un appui sur formulaire incomplet révèle les erreurs au lieu de laisser l'utilisateur devant un bouton grisé sans explication. `submit()` revalide en entrée, la persistance reste protégée.

### Fichiers modifiés
| Fichier | Nature |
|---|---|
| `domain/i18n/StringKey.kt` | Ajout `ACTION_SAVE_DRAFT` ; `FORM_SENDING` renommé `FORM_PROCESSING` (clé utilisée par ce seul écran). |
| `domain/i18n/AppTranslations.kt` | « Enregistrer le brouillon » / « Save draft » ; `ACTION_SUBMIT_INVOICE` reformulé en « Valider et émettre » / « Validate and issue » (les deux actions persistant désormais, « Émettre et Persister » devenait ambigu) ; « Traitement en cours… » / « Processing… ». |
| `presentation/invoiceform/InvoiceFormIntent.kt` | `Submit` remplacé par `SaveDraft` et `ValidateAndIssue`, documentées avec leur statut cible. |
| `presentation/invoiceform/InvoiceFormUiState.kt` | `isSubmitting` dérivé de `submissionStatus` ; `isFormEnabled` réexprimé en `!isSubmitting` ; `isSubmitEnabled` documenté comme indicateur de validité, non de pilotage des boutons. |
| `presentation/invoiceform/InvoiceFormViewModel.kt` | `processIntent` route les deux intentions vers `submit(targetStatus)` ; `buildInvoice` prend le statut cible ; garde-fou contre le double appui (`if (isSubmitting) return`). |
| `presentation/invoiceform/InvoiceFormScreen.kt` | Deux boutons — `OutlinedButton` brouillon (tag `SAVE_DRAFT_BUTTON`) et `Button` émission (tag `SUBMIT_BUTTON` conservé) —, tous deux `enabled = !uiState.isSubmitting` ; indicateur de progression affiné (`strokeWidth = 2.dp`) et libellé `FORM_PROCESSING`. |

### Fichiers de test modifiés
| Fichier | Cas |
|---|---|
| `commonTest/…/InvoiceFormViewModelTest.kt` | 6 cas d'état : `isSubmitting` faux au départ ; vrai pendant l'écriture puis faux au succès, pour chacune des deux actions ; retour à faux en cas d'échec ; jamais activé quand la validation rejette ; second appui ignoré pendant une écriture. 2 cas de statut : `SaveDraft` persiste en `DRAFT` et reste modifiable, `ValidateAndIssue` persiste en `VALIDATED`, verrouille la facture et l'ouvre à l'annulation par avoir. Les 6 usages de `Submit` remappés sur `ValidateAndIssue`. |
| `commonTest/…/InvoiceFormScreenTest.kt` + `androidUnitTest/…/InvoiceFormScreenRobolectricTest.kt` | `initialState_submitButtonIsDisabled` devient `initialState_bothActionsAreOfferedAndClickable` (nouvelle sémantique) ; nouveau cas `clickingIssueOnEmptyForm_revealsErrorsWithoutSubmitting`, dupliqué côté Robolectric conformément à la convention du projet — les tests UI de `commonTest` servent `iosTest` et ne s'exécutent pas sous `testDebugUnitTest`. |

### Tests
- **Unitaires** : `./gradlew :composeApp:testDebugUnitTest --console=plain` → **226/226 verts**, 0 échec / 0 erreur (217 → 226).
- **Build** : `./gradlew assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Vérification sur émulateur — `Pixel_5_API_35`
| Vérification | Résultat |
|---|---|
| Rendu des deux actions, hiérarchie visuelle (outlined / filled) | ✅ « Enregistrer le brouillon » et « Valider et émettre » |
| Les deux boutons actifs sur formulaire vierge | ✅ |
| Appui sur « Valider et émettre » avec formulaire vide | ✅ Toutes les erreurs révélées, aucune persistance — le chemin `submitAttempted` est enfin atteignable |
| Glyphe du bouton brouillon | ⚠️ puis ✅ — `🖫` (U+1F5AB) rendait un tofu, absent des polices Android ; remplacé par `💾` |
| Écriture effective en statut `VALIDATED` | ⏸️ **Non vérifiée sur device** — deux tentatives de saisie pilotée par `adb` ont dérivé sur les coordonnées de champ. Couverte par `validateAndIssue_persistsAsValidated_andLocksTheInvoice` et par la chaîne de persistance de `SqlDelightInvoiceRepositoryTest`. |

### Points d'attention transmis
- **L'indicateur de progression est en pratique invisible avec le dépôt local.** L'écriture SQLDelight est synchrone et sous-frame : l'état `Loading` ne dure pas assez pour être perçu. Le `CircularProgressIndicator` et le libellé « Traitement en cours… » ne prendront leur sens qu'une fois l'émission adossée au backend Ktor. Le garde-fou anti-double-appui du ViewModel, lui, reste utile dès maintenant.
- **`isSubmitEnabled` n'est plus consommé par l'écran.** Il reste calculé, documenté et testé comme indicateur de validité du formulaire — utile pour un futur récapitulatif ou un badge d'état, mais il n'a plus de rôle de pilotage.
- **Le libellé du bouton d'émission a changé** (« Émettre et Persister » → « Valider et émettre »). Le commentaire de `LocalLedgerRepository` cite encore l'ancien libellé ; sans impact fonctionnel.
- **Vérification device à compléter** : émission réelle en statut Validée, et affichage du badge « Validée » dans la liste et les filtres (le compteur « Validées » était à 0 sur toutes les passes de recette, faute de chemin pour produire ce statut — ce lot le rend enfin possible).

---

## US-04 — Réactivité i18n à chaud et verrouillage fiscal de l'édition
- **Date :** 2026-08-29
- **Branche :** `feature/US-04-clients-actions-settings`
- **Statut :** ✅ Clos — 237/237 tests unitaires verts (+11), APK `BUILD SUCCESSFUL`, cadenas de liste vérifié sur émulateur
- **Objectif :** Garantir par le test la réactivité du formatage monétaire et temporel à la bascule de langue, et rendre lisible l'immutabilité des factures non-brouillon dans l'interface.

### Constat d'audit préalable (Étape 1)
1. **Le formatage monétaire était déjà exactement conforme.** `formatMoney` produit `25 263,60 €` en FR (espace insécable U+00A0, virgule décimale, symbole suffixé) et `€25,263.60` en EN, en arithmétique entière `Long`. La réactivité passe par `LocalAppLanguage`, un `compositionLocalOf` : tout composable lecteur recompose à la bascule. **Rien à corriger — le point 1 relevait de la couverture de test, pas du code.**
2. **La date FR ne correspondait pas à l'exemple du prompt.** Format en place : `24/06/2026`, contre `24 juin 2026` attendu. Seules les abréviations de mois existent au dictionnaire ; la forme longue FR aurait demandé 12 clés supplémentaires.
3. **Le verrouillage d'édition était déjà correct côté logique**, absent côté affordance : `enabled = uiState.canEdit`, dérivé de `Invoice.isEditable` (vrai pour `DRAFT` seul). Une facture `VALIDATED` ou `PAID` avait donc déjà son bouton désactivé, sans cadenas ni atténuation.
4. **`InvoiceCard` n'expose aucun point d'entrée « Modifier »** — carte simplement cliquable vers le détail. Rien à désactiver.
5. **Aucun accès à l'état mutable du formulaire n'existe aujourd'hui** : `onEditClick` a une valeur par défaut `{}` et n'est câblé nulle part dans `App.kt`. L'exigence est satisfaite par absence de fonctionnalité, non par un garde-fou — distinction qui comptera quand le parcours d'édition sera ouvert.
6. **Le seul test de réactivité i18n existant, `LanguageUiTest`, est en `androidInstrumentedTest`** : il exige `connectedDebugAndroidTest` et un émulateur, donc ne tourne ni en local ni en CI. Un équivalent Robolectric apportait une couverture réelle.

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Date FR maintenue en `24/06/2026`.** Troisième divergence consécutive entre un prompt et la décision du Sprint 2 US-02 : tranchée définitivement en faveur du format numérique — compact pour des cartes de liste denses, et usage dominant sur les pièces comptables françaises.
2. **Cadenas informatif sur `InvoiceCard`** plutôt qu'un bouton « Modifier » désactivé. Inventer une action sans destination aurait été trompeur : le parcours d'édition n'existe pas. Le cadenas renseigne sur l'immutabilité avant même d'ouvrir le détail.
3. **Traduction des libellés d'action du détail.** « Modifier la facture », « Annuler par un avoir » et la bannière de lecture seule étaient codés en dur — exclus du lot i18n du Sprint 2. Les laisser aurait produit une zone mêlant textes traduits et textes figés, juste à côté des nouveaux libellés de verrouillage.
4. **`isFiscallyLocked` distinct de `isLocked`.** Le premier vise tout statut hors Brouillon et pilote l'affordance ; le second reste réservé à l'annulation et à sa bannière. La règle métier, elle, n'est jamais recalculée dans la présentation : elle reste portée par `Invoice.isEditable`.

### Fichiers modifiés
| Fichier | Nature |
|---|---|
| `domain/i18n/StringKey.kt` | Ajout `ACTION_EDIT_INVOICE`, `ACTION_CANCEL_BY_CREDIT_NOTE`, `DETAIL_CANCELLED_READ_ONLY`, `INVOICE_LOCKED_HINT`. |
| `domain/i18n/AppTranslations.kt` | Les quatre clés en FR et EN — « Facture émise — non modifiable » / « Issued invoice — locked ». |
| `presentation/invoices/InvoiceDetailUiState.kt` | Ajout `isFiscallyLocked`, dérivé de `Invoice.isEditable`. |
| `presentation/invoices/InvoiceDetailScreen.kt` | Bouton d'édition : cadenas 🔒 dans le libellé, `alpha 0.4` et `contentDescription` explicative quand la facture est verrouillée ; mention d'aide sous le bouton (tag `LOCKED_HINT`) hors cas d'annulation, qui garde sa bannière. Les trois libellés codés en dur passent par `tr()`. |
| `presentation/invoices/components/InvoiceCard.kt` | Cadenas à côté du numéro pour toute facture non modifiable, avec `contentDescription` et tag `lockTag(number)`. |

### Fichiers de test créés
| Fichier | Cas |
|---|---|
| `androidUnitTest/…/i18n/LocalizationReactivityTest.kt` | 4 cas. L'arbre n'est monté qu'une fois ; seule la valeur de `LocalAppLanguage` change. Montant reformaté `25 263,60 €` → `€25,263.60`, date `Émise le 24/06/2026` → `Issued on Jun 24, 2026`, retour au français restituant les deux, et pastille de statut retraduite « Payée » → « Paid ». Le test échoue si une valeur est figée à la première composition. |
| `androidUnitTest/…/invoices/InvoiceImmutabilityUiTest.kt` | 7 cas, à partir d'une facture **persistée puis relue** en base (SQLDelight / `JdbcSqliteDriver` en mémoire) : `VALIDATED` et `PAID` exposent `isEditable = false` et `canDelete = false`, `DRAFT` reste modifiable ; l'écran de détail d'une facture verrouillée présente un bouton désactivé, ne remonte aucun `onEditClick` au clic et affiche la mention de verrouillage ; la carte de liste porte le cadenas sur une facture émise et pas sur un brouillon. |

### Tests
- **Unitaires** : `./gradlew :composeApp:testDebugUnitTest --console=plain` → **237/237 verts**, 0 échec / 0 erreur (226 → 237).
- **Build** : `./gradlew assembleDebug --console=plain` → `BUILD SUCCESSFUL`.
- **Émulateur** `Pixel_5_API_35` : cadenas présent sur `FAC-2026-0137` (Envoyée), absent sur les deux brouillons — conforme.

### Matrice RCA — incidents rencontrés
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | `assertHasNoClickAction` échoue sur le bouton d'édition désactivé | Compose **conserve** l'action `OnClick` sur un nœud désactivé et se contente de le marquer `[Disabled]` — l'absence d'action n'est donc pas le bon critère | Assertion remplacée par un `performClick` suivi de la vérification que le callback n'a pas été invoqué : on teste l'effet, pas la structure sémantique |
| 2 | `onNodeWithTag` introuvable sur le cadenas et la pastille de statut | `InvoiceCard` est `clickable`, donc fusionne ses descendants sémantiques : un tag porté par un enfant n'est pas visible dans l'arbre fusionné | `useUnmergedTree = true` sur ces recherches |
| 3 | `LOCKED_HINT` « not displayed » | L'écran de détail défile ; la mention était hors du viewport de test | `performScrollTo()` avant l'assertion |
| 4 | Helper de test : `onNodeWith…` non résolus | Le lambda passé à `runWithLanguageSwitch` n'avait pas le receiver `ComposeUiTest` | Type du paramètre passé en `ComposeUiTest.((AppLanguage) -> Unit) -> Unit` |

### Points d'attention transmis
- **L'immutabilité côté interface reste une affordance, pas une barrière.** Aujourd'hui elle tient parce qu'aucun parcours d'édition n'existe. Le jour où `onEditClick` sera câblé, il faudra un garde-fou en amont — refus côté ViewModel ou use case — car un bouton désactivé ne protège que du clic, pas d'un chemin de navigation alternatif.
- **`Invoice.isEditable` est l'unique source de la règle.** Les trois points d'affichage (`canEdit`, `isFiscallyLocked`, cadenas de la carte) en dérivent sans la recalculer ; toute évolution du périmètre des statuts modifiables se fait dans le domaine.
- **Zones toujours en français en dur** dans le détail : « Émetteur », « Destinataire », « Ventilation TVA », « Total HT / TVA / Total TTC ». Hors périmètre de ce lot, mais la zone reste partiellement bilingue.
- **Le format de date FR est désormais arbitré** (`24/06/2026`) après trois demandes divergentes. À traiter comme acquis dans les prompts suivants.

---

# Bilan de Recette Manuelle & Automatisée — US-04

## 1. En-tête

| Champ | Valeur |
|---|---|
| **US** | US-04 — Réactivité i18n à chaud & verrouillage fiscal de l'édition |
| **Date de recette** | 2026-08-29 |
| **Cible — émulateur** | AVD `Pixel_5_API_35` |
| **Cible — OS / API** | Android 15 · API 35 · image `google_apis` x86_64 · rendu `swiftshader_indirect` |
| **Application** | `com.ledgerhub.app.debug` (`composeApp-debug.apk`) |
| **Branche** | `feature/US-04-clients-actions-settings` |
| **Commit SHA** | `d5749a2fa5de4ce0786742691ed1490e9ff873e7` (`d5749a2`) |
| **Commit parent** | `7c9424a` — Prompt 3, actions Brouillon / Émission |
| **Méthode** | Pilotage `adb` (taps, saisie, captures), `uiautomator dump` pour le rendu Compose, `sqlite3` sur la base applicative, suite Gradle pour l'automatisé |

## 2. Tableau de Recette

### 2.1 Recette manuelle sur émulateur

| Réf Cas | Intitulé / Action | Résultat Attendu | Résultat Obtenu | Verdict |
|---|---|---|---|---|
| RM-01 | Formatage monétaire FR — liste des factures | Espace insécable, virgule décimale, symbole € suffixé | `2 640,00 €`, `1 140,90 €`, `880,80 €` | **CONFORME** |
| RM-02 | Formatage monétaire EN — détail, après bascule à chaud | Symbole € préfixé, virgule milliers, point décimal | `€2,200.00` · `€440.00` · `€2,640.00` | **CONFORME** |
| RM-03 | Date FR — carte de liste | Format numérique arbitré `JJ/MM/AAAA` | `Émise le 12/07/2026` | **CONFORME** |
| RM-04 | Date EN — détail, après bascule à chaud | `MMM d, yyyy` | `Issue date : Jul 12, 2026` | **CONFORME** |
| RM-05 | Bascule FR → EN en mémoire, sans redémarrage | Montants, dates, libellés et statuts reformatés instantanément | Écran de détail intégralement recomposé au tap ; `Envoyée` → `Sent`, `Conforme Factur-X 2026` → `Factur-X 2026 compliant` | **CONFORME** |
| RM-06 | Cadenas de liste — facture Envoyée | Cadenas visible à côté du numéro | 🔒 présent sur `FAC-2026-0137` | **CONFORME** |
| RM-07 | Cadenas de liste — brouillons | Aucun cadenas | Absent sur `FAC-2026-0300` et `FAC-2026-0136` | **CONFORME** |
| RM-08 | Détail d'une facture Envoyée — bouton d'édition | Désactivé, atténué, icône de cadenas | `🔒 Modifier la facture` nettement atténué et non cliquable | **CONFORME** |
| RM-09 | Détail — mention de verrouillage | Explication du motif sous le bouton | `Facture émise — non modifiable` | **CONFORME** |
| RM-10 | Détail — action d'annulation | Reste active (seule sortie légale) | `Annuler par un avoir` actif | **CONFORME** |
| RM-11 | Nouveaux libellés en EN | Traduits, réactifs à la bascule | `Edit invoice` · `Issued invoice — locked` · `Cancel with a credit note` | **CONFORME** |
| RM-12 | Filtre de statut « Envoyées » | Restitue la seule facture Envoyée | 1 résultat, `FAC-2026-0137` | **CONFORME** |
| RM-13 | Stabilité sur le parcours complet | Aucun crash, ANR ni exception | Buffer `crash`, `FATAL`/`ANR`, exceptions applicatives : tous vides | **CONFORME** |
| RM-14 | Détail en mode EN — libellés du corps | Ensemble de l'écran traduit | `Émetteur`, `Destinataire`, `Ventilation TVA`, `Total HT`, `TVA`, `Total TTC`, `Base 20 %` restent en français | **ÉCART** (connu, hors périmètre — voir §4, A-03) |

### 2.2 Recette automatisée — `testDebugUnitTest`

| Réf Cas | Intitulé / Action | Résultat Attendu | Résultat Obtenu | Verdict |
|---|---|---|---|---|
| RA-01 | `LocalizationReactivityTest` — montant reformaté à chaud | `25 263,60 €` → `€25,263.60`, arbre monté une seule fois | Vert | **CONFORME** |
| RA-02 | `LocalizationReactivityTest` — date reformatée à chaud | `Émise le 24/06/2026` → `Issued on Jun 24, 2026` | Vert | **CONFORME** |
| RA-03 | `LocalizationReactivityTest` — retour au français | Les deux formats FR restitués | Vert | **CONFORME** |
| RA-04 | `LocalizationReactivityTest` — pastille de statut retraduite | `Payée` → `Paid` | Vert | **CONFORME** |
| RA-05 | `InvoiceImmutabilityUiTest` — facture `VALIDATED` persistée puis relue | `isEditable = false`, `canDelete = false`, `isCancellableByCreditNote = true` | Vert | **CONFORME** |
| RA-06 | `InvoiceImmutabilityUiTest` — facture `PAID` persistée puis relue | `isEditable = false`, `canDelete = false` | Vert | **CONFORME** |
| RA-07 | `InvoiceImmutabilityUiTest` — facture `DRAFT` persistée puis relue | Reste modifiable et supprimable | Vert | **CONFORME** |
| RA-08 | `InvoiceImmutabilityUiTest` — détail `VALIDATED` | Bouton désactivé, clic sans effet, mention affichée | Vert | **CONFORME** |
| RA-09 | `InvoiceImmutabilityUiTest` — détail `PAID` | Bouton désactivé, clic sans effet | Vert | **CONFORME** |
| RA-10 | `InvoiceImmutabilityUiTest` — détail `DRAFT` | Bouton actif | Vert | **CONFORME** |
| RA-11 | `InvoiceImmutabilityUiTest` — cadenas de carte | Présent sur `VALIDATED`, absent sur `DRAFT` | Vert | **CONFORME** |
| RA-12 | Suite complète de non-régression | Aucune régression sur les 35 classes de test | 237/237 verts | **CONFORME** |

### 2.3 Cas non couverts par cette recette

| Réf | Intitulé | Motif |
|---|---|---|
| NC-01 | Verrouillage d'une facture au statut `VALIDATED` **sur émulateur** | Aucune facture Validée en base (`Validées (0)`) — le statut n'est atteignable que via l'action « Valider et émettre » livrée en `7c9424a`, dont l'émission bout-en-bout sur device n'a pas abouti. Couvert par RA-05 et RA-08. |
| NC-02 | Layout étendu ≥ 840 dp (sidebar permanente) | Non exercé sur aucune passe depuis la recette du 29/08 |
| NC-03 | Parcours d'avoir complet depuis « Annuler par un avoir » | Seul le point d'entrée est observé |

## 3. Tableau de bord Métriques

| Métrique | Valeur | Delta |
|---|---|---|
| **Tests unitaires + Robolectric — total** | **237 / 237 verts** | **+11** (226 → 237) |
| Échecs / erreurs | 0 | — |
| Classes de test exécutées | 35 | +2 |
| `LocalizationReactivityTest` (Robolectric) | 4 cas | +4 (nouveau) |
| `InvoiceImmutabilityUiTest` (Robolectric) | 7 cas | +7 (nouveau) |
| **APK — `./gradlew assembleDebug`** | **`BUILD SUCCESSFUL`** | — |
| **Crash logcat (buffer `crash`)** | **0** | — |
| **ANR applicatif** | **0** | — |
| Exceptions applicatives (`FATAL` / `AndroidRuntime`) | 0 | — |
| Fichiers de production modifiés | 5 | — |
| Fichiers de test créés | 2 | — |

## 4. Fiche des anomalies & arbitrages validés

### 4.1 Arbitrages produit (validés par le PO en Étape 1)

| Réf | Sujet | Décision retenue | Justification |
|---|---|---|---|
| AR-01 | Format de date FR — `24 juin 2026` demandé vs `24/06/2026` en place | **`24/06/2026` maintenu**, arbitré définitivement | Troisième divergence consécutive entre prompt et décision Sprint 2 US-02. Format numérique compact adapté aux cartes de liste denses, usage dominant sur les pièces comptables françaises. Économise 12 clés de mois complets. |
| AR-02 | `InvoiceCard` n'a aucun bouton « Modifier » à désactiver | **Cadenas informatif**, pas de bouton inventé | Créer une action « Modifier » sur la carte l'aurait dotée d'une destination inexistante : le parcours d'édition n'est pas implémenté. Le cadenas renseigne avant l'ouverture du détail. |
| AR-03 | Libellés d'action du détail codés en dur en français | **Traduits via `tr()`** | Sans cela, la zone aurait mêlé les nouveaux libellés de verrouillage (traduits) et les anciens (figés). Le reste du corps prosaïque reste hors périmètre → **écart RM-14**. |

### 4.2 Anomalies rencontrées en cours d'implémentation

| Réf | Cause | Correctif | Preuve de non-régression |
|---|---|---|---|
| A-01 | `assertHasNoClickAction` échouait sur le bouton d'édition désactivé : **Compose conserve l'action `OnClick`** sur un nœud désactivé et se contente de le marquer `[Disabled]`. L'absence d'action n'est donc pas le bon critère de verrouillage. | Assertion remplacée par un `performClick` suivi de la vérification que le callback `onEditClick` n'a pas été invoqué — on teste l'effet, pas la structure sémantique. | RA-08 et RA-09 verts ; RM-08 confirme visuellement l'état désactivé sur device. |
| A-02 | `onNodeWithTag` ne trouvait ni le cadenas ni la pastille de statut : `InvoiceCard` est `clickable`, donc **fusionne ses descendants sémantiques** ; un tag porté par un enfant est invisible dans l'arbre fusionné. | `useUnmergedTree = true` sur ces recherches. | RA-04 et RA-11 verts ; RM-06 et RM-07 confirment le rendu réel. |
| A-03 | Mention de verrouillage rapportée « not displayed » : l'écran de détail défile, la mention était hors du viewport de test. | `performScrollTo()` avant l'assertion. | RA-08 vert ; RM-09 confirme l'affichage sur device. |
| A-04 | Helper de test : les `onNodeWith…` n'étaient pas résolus — le lambda passé à `runWithLanguageSwitch` n'exposait pas le receiver `ComposeUiTest`. | Paramètre typé `ComposeUiTest.((AppLanguage) -> Unit) -> Unit`. | RA-01 à RA-04 verts. |

### 4.3 Constats d'audit — code déjà conforme avant intervention

| Réf | Constat | Conséquence |
|---|---|---|
| C-01 | `formatMoney` produisait déjà `25 263,60 €` / `€25,263.60` en arithmétique entière `Long` | Aucune modification : le point 1 de la spécification relevait de la **couverture de test**, pas du code. |
| C-02 | `canEdit` dérivait déjà de `Invoice.isEditable` — bouton déjà désactivé hors Brouillon | Seule l'**affordance** manquait (cadenas, atténuation, explication). |
| C-03 | `onEditClick` a une valeur par défaut vide et n'est câblé nulle part dans `App.kt` | « Aucun accès à l'état mutable » est satisfait **par absence de fonctionnalité**, pas par un garde-fou — voir §5. |
| C-04 | Le seul test de réactivité i18n, `LanguageUiTest`, est en `androidInstrumentedTest` | Il exige `connectedDebugAndroidTest` et un émulateur : il ne tourne ni en local ni en CI. L'équivalent Robolectric ajouté comble ce trou. |

## 5. Conformité Factur-X & Immutabilité — verdict final

**Verdict : CONFORME**, sous une réserve d'architecture explicitée ci-dessous.

| Exigence | État | Preuve |
|---|---|---|
| Une facture émise n'est plus modifiable | ✅ | `Invoice.isEditable` restreint la modification au seul statut `DRAFT` ; vérifié sur facture **persistée puis relue en base** (RA-05, RA-06) |
| Une facture émise n'est plus supprimable | ✅ | `canDelete` faux pour `VALIDATED` et `PAID` (RA-05, RA-06) |
| Seule sortie légale : l'avoir | ✅ | `isCancellableByCreditNote` vrai, action restée active (RA-05, RM-10) |
| L'identité du destinataire est gelée à l'émission | ✅ | Acquis du lot D-03 (`recipientName` / `recipientEmail` figés sur la ligne `Invoice`, `INSERT OR IGNORE` sur `Customer`) |
| Le verrouillage est lisible par l'utilisateur | ✅ | Cadenas en liste et au détail, atténuation, mention explicative, description d'accessibilité (RM-06 à RM-09) |
| La règle n'est jamais dupliquée dans la présentation | ✅ | `canEdit`, `isFiscallyLocked` et le cadenas de carte dérivent tous de `Invoice.isEditable` |
| Mention de conformité Factur-X 2026 | ✅ | Badge présent en liste et au détail, traduit dans les deux langues (RM-05) |

**Réserve — l'immutabilité est aujourd'hui une affordance, pas une barrière.** Elle tient parce qu'aucun parcours d'édition n'existe (C-03). Le jour où `onEditClick` sera câblé, un bouton désactivé ne protégera que du clic : il faudra **un refus en amont, côté ViewModel ou use case**, pour couvrir tout chemin de navigation alternatif (deep link, restauration d'état, écran Clients). À traiter comme prérequis de l'US d'édition, et non comme une amélioration ultérieure.

**Réserve secondaire — le statut `VALIDATED` n'a pas été exercé sur device** (NC-01), faute de facture Validée en base : la couverture repose sur l'automatisé. À reprendre dès qu'une émission « Valider et émettre » aura abouti bout-en-bout sur l'émulateur.

---

## US-04 (Partie 2) — CRUD Clients & Paramètres fiscaux
- **Date :** 2026-08-29
- **Branche :** `feature/US-04-clients-actions-settings`
- **Statut :** ✅ Clos — 292/292 tests unitaires verts (+55), APK `BUILD SUCCESSFUL`, parcours CRUD et Snackbar vérifiés sur émulateur
- **Objectif :** Remplacer les placeholders Clients et Paramètres par des écrans réels connectés à SQLDelight.

### Constat d'audit préalable (Étape 1)
1. **La table `Customer` existait déjà** avec `insertIfAbsent`, `updateIdentity`, `selectAll`, `selectBySiret`, `deleteBySiret`. La requête `updateIdentity`, ajoutée avec D-03 et restée inutilisée faute d'écran, trouve enfin son point d'entrée : cette US ferme la boucle signalée à l'audit D-03.
2. **`Party` modélise déjà exactement la fiche client** (nom, SIREN, SIRET, email) — même forme que la table. Réutilisé plutôt que d'introduire un type `Client` en doublon.
3. **Aucune table de paramètres n'existait** — `TaxSettings` créée, mono-ligne (`CHECK (id = 1)`).
4. **Les taux de TVA sont figés dans l'enum de domaine `VatRate`**, en points de base, et alimentent `computeVatBreakdown`. Deux écarts avec la spécification : le code porte **cinq** taux (20 %, 10 %, 5,5 %, **2,1 %**, Exonéré) là où le prompt en cite quatre et nomme « 0 % » ce que le domaine appelle « Exonéré ».
5. **La suppression d'un client touchait l'intégrité fiscale** : `Invoice.recipientSiret` porte une clé étrangère vers `Customer(siret)` et l'application n'active pas `PRAGMA foreign_keys` — une suppression serait passée sans erreur, en laissant des références orphelines.
6. **`CabinetIdentity` était codé en dur** et servait d'émetteur à toute facture : sans branchement, l'écran Paramètres serait resté décoratif.

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Taux de référence + taux par défaut.** Les cinq taux légaux sont présentés en lecture seule ; seul le taux pré-sélectionné à la saisie d'une ligne se configure. Rendre les valeurs éditables aurait permis d'émettre des factures à taux non conforme et imposé de transformer `VatRate` en donnée persistée — refonte de `computeVatBreakdown` et de ses tests. Le taux particulier à 2,1 % est conservé : c'est un taux français réel.
2. **Suppression bloquée si des factures référencent la fiche.** Message nommant le nombre de factures concernées ; requête `Invoice.countByRecipientSiret` ajoutée. Les clients sans facture restent supprimables. Cohérent avec l'immutabilité déjà en place : on ne retire pas une pièce du dossier fiscal.
3. **Les paramètres alimentent réellement les factures.** `InvoiceFormViewModel` reçoit l'émetteur et le taux par défaut ; `CabinetIdentity` n'est plus qu'un repli tant que rien n'est enregistré. L'aperçu WYSIWYG lit le même émetteur, désormais porté par `InvoiceFormUiState.issuer`.

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `sqldelight/…/TaxSettings.sq` | Table mono-ligne + `select` / `upsert`. |
| `domain/client/ClientRepository.kt` | Contrat CRUD sur `Party`, plus `ClientInUseException` et `DuplicateClientException`. |
| `domain/settings/TaxSettings.kt` | Modèle, valeurs par défaut, `issuerParty`, contrat `TaxSettingsRepository`. |
| `data/client/SqlDelightClientRepository.kt` | CRUD SQLDelight, refus de doublon et garde-fou de suppression, tous deux en transaction. |
| `data/settings/SqlDelightTaxSettingsRepository.kt` | Chargement avec repli sur les valeurs par défaut, `upsert`. |
| `presentation/components/InputFilters.kt` | Filtres de saisie partagés — extraits du formulaire de facture où ils étaient privés. |
| `presentation/clients/ClientsUiState.kt` · `ClientsViewModel.kt` · `ClientsScreen.kt` | Écran Clients complet (liste, dialogue d'ajout/édition, confirmation de suppression). |
| `presentation/settings/TaxSettingsViewModel.kt` · `TaxSettingsScreen.kt` | Écran Paramètres fiscaux avec Snackbar. |

### Fichiers modifiés
| Fichier | Nature |
|---|---|
| `sqldelight/…/Invoice.sq` | Ajout `countByRecipientSiret` — garde-fou de suppression. |
| `domain/invoice/FiscalValidation.kt` | Ajout `validateEmail`, `validateCompanyName`, `validateVatNumber` : les deux nouveaux écrans réutilisent les règles du formulaire de facture, jamais une seconde implémentation. |
| `presentation/invoiceform/InvoiceFormViewModel.kt` | Nouveaux paramètres `issuer` et `defaultVatRate` ; `buildInvoice` lit `state.issuer` ; toute nouvelle ligne naît au taux configuré. |
| `presentation/invoiceform/InvoiceFormUiState.kt` | Ajout `issuer`. |
| `presentation/invoiceform/InvoiceFormScreen.kt` | Filtres de saisie déplacés vers `InputFilters.kt`. |
| `presentation/invoiceform/InvoicePaperCanvas.kt` | L'aperçu lit `uiState.issuer` au lieu de `CabinetIdentity`. |
| `App.kt` | Câblage des deux dépôts et ViewModels ; les paramètres sont relus à chaque changement d'onglet, ce qui propage une modification sans coupler les ViewModels entre eux. |
| `domain/i18n/StringKey.kt` · `AppTranslations.kt` | 32 clés nouvelles, FR et EN. |

### Tests
- **Unitaires** : `./gradlew :composeApp:testDebugUnitTest --console=plain` → **292/292 verts**, 0 échec / 0 erreur (237 → 292).
  - `ClientsViewModelTest` — 15 cas (dépôt en mémoire aux mêmes règles que SQLDelight)
  - `ClientsScreenRobolectricTest` — 9 cas
  - `TaxSettingsViewModelTest` — 11 cas
  - `TaxSettingsScreenRobolectricTest` — 10 cas
  - `SqlDelightClientRepositoryTest` — 7 cas
  - `SqlDelightTaxSettingsRepositoryTest` — 3 cas
- **Build** : `./gradlew assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Matrice RCA — incidents rencontrés
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | `no such table: TaxSettings` sur l'installation existante | **La dette de migration signalée à l'audit D-03 se matérialise** : `Schema.create` ne s'exécute que sur une base neuve, et le projet n'a aucun `.sqm`. Le `runCatching` du dépôt masquait l'erreur, l'écran affichait les valeurs par défaut et l'enregistrement échouait silencieusement. | Réinstallation à froid pour la recette, conformément à la procédure retenue en D-03. **La dette reste ouverte** — voir points d'attention. |
| 2 | `successMessage_isPresentedInASnackbar_thenConsumed` en échec | La consommation du message intervient **après** la fermeture du Snackbar (`showSnackbar` suspend le temps de l'affichage) : l'assertion d'immédiateté était fausse, pas le code. | Test scindé — l'affichage est vérifié à l'écran, la consommation au niveau du ViewModel. |

### Points d'attention transmis
- **La dette de migration SQLDelight est désormais avérée, plus seulement théorique.** Tout ajout de table ou de colonne casse silencieusement les installations existantes : le `runCatching` des dépôts transforme l'erreur SQL en repli sur les valeurs par défaut, sans rien signaler. Avant toute distribution — et idéalement avant le prochain changement de schéma — il faut introduire `.sqm` + `verifyMigrations`, ou faire échouer bruyamment un schéma incompatible.
- **Messages utilisateur des deux nouveaux écrans en français en dur.** « Client ajouté », « Suppression impossible : … », et les motifs rendus par `FiscalValidation`, ne passent pas par `tr()`. Le formulaire de facture, lui, utilise `ValidationErrorKey`. Incohérence assumée pour ce lot : la généraliser demande d'introduire des clés pour tous les motifs du domaine.
- **Le SIRET est verrouillé en édition** : il est la clé primaire et l'identité métier. Changer de SIRET revient à créer une autre fiche — comportement volontaire, signalé à l'utilisateur par une mention sous le champ.
- **Les puces de taux non sélectionnées manquent de contraste** en thème sombre (gris sombre sur fond sombre). Lisible mais perfectible ; à reprendre avec le Design System.
- **La suppression d'un client sans facture n'a pas été exercée sur émulateur** : le seul client supprimable est celui créé pendant la recette, et la vérification s'est arrêtée après la création. Couverte par `ClientsViewModelTest` et `SqlDelightClientRepositoryTest`.

---

## US-05 — Cycle d'annulation comptable Factur-X 2026 (Avoirs)
- **Date :** 2026-08-29
- **Branche :** `feature/US-05-credit-notes` (créée depuis `main` @ `855fc02`)
- **Statut :** ✅ Clos — 317/317 tests unitaires verts (+25), APK `BUILD SUCCESSFUL`, migration vérifiée sur base réelle, parcours d'annulation validé de bout en bout sur émulateur
- **Objectif :** Rendre atteignable et complet le cycle d'émission d'avoirs, et fermer la dette A-01.

### Constat d'audit préalable (Étape 1)
La prémisse du prompt était exacte : `main` portait bien US-03 et US-04 (`855fc02`, poussé sur `origin`).

**Une grande partie de l'US-05 existait déjà** : domaine `CreditNote` imposant les montants négatifs par `init require`, `CreateCreditNoteUseCase` refusant les factures non finalisées, table `CreditNote.sq` avec clé étrangère vers `Invoice(number)`, repository basculant la facture d'origine en `CANCELLED` **dans la même transaction** — l'inaltérabilité demandée au point 3 était donc déjà acquise. Un écran, un ViewModel et cinq suites de tests étaient en place.

**Six écarts réels** ont été relevés et traités :
1. **Parcours inatteignable** — `onCreateCreditNoteClick` avait une valeur par défaut vide et n'était câblé nulle part : le formulaire d'avoir était du code mort, exactement comme `onEditClick` signalé en US-04.
2. **Aucun blocage de second avoir** — `selectByInvoiceNumber` existait mais n'était jamais consulté, et le repository faisait `INSERT OR REPLACE`.
3. **`originalInvoiceDate` absent** — la référence croisée se limitait au numéro.
4. **Lignes et assiettes non recopiées** — seuls les trois totaux l'étaient.
5. **Numéro saisi à la main**, sans séquence ni contrôle de format.
6. **Aucune mention « Avoir émis : … »** sur la facture parente.

**Choix de modélisation** : la table `CreditNote` séparée est conservée plutôt qu'une colonne discriminante sur `Invoice`. Elle existe, porte déjà la clé étrangère, et fusionner les deux pièces imposerait de rendre nullables la moitié des colonnes d'`Invoice` et de filtrer chaque lecture existante. La spécification laissait ce choix ouvert.

### Décisions d'architecture (validées par le PO en Étape 1)
1. **Vraies migrations SQLDelight — dette A-01 close.** `schemaOutputDirectory` + `verifyMigrations` activés, schéma de référence `1.db` figé **avant** toute modification, migration `1.sqm` (v1 → v2). Le build échoue désormais si un `.sq` évolue sans son `.sqm` : le problème devient une erreur de compilation au lieu d'un repli muet à l'exécution. La migration inclut par surcroît un `CREATE TABLE IF NOT EXISTS TaxSettings` qui **rattrape les bases antérieures à l'US-04**, restées en version 1 sans cette table (incident constaté en recette US-04) — idempotent sur une base saine, donc validé tel quel par `verifyMigrations`.
2. **Table `CreditNoteLine` dédiée.** L'avoir porte sa propre copie des lignes : un document fiscal doit rester lisible sans sa pièce parente, même principe que la copie gelée du destinataire introduite en D-03. Les prix restent positifs ; c'est le sens comptable qui inverse totaux et assiettes.
3. **Numérotation séquentielle attribuée par la base.** `AV-AAAA-NNNN`, suffixe à largeur fixe — ce qui rend le tri lexicographique équivalent au tri numérique et permet à `selectLastNumberForPrefix` de se contenter d'un `ORDER BY number DESC LIMIT 1`. La continuité étant une obligation fiscale, elle ne pouvait pas dépendre d'une saisie.

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `sqldelight/databases/1.db` | Schéma de référence figé depuis `main`. |
| `sqldelight/…/1.sqm` | Migration v1 → v2 : `originalInvoiceDate`, `CreditNoteLine`, rattrapage `TaxSettings`. |
| `sqldelight/…/CreditNoteLine.sq` | Lignes recopiées de la facture annulée. |
| `domain/creditnote/CreditNoteNumbering.kt` | Format, séquence, motif `LIKE` par exercice. |
| `commonTest/…/CreditNoteNumberingTest.kt` | 8 cas. |
| `androidUnitTest/…/CreditNoteLifecycleRepositoryTest.kt` | 7 cas sur base réelle. |
| `androidUnitTest/…/CreditNoteCrossReferenceUiTest.kt` | 7 cas d'interface. |

### Fichiers modifiés (principaux)
| Fichier | Nature |
|---|---|
| `composeApp/build.gradle.kts` | `schemaOutputDirectory` + `verifyMigrations`. |
| `sqldelight/…/CreditNote.sq` | Colonne `originalInvoiceDate` **en dernière position** (voir RCA n°1), `insertOrReplace` étendu, `selectLastNumberForPrefix`. |
| `sqldelight/…/Invoice.sq` | — (inchangé sur ce lot). |
| `domain/creditnote/CreditNote.kt` | `originalInvoiceDate`, `lines`, `vatBreakdown` inversé calculé par le moteur du domaine — aucune duplication de l'arithmétique fiscale. |
| `domain/creditnote/CreditNoteRepository.kt` | `findByInvoiceNumber`, `nextNumberForYear`, `InvoiceAlreadyCreditedException`. |
| `domain/creditnote/CreateCreditNoteUseCase.kt` | Recopie des lignes, référence croisée, contrôle de format du numéro. |
| `data/creditnote/SqlDelightCreditNoteRepository.kt` | Contrôle d'unicité **dans la transaction**, écriture des lignes, numérotation. |
| `data/creditnote/MockCreditNoteRepository.kt` | Mêmes règles que l'implémentation réelle. |
| `presentation/creditnoteform/*` | Numéro attribué et présenté en lecture seule, référence croisée complète, lignes et assiettes affichées, bannière de refus, erreurs révélées progressivement (D-02). Formatage monétaire aligné sur le `formatMoney` i18n partagé — l'écran avait jusqu'ici son propre formateur, qui ignorait la locale. |
| `presentation/invoices/*` | `creditNoteNumber` dans l'état du détail et index `creditNotesByInvoice` dans celui de la liste ; mention « Avoir émis : … » sur les deux surfaces ; les deux ViewModels interrogent le dépôt d'avoirs. |
| `App.kt` | `Overlay.CreditNote`, câblage de `onCreateCreditNoteClick`, dépôt d'avoirs transmis aux ViewModels liste et détail. |
| `domain/i18n/*` | Clé `INVOICE_CREDITED_BY` (FR/EN). |

### Tests
- **Migration** : `./gradlew :composeApp:verifyCommonMainLedgerHubDatabaseMigration` → `BUILD SUCCESSFUL`.
- **Unitaires** : `./gradlew :composeApp:testDebugUnitTest --console=plain` → **317/317 verts** (292 → 317).
- **Build** : `./gradlew assembleDebug --console=plain` → `BUILD SUCCESSFUL`.

### Matrice RCA — incidents rencontrés
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | `verifyMigrations` : « fresh database looks different from migration database » | `ALTER TABLE ADD COLUMN` place la colonne **en fin de table**, alors qu'elle était déclarée au milieu du `CREATE TABLE` : l'ordre des colonnes divergeait entre base migrée et base neuve. | `originalInvoiceDate` déplacée en dernière position du `CREATE TABLE`, avec le commentaire expliquant la contrainte. |
| 2 | 12 suites d'avoirs en échec après le changement de contrat | Numéros à 3 chiffres devenus invalides (`AV-AAAA-NNNN`), et `CreditNoteNumberChanged` rendue sans effet par la numérotation automatique. | Numéros portés à 4 chiffres ; saisie du numéro retirée des tests ; `advanceUntilIdle()` ajouté après construction du ViewModel pour laisser la séquence s'attribuer. |
| 3 | `typingBlankReason_displaysFieldError` en échec | Le passage à `visibleErrors` (D-02) rend neutre un champ jamais saisi ; le test n'exerçait plus son intention. | Réécrit en `clearingTheReason_displaysFieldError` : saisie puis effacement, ce que le nom annonçait. |
| 4 | Assertions de montants en échec sur les nouveaux affichages | L'écran d'avoir utilisait un formateur local (`500.00`) ignorant la locale, là où le reste de l'app utilise `formatMoney`. | Écran aligné sur `formatMoney` ; attentes de test mises au format FR (espace insécable). |

### Recette manuelle — migration sur base existante
Point le plus important de cette recette : l'APK a été installé **par-dessus** une base US-04 en place, sans désinstallation.

| Contrôle | Avant | Après |
|---|---|---|
| `PRAGMA user_version` | 1 | **2** |
| Tables | 8 (sans `CreditNoteLine`) | 9, `CreditNoteLine` créée |
| Colonne `originalInvoiceDate` | absente | présente, position 12 |
| Factures / clients | 7 / 2 | **7 / 2 — préservés** |

### Points d'attention transmis
- **L'exercice de rattachement de l'avoir est celui de la facture annulée.** commonMain n'a pas d'horloge — le projet a écarté `kotlinx-datetime` (décision v1) et les dates transitent en chaînes ISO saisies. Le choix reste juste dans le cas courant (avoir émis dans l'année de la facture) mais devra être revu le jour où une horloge est introduite : un avoir émis en janvier 2027 sur une facture de 2026 recevrait aujourd'hui un numéro `AV-2026-…`.
- **Les bases antérieures à l'US-04 sont rattrapées par la migration**, mais ce rattrapage est un correctif ponctuel inscrit dans `1.sqm` : il n'y aura pas d'équivalent pour un futur écart, puisque `verifyMigrations` empêche désormais qu'il s'en produise.
- **La bannière de refus d'un second avoir est un filet de sécurité.** En pratique l'émission cascade la facture en `CANCELLED`, ce qui désactive déjà l'action côté interface ; la bannière et l'exception du dépôt couvrent les cas de course ou d'état incohérent. Les deux niveaux sont testés.
- **Messages du domaine et du formulaire d'avoir toujours en français en dur** (motifs de validation, libellés de section). Même limitation que les écrans Clients et Paramètres, signalée en US-04.

---

## US-06 — Moteur de génération et d'export Factur-X (CII, profil BASIC / EN 16931)
- **Date :** 2026-08-29
- **Branche :** `feature/US-06-facturx-export` (créée depuis `feature/US-05-credit-notes`)
- **Statut :** ✅ Clos — 370/370 tests unitaires verts (+53, dont **7 de validation XSD officielle**), APK `BUILD SUCCESSFUL`, export et partage vérifiés de bout en bout sur émulateur
- **Objectif :** Produire le XML Factur-X des factures et des avoirs, et le remettre à la plateforme.

### Constats d'audit préalable
1. **`feature/US-05-credit-notes` était déjà mergée dans `main`** (`bae2b9f`), diff vide entre les deux. Branchement effectué depuis la branche demandée, contenu identique.
2. **Il n'existe aucun écran de détail d'avoir** — seulement un formulaire de création. Le point 2 de la spécification n'avait pas de cible pour les avoirs.
3. **`Party` ne porte aucune adresse**, ni `Customer`, ni `TaxSettings`. Or `ram:CountryID` est obligatoire dans `ram:PostalTradeAddress` : sans lui, aucun document n'aurait été conforme.
4. **Aucune bibliothèque XML** au catalogue, **aucun `FileProvider`** au manifeste.
5. `presentation/invoicedetail` est un **second module de détail non câblé** (code mort hors tests) — à ne pas confondre avec `presentation/invoices`, le vivant.

### Décisions d'architecture (validées par le PO)
1. **Constructeur XML maison** (`XmlBuilder`, ~90 lignes) plutôt qu'une dépendance. CII est profondément imbriqué avec quatre préfixes de namespace ; un mapping par annotations aurait été plus verbeux que le XML lui-même. Cohérent avec les décisions v1 (rejet de `kotlinx-datetime`, arithmétique entière maison). Sortie déterministe, donc comparable au caractère près.
2. **`ram:CountryID` fixé à `FR`.** Arbitrage assumé : produit franco-français. Une adresse complète relève d'une US dédiée (schéma + deux formulaires + migration).
3. **Export de l'avoir depuis le détail de sa facture parente**, à l'endroit où figure déjà la mention de liaison. Aucun écran nouveau.
4. **Validation XSD incluse** — 4 fichiers officiels du profil BASIC (~20 Ko) versés dans `androidUnitTest/resources/facturx/`, depuis `ZUGFeRD/mustangproject` (Apache 2.0).
5. **`DocumentExporter` en interface injectée**, pas en `expect`/`actual`. La règle du projet réserve ce mécanisme au dernier recours ; une abstraction suffit, et elle rend l'action substituable en test sans runtime de plateforme.

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `domain/facturx/XmlBuilder.kt` | DSL XML minimal, échappement, indentation déterministe. |
| `domain/facturx/FacturXFormat.kt` | Montants `xs:decimal`, dates format 102, taux — arithmétique entière. |
| `domain/facturx/FacturXDocument.kt` | Modèle pivot neutre, codes 380/381, catégories S/E. |
| `domain/facturx/FacturXMapper.kt` | `Invoice`/`CreditNote` → pivot ; aucun recalcul de montant. |
| `domain/facturx/FacturXGenerator.kt` | Génération CII, profil BASIC. |
| `domain/export/DocumentExporter.kt` | Contrat d'export + implémentation neutre. |
| `androidMain/data/export/AndroidDocumentExporter.kt` | Écriture en cache, `FileProvider`, `ACTION_SEND`. |
| `androidMain/res/xml/file_paths.xml` | Un seul chemin exposé : `cache/exports/`. |
| `androidUnitTest/resources/facturx/*.xsd` | 4 schémas officiels du profil BASIC. |
| 5 fichiers de test | 53 cas — détail ci-dessous. |

### Fichiers modifiés
`AndroidManifest.xml` (provider) · `App.kt` (paramètre `documentExporter`, génération à la demande, propagation) · `MainActivity.kt` · `presentation/invoices/InvoiceDetailScreen.kt` (deux actions d'export) · `domain/i18n/{StringKey,AppTranslations}.kt` (4 clés FR/EN).

### Tests — 53 cas ajoutés
| Suite | Cas | Portée |
|---|---|---|
| `XmlBuilderTest` | 7 | Imbrication, ordre des attributs, échappement — dont l'injection de balise via une raison sociale. |
| `FacturXFormatTest` | 8 | Décimales, signe négatif, absence de groupement, date 102, cinq taux. |
| `FacturXGeneratorTest` | 22 | Racine et 4 namespaces, profil EN 16931, 380 vs 381, chaînage d'avoir et sa position dans la séquence, parties, lignes, assiettes par taux, catégories S/E, sommation au centime. |
| **`FacturXSchemaValidationTest`** | **7** | **Validation contre le XSD officiel** : facture, avoir, multi-taux, sans TVA intracommunautaire, avec balisage échappé — **plus deux contre-épreuves** vérifiant que le validateur rejette bien un ordre invalide et une balise obligatoire manquante. |
| `FacturXExportUiTest` | 9 | Présence et état des deux actions, absence de l'export d'avoir sans avoir, câblage des événements, contenu remis à la plateforme. |

- **Unitaires** : `./gradlew :composeApp:testDebugUnitTest` → **370/370 verts** (317 → 370).
- **Build** : `./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

### Matrice RCA
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | Arguments dupliqués dans un appel à `ShellContent` | Un remplacement scripté à deux niveaux d'indentation a frappé deux fois le même site, et manqué le second. | Déduplication et ajout manuel sur le site oublié. Détecté à la relecture, avant compilation. |

### Recette manuelle sur émulateur
| Contrôle | Résultat |
|---|---|
| Deux actions d'export sur une facture annulée portant un avoir | ✅ « Exporter la facture en XML » et « Exporter l'avoir AV-2026-0001 » |
| Génération du fichier | ✅ `cache/exports/factur-x.xml`, 4 060 o (facture) / 4 449 o (avoir) |
| Feuille de partage Android | ✅ « Sharing 1 file — factur-x.xml », Quick Share / Drive / Gmail |
| Contenu du XML de facture | ✅ Racine, 4 namespaces, profil BASIC, `380`, `20260712`, montants exacts |
| Contenu du XML d'avoir | ✅ `381`, `AV-2026-0001`, totaux négatifs, `IssuerAssignedID` = `FAC-2026-0137` daté `20260712` |
| Logcat | ✅ 0 crash, 0 `FileUriExposedException` |

### Points d'attention transmis
- **La validation XSD porte sur le générateur, pas sur l'artefact du téléphone.** Aucun validateur n'était disponible localement pour valider le fichier extrait de l'appareil. Le test XSD exerce néanmoins `FacturXGenerator.generate()` — la fonction même qui l'a produit — sur les mêmes données (`FAC-2026-0137`, `AV-2026-0001`). La couverture est équivalente, la nuance mérite d'être connue.
- **`CountryID` codé à `FR`.** Le premier client étranger, ou la première facture intracommunautaire, rendra ce raccourci faux. C'est la limite structurelle de ce lot.
- **Le destinataire ne porte pas de numéro de TVA** : l'application ne le collecte pas. `ram:SpecifiedTaxRegistration` n'est donc émis que pour le vendeur. Le profil l'admet, une facturation intracommunautaire ne s'en contenterait pas.
- **Le nom `factur-x.xml` est imposé par la norme**, donc deux exports successifs se recouvrent. Le dossier de cache est vidé avant chaque écriture plutôt que d'accumuler des fichiers homonymes.
- **iOS reçoit `NoOpDocumentExporter`** : l'export y réussit sans rien faire. À implémenter avec `UIActivityViewController` le jour où la cible iOS sera activée.
- **Le PDF Factur-X n'est pas produit** — ce lot génère le XML seul, conformément au périmètre. L'embarquement dans un PDF/A-3 reste à faire.

---

## US-07 — Cycle de vie DGFIP 2026 & Piste d'Audit Fiable
- **Date :** 2026-08-29
- **Branche :** `feature/US-07-dgfip-lifecycle-audit` (créée depuis `main` @ `14da3c0`)
- **Statut :** ✅ Clos — 416/416 tests unitaires verts (+46), `verifyMigrations` vert sur `1.db` et `2.db`, APK `BUILD SUCCESSFUL`, migration et cycle de vie validés de bout en bout sur émulateur
- **Objectif :** Aligner le référentiel de statuts sur la DGFIP 2026, encadrer les transitions par une machine d'états, et tracer chaque changement dans une piste d'audit persistante.

### Constats d'audit préalable
1. **Le risque principal n'était pas le code, mais la donnée.** Le référentiel passe de `DRAFT/VALIDATED/SENT/PAID/CANCELLED` à `DRAFT/DEPOSITED/PAID/REJECTED/REFUSED/CANCELLED`. Les bases installées contenaient des lignes `VALIDATED` et `SENT` : sans remappage, `InvoiceStatus.valueOf()` aurait levé **à la lecture** — pas à l'écriture — rendant chaque écran inutilisable. 31 fichiers référençaient les deux valeurs supprimées.
2. **Aucune machine d'états n'existait.** `Invoice.sq:updateStatus` avait un unique appelant (la cascade d'annulation par avoir) et aucun contrôle de transition nulle part.
3. **Aucune horloge en commonMain.** Cinq fichiers documentaient le rejet de `kotlinx-datetime` en v1. Un horodatage d'audit fourni par l'appelant serait falsifiable — donc incompatible avec la *fiabilité* attendue de la PAF. Point bloquant, signalé en réserve dès l'US-05.

### Décisions d'architecture (validées par le PO)
1. **Adoption de `kotlinx-datetime`**, revenant sur la décision v1. `Clock` est une interface injectée (`SystemClock` / `FixedClock`) : les tests d'audit deviennent déterministes sans ouvrir la moindre porte côté production. Lève au passage la réserve US-05 sur l'exercice de numérotation des avoirs.
2. **Matrice 6 × 6 déclarative.** Table exhaustive, y compris les états terminaux avec un ensemble vide : ce qui n'est pas écrit est interdit. Le seul retour en arrière est `REJECTED → DRAFT` — une facture rejetée par la plateforme n'est jamais entrée dans le circuit légal, sa correction est la procédure attendue. Corollaire : `isEditable` vaut pour `DRAFT` **et** `REJECTED`. `isCancellableByCreditNote` est désormais **dérivé de la table** plutôt que réénuméré, pour ne pas pouvoir diverger d'elle.
3. **« Valider et émettre » produit `DEPOSITED`** : émettre, c'est déposer sur le PPF/PDP.
4. **`CANCELLED` n'est jamais proposé à l'utilisateur.** `userActionableFrom()` l'exclut ; seule l'émission d'un avoir y conduit, dans la transaction atomique héritée de l'US-05.
5. **`canDelete` reste plus strict que `isEditable`** : une facture rejetée redevient corrigeable, mais la supprimer effacerait son passage et sa trace d'audit du dossier.

### Fichiers créés
| Fichier | Rôle |
|---|---|
| `domain/invoice/InvoiceStatusTransition.kt` | Machine d'états, `allowedFrom` / `userActionableFrom` / `validate`. |
| `domain/invoice/ChangeInvoiceStatusUseCase.kt` | Point d'entrée unique des transitions + contrat `InvoiceStatusRepository`. |
| `domain/audit/AuditEntry.kt` | Modèle PAF et contrat de lecture. |
| `domain/time/Clock.kt` | `Clock`, `SystemClock`, `FixedClock`. |
| `data/audit/SqlDelightAuditRepository.kt` | Lecture de l'historique (écriture réservée au dépôt facture). |
| `sqldelight/…/AuditLog.sq` | Table en écriture seule + index de lecture. |
| `sqldelight/…/2.sqm` | Migration 2 → 3 : table, **trace du remappage**, remappage. |
| `sqldelight/databases/2.db`, `3.db` | Schémas de référence. |
| `androidUnitTest/resources/migrations/schema-v2.db` | Base v2 réelle, socle du test de migration de données. |
| 3 fichiers de test | 46 cas. |

### Fichiers modifiés (principaux)
`InvoiceStatus.kt` (6 statuts) · `Invoice.kt` (règles dérivées) · `SqlDelightInvoiceRepository.kt` (`changeStatus` transactionnel, `Uuid.random()`) · `SqlDelightCreditNoteRepository.kt` (cascade d'annulation désormais tracée) · `InvoiceDetailUiState/ViewModel/Screen` (actions contextuelles, saisie de motif, chronologie PAF) · `InvoiceListUiState` (filtres) · `InvoiceStatusUi.kt` · `DashboardScreen.kt` · `DashboardAnalytics` (« en attente » = `DEPOSITED`) · `App.kt` · i18n (16 clés) · `libs.versions.toml` + `build.gradle.kts`.

### Tests
- **Migration** : `verifyCommonMainLedgerHubDatabaseMigration` → `BUILD SUCCESSFUL` sur `1.db` **et** `2.db`.
- **Unitaires** : **416/416 verts** (370 → 416).
  - `InvoiceStatusTransitionTest` — 8 cas dont la **matrice exhaustive 6 × 6** (36 cases énumérées à la main, indépendamment de l'implémentation) et un garde-fou de complétude.
  - `ChangeInvoiceStatusUseCaseTest` — 7 cas : refus **avant** tout accès au dépôt, motif obligatoire, correction d'un rejet.
  - `InvoiceLifecycleRulesTest` — 3 cas : `isEditable`, `canDelete`, `isCancellableByCreditNote`.
  - `AuditTrailPersistenceTest` — 6 cas : atomicité, motif persisté, ordre chronologique, cloisonnement par facture.
  - `DgfipStatusMigrationTest` — 4 cas de **migration de données**, ce que `verifyMigrations` ne couvre pas.
  - `InvoiceLifecycleUiTest` — 15 cas : actions proposées **et non proposées** par statut, motif obligatoire, rendu de la PAF.
  - `DashboardAnalyticsTest` — 2 cas ajoutés, sémantique « en attente » redéfinie.
- **Build** : `assembleDebug` → `BUILD SUCCESSFUL`.

### Matrice RCA
| # | Symptôme | Cause racine | Correctif |
|---|---|---|---|
| 1 | `Schema.migrate(driver, 0, 2)` : « no such table » | SQLDelight n'expose pas les `CREATE` d'une version passée ; migrer depuis 0 ne crée rien. | Le test part du schéma de référence `2.db`, copié en ressource de test : une vraie base SQLite v2, donc la copie la plus fidèle d'une installation existante. |
| 2 | Arguments dupliqués dans `App.kt` | Remplacement scripté appliqué à deux niveaux d'indentation : il frappe deux fois le même site et manque le second. **Troisième occurrence de ce piège** (US-06, US-07 ×2). | Déduplication et ajout manuel. À l'avenir, ancrer ces remplacements sur un texte unique plutôt que sur l'indentation. |
| 3 | Branches `when` dupliquées après le remplacement en masse `VALIDATED`/`SENT` → `DEPOSITED` | Deux valeurs distinctes fusionnées en une seule produisent mécaniquement des doublons dans les `when` exhaustifs. | Réécriture manuelle des cinq mappings concernés avec les six statuts. |
| 4 | `DashboardAnalyticsTest` en échec | Vrai changement de sémantique : avant, `VALIDATED` ne comptait nulle part et seul `SENT` était « en attente ». Leur fusion sous `DEPOSITED` déplace la frontière. | Test scindé en trois cas explicites, dont un nouveau sur `REJECTED`/`REFUSED`. |

### Recette manuelle — migration sur base existante
Installation **par-dessus** une base v2, sans désinstallation, après injection de deux lignes `VALIDATED` et `SENT`.

| Contrôle | Avant | Après |
|---|---|---|
| `PRAGMA user_version` | 2 | **3** |
| Lignes `VALIDATED` / `SENT` | 1 / 1 | **0 / 0 — remappées en `DEPOSITED`** |
| `DRAFT` / `PAID` / `CANCELLED` | intacts | **intacts** |
| Table `AuditLog` | absente | créée, **2 entrées traçant le remappage** |

### Recette manuelle — cycle de vie
| Étape | Résultat |
|---|---|
| Facture `DRAFT` | Une seule action : « Marquer comme déposée ». Avoir désactivé. PAF vide. |
| Dépôt | `DEPOSITED` en base, trace `DRAFT → DEPOSITED` horodatée `2026-08-29T15:43:09.174231Z` |
| Écran après dépôt | Édition verrouillée avec cadenas, avoir **activé**, trois actions (encaissée / rejet / refus), aucune annulation directe |
| Rejet sans motif | « Confirmer » désactivé, champ en erreur, message explicite |
| Rejet avec motif | `REJECTED` en base, trace `DEPOSITED → REJECTED` avec motif |
| Écran après rejet | Édition **rouverte**, avoir désactivé, une seule action « Corriger et repasser en brouillon », PAF à deux entrées chronologiques |
| Logcat | 0 crash, 0 exception |

### Points d'attention transmis
- **Résolution de l'anomalie de seeding (acte 1) :** Remplacement de la détection de base vide basée sur `selectAll()` par un comptage SQL brut (`selectCount()`). Même en présence de données partiellement malformées ou d'erreurs de parsing de lignes, le seeding de démonstration n'est plus redéclenché à tort et n'écrase plus les factures existantes.
- **Activation effective des clés étrangères SQLite (`PRAGMA foreign_keys = ON`) (acte 2) :** `PRAGMA foreign_keys = ON` est désormais exécuté systématiquement à l'initialisation de tous les drivers SQLite (`AndroidSqliteDriver`, `JdbcSqliteDriver`, `NativeSqliteDriver`). L'intégrité référentielle et les clauses `ON DELETE CASCADE` sont garanties sur toutes les plateformes (Android, JVM, iOS).
- **`STATUS_VALIDATED` et `STATUS_SENT` subsistent** : elles servent désormais aux devis (`QuoteStatus`), dont le cycle de vie est distinct de celui, réglementaire, des factures.
- **Le remappage `VALIDATED`/`SENT` → `DEPOSITED` est irréversible** et perd la nuance « validée mais pas encore transmise ». Elle n'existe pas au référentiel DGFIP : c'est un choix de conformité, pas une perte accidentelle.
- **Aucun horodatage n'est encore affiché en format local** : la PAF montre l'ISO brut. Lisible en audit, perfectible en interface.
