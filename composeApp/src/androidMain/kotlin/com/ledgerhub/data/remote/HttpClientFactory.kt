package com.ledgerhub.data.remote

import com.ledgerhub.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Client HTTP Android — moteur `Android` (HttpURLConnection/OkHttp selon la plateforme).
 *
 * Les délais d'attente sont **explicites** et courts : sans eux, un backend injoignable (réseau
 * mobile capricieux, serveur éteint, port filtré) laisse la requête pendre jusqu'au délai par
 * défaut du système — plusieurs dizaines de secondes pendant lesquelles l'écran appelant reste
 * en chargement. Un échec rapide vaut mieux qu'une attente muette : l'appelant peut alors
 * afficher son erreur et rendre la main à l'utilisateur (voir
 * [com.ledgerhub.presentation.auth.SireneVerificationStatus.UNAVAILABLE]).
 */
actual fun createPlatformHttpClient(): HttpClient = HttpClient(Android) {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

/** Temps laissé à l'établissement de la connexion TCP. */
private const val CONNECT_TIMEOUT_MILLIS = 5_000L

/** Silence toléré entre deux paquets une fois la connexion ouverte. */
private const val SOCKET_TIMEOUT_MILLIS = 10_000L

/** Plafond de bout en bout d'une requête, redirections et lecture du corps comprises. */
private const val REQUEST_TIMEOUT_MILLIS = 15_000L

/**
 * Backend métier (routes `/api/...`), injecté à la compilation depuis
 * `composeApp/build.gradle.kts` (propriété Gradle `ledgerhub.apiBaseUrl`).
 *
 * La valeur n'est plus codée en dur ici : `10.0.2.2` n'est un alias de la machine hôte que pour
 * l'émulateur Android et ne désigne rien sur un appareil physique, où tout appel échouait donc
 * silencieusement. Changer de serveur ne demande plus de recompiler ce fichier :
 * `./gradlew :composeApp:assembleDebug -Pledgerhub.apiBaseUrl=http://10.0.2.2:3000`.
 */
actual val ledgerApiBaseUrl: String = BuildConfig.LEDGER_API_BASE_URL
