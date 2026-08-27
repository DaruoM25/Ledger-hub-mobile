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
