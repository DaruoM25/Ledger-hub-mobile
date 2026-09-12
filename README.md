# LedgerHub Mobile

![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin%20Multiplatform-2.0%2B-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-UI-4285F4?logo=jetpackcompose&logoColor=white)
![RevenueCat](https://img.shields.io/badge/RevenueCat-Monetization-FA233B)
![License: AGPL v3](https://img.shields.io/badge/License-AGPLv3-blue.svg)
![Tests](https://img.shields.io/badge/Tests-1%2C157%20Passed%20(100%25)-brightgreen)
![Android](https://img.shields.io/badge/Android%2016-Verified%20(Galaxy%20S23%2B)-3DDC84?logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-CI%20Validated%20(macOS--14%20runner)-000000?logo=apple&logoColor=white)

## Executive Pitch

LedgerHub Mobile is a sovereign, cross-platform B2B invoicing application built with Kotlin Multiplatform and Compose Multiplatform, designed to comply with the French and European 2026 electronic invoicing reform. It generates and validates hybrid **Factur-X** documents (PDF/A-3 with embedded structured XML), giving small and mid-sized businesses a mobile-first path to full fiscal compliance without depending on a proprietary, closed SaaS lock-in — the AGPLv3 license guarantees that any hosted derivative remains open to its users.

## Architecture & Engineering Excellence

**Strict financial arithmetic.** All monetary values are handled as integer cent amounts (`Long`), never as `Double` or `Float`. Every HT (excl. tax) / VAT / TTC (incl. tax) computation is exact to the cent, eliminating floating-point drift that would otherwise cause rejections from French tax authorities.

**Clean Architecture & Unidirectional Data Flow.** The codebase follows a strict Clean Architecture split between `domain` (pure business logic, platform-agnostic), `data` (Ktor networking, SQLDelight persistence), and `presentation` (Compose Multiplatform UI). State is exposed exclusively through `StateFlow`, following a strict UDF/MVVM pattern. 100% of the UI is shared through Compose Multiplatform — no platform-specific UI toolkit is used on either target.

**Offline-first resilience.** Local persistence is handled by SQLDelight, with all database I/O explicitly confined off the main thread (`Dispatchers.Default`). Network synchronization is handled through Ktor Client against the Kubernetes-hosted backend API, with resilient retry and reconciliation logic for intermittent connectivity.

**Fiscal engine & control.** SIREN (9-digit) and SIRET (14-digit) identifiers are validated client-side using a Luhn checksum before any payload reaches the backend. The intra-community VAT key is computed and certified using the official **Modulo 97** formula, with automatic routing toward the French public invoicing portal (PPF) once a document is finalized.

**Security & R8 hardening.** Release builds run with R8 minification and obfuscation enabled (`isMinifyEnabled` / `isShrinkResources`), producing a hardened release APK of roughly 4.4 MB. Kotlinx serializers and SQLDelight-generated schemas are explicitly preserved through consolidated ProGuard rules. Secret leakage is prevented through a pre-commit Git hook and an automated secret scanner, with zero credentials tracked in source control.

## Dedicated Devpost Judges Section (Shipaton Evaluation Guide)

To evaluate Pro-tier features without entering payment details:

1. Launch the app and trigger the **Paywall** screen (via the FEC export button or settings).
2. Tap **"Partner / Judge Access Code"** at the bottom.
3. Enter the code **`DEVPOST2026`** to instantly unlock all Pro features, including unlimited invoicing and the official FEC accounting export (compliant with **Article A.47 A-1 of the French Tax Procedures Code — LPF**).

**Run the automated test suite (1,157 tests):**
```bash
./gradlew :composeApp:testDebugUnitTest