package com.ledgerhub.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) { json() }
}

/** Le simulateur iOS partage la pile réseau de la machine hôte — pas d'alias spécial requis. */
actual val authBaseUrl: String = "http://127.0.0.1:3000"
