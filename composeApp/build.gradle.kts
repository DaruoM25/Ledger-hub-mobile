import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    // ── Cibles compilées ──────────────────────────────────────────────────────
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Les 3 cibles iOS couvrent : simulateur ARM (M1+), device ARM, simulateur x86
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true  // Requis pour Compose Multiplatform sur iOS
        }
    }

    // ── Source Sets ───────────────────────────────────────────────────────────
    sourceSets {

        /**
         * RÈGLE D'OR KMP — LIRE AVANT TOUTE MODIFICATION :
         * commonMain = code partagé iOS + Android.
         * ❌ Aucun import android.*, Context, ou Jetpack natif ici.
         * ❌ Aucun moteur Ktor platform-specific (OkHttp, Darwin) ici.
         * ✅ Ktor Core, kotlinx.*, Compose Multiplatform = autorisés.
         */
        commonMain.dependencies {
            // Compose Multiplatform — UI 100% partagée
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            // Réseau — Ktor Core uniquement (moteurs dans androidMain / iosMain)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Concurrence & Sérialisation
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Persistance — SQLDelight Core (le driver SQLite concret vient d'androidMain/iosMain)
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.datetime)
        }

        // ── Android uniquement ─────────────────────────────────────────────
        androidMain.dependencies {
            implementation(libs.ktor.client.android)        // Moteur OkHttp sous le capot
            implementation(libs.androidx.activity.compose)  // setContent { }
            implementation(compose.preview)                 // @Preview en Android Studio
            implementation(libs.sqldelight.android.driver)  // AndroidSqliteDriver
        }

        // ── iOS uniquement ─────────────────────────────────────────────────
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)         // Moteur NSURLSession
            implementation(libs.sqldelight.native.driver)   // NativeSqliteDriver
        }

        // ── Tests partagés ─────────────────────────────────────────────────
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)   // runTest, advanceUntilIdle
            implementation(libs.ktor.client.mock)           // MockEngine — LedgerRepositoryImplTest
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)                  // runComposeUiTest, onNodeWithTag
        }

        /**
         * androidUnitTest — Robolectric requis.
         * `runComposeUiTest` (compose.uiTest) sur Android a besoin d'un runtime Android réel
         * (Build.FINGERPRINT, Looper, etc.), absent des tests JVM locaux "nus".
         * Robolectric fournit ce runtime simulé pour exécuter les tests d'interface sans émulateur.
         * Voir HelloScreenRobolectricTest.kt (androidUnitTest) — équivalent de HelloScreenTest.kt
         * (commonTest) annoté @RunWith(RobolectricTestRunner), car cette annotation JVM/Android
         * ne peut pas être ajoutée à la classe partagée sans casser la compilation iosTest.
         */
        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            // JdbcSqliteDriver (JVM, en mémoire) — réservé aux tests JVM/Robolectric locaux,
            // pour garder la suite de tests SQLDelight rapide, déterministe et sans fichier
            // disque. Indisponible côté iosTest (native) : voir SqlDelightInvoiceRepositoryTest.
            implementation(libs.sqldelight.sqlite.driver)
        }

        /**
         * androidInstrumentedTest — tests d'interface exécutés sur un émulateur/appareil Android réel
         * (`connectedDebugAndroidTest`). `compose.uiTest` fournit `runComposeUiTest` on-device ;
         * le runner AndroidX et `ui-test-manifest` (voir bloc `dependencies` ci-dessous) fournissent
         * l'Activity hôte. Le pilote SQLDelight Android sert à construire une base en mémoire dans le test.
         */
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.junit)
            implementation(libs.sqldelight.android.driver)
        }
    }
}

// ui-test-manifest — Activity vide requise par `runComposeUiTest` sur appareil (variante debug only).
// `compose.uiTestManifest` n'est pas exposé par le DSL Compose Multiplatform 1.7.3 → coordonnée AndroidX brute.
dependencies {
    debugImplementation(libs.compose.ui.test.manifest)
}

// ── SQLDelight — schémas .sq lus depuis commonMain/sqldelight, code généré en commonMain ──
sqldelight {
    databases {
        create("LedgerHubDatabase") {
            packageName.set("com.ledgerhub.db")

            // ── Migrations (dette A-01, close en US-05) ────────────────────────────────────
            // Jusqu'ici tout ajout de table ou de colonne cassait silencieusement les bases
            // déjà installées : Schema.create ne s'exécute que sur une base neuve, et le
            // runCatching des repositories transformait le « no such table » en repli muet.
            //
            // schemaOutputDirectory fige le schéma de référence (N.db) ; verifyMigrations fait
            // échouer le build si un .sq évolue sans le .sqm correspondant. Le problème ne peut
            // donc plus passer inaperçu — il devient une erreur de compilation.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

// ── Configuration Android ─────────────────────────────────────────────────────
android {
    namespace   = "com.ledgerhub.app"
    compileSdk  = 35

    defaultConfig {
        applicationId  = "com.ledgerhub.app"
        minSdk         = 26      // Android 8 — couverture ~96% du parc mondial
        targetSdk      = 35
        versionCode    = 1
        versionName    = "0.1.0-shipaton"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true // requis par Robolectric
    }

    buildTypes {
        getByName("debug") {
            isDebuggable          = true
            applicationIdSuffix   = ".debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Les JVM de test sont **forkées** : elles n'héritent ni des `System.setProperty` du build, ni du
 * `doFirst` plus bas, qui ne valent que pour la JVM Gradle. Sans cette ligne, l'extraction de la
 * bibliothèque native de sqlite-jdbc retombe sur le Temp partagé du poste — la collision de DLL
 * diagnostiquée en US-16.
 */
tasks.withType<Test>().configureEach {
    val sqliteTmp = rootDir.resolve("build/tmp/sqlite").apply { mkdirs() }
    systemProperty("org.sqlite.tmpdir", sqliteTmp.invariantSeparatorsPath)
}

// HelloScreenTest (commonTest) reste valide pour iosTest ; côté Android local, son équivalent
// Robolectric (HelloScreenRobolectricTest, androidUnitTest) le remplace — voir commentaire ci-dessus.
tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
    filter {
        excludeTestsMatching("com.ledgerhub.presentation.hello.HelloScreenTest")
        excludeTestsMatching("com.ledgerhub.presentation.invoiceform.InvoiceFormScreenTest")
        excludeTestsMatching("com.ledgerhub.presentation.quoteform.QuoteFormScreenTest")
        excludeTestsMatching("com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreenTest")
    }
}

// Filet de sécurité hérité de l'US-08. La pose effective a désormais lieu à la **configuration**
// (root build.gradle.kts) : un `doFirst` s'exécute après le chargement éventuel du natif par une
// tâche antérieure de la même JVM, et arrivait alors trop tard.
tasks.configureEach {
    doFirst {
        val sqliteTmp = rootDir.resolve("build/tmp/sqlite").apply { mkdirs() }
        val sqlitePath = sqliteTmp.absolutePath.replace('\\', '/')
        System.setProperty("org.sqlite.tmpdir", sqlitePath)
        System.setProperty("java.io.tmpdir", sqlitePath)
    }
}
