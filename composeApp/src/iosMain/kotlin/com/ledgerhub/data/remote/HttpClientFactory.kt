package com.ledgerhub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) { json() }
}

/**
 * Backend local de dev (routes `/api/invoices`). Le simulateur iOS partage la pile réseau de la
 * machine hôte — pas d'alias spécial requis.
 */
actual val ledgerApiBaseUrl: String = "http://127.0.0.1:3000"
