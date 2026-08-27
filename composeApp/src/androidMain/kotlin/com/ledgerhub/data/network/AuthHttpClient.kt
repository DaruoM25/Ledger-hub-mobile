package com.ledgerhub.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

actual fun createPlatformHttpClient(): HttpClient = HttpClient(Android) {
    install(ContentNegotiation) { json() }
}

/** `10.0.2.2` — alias réseau spécial de l'émulateur Android vers le `localhost` de la machine hôte. */
actual val authBaseUrl: String = "http://10.0.2.2:3000"
