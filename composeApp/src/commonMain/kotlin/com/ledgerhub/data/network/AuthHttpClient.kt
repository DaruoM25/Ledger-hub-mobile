package com.ledgerhub.data.network

import io.ktor.client.HttpClient

/**
 * Client HTTP + base URL du backend d'authentification local — chaque plateforme a un moteur
 * Ktor différent (OkHttp sous Android, NSURLSession sous iOS via Darwin, voir les `actual`) et
 * une façon différente d'atteindre le `localhost` de la machine hôte pendant le développement :
 * - Émulateur Android : `10.0.2.2` est l'alias historique de la boucle locale de l'hôte.
 * - Simulateur iOS : partage la pile réseau de son hôte, `127.0.0.1` suffit directement.
 *
 * Voir [com.ledgerhub.db.DatabaseDriverFactory] pour le même principe côté SQLDelight.
 */
expect fun createPlatformHttpClient(): HttpClient

expect val authBaseUrl: String
