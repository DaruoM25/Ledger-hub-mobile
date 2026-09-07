package com.ledgerhub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/**
 * Client HTTP iOS — moteur Darwin (NSURLSession). Mêmes délais d'attente explicites que côté
 * Android : un backend injoignable doit échouer vite, jamais laisser un écran en chargement.
 */
actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}

private const val CONNECT_TIMEOUT_MILLIS = 5_000L
private const val SOCKET_TIMEOUT_MILLIS = 10_000L
private const val REQUEST_TIMEOUT_MILLIS = 15_000L

/**
 * Backend métier. Pas d'équivalent iOS au `buildConfigField` d'Android : la valeur est ici en
 * dur, alignée sur le défaut Android (`ledgerhub.apiBaseUrl`). iOS est en attente (voir
 * IOS_STATUS.md) — à basculer sur une configuration injectée quand la cible reprendra.
 */
actual val ledgerApiBaseUrl: String = "http://130.61.25.71"
