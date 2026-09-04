package com.ledgerhub.data.remote

import io.ktor.client.HttpClient

/**
 * Client HTTP + base URLs des backends locaux — chaque plateforme a un moteur Ktor différent
 * (OkHttp sous Android, NSURLSession sous iOS via Darwin, voir les `actual`) et une façon
 * différente d'atteindre le `localhost` de la machine hôte pendant le développement :
 * - Émulateur Android : `10.0.2.2` est l'alias historique de la boucle locale de l'hôte.
 * - Simulateur iOS : partage la pile réseau de son hôte, `127.0.0.1` suffit directement.
 *
 * Un seul client HTTP est partagé par tous les repositories réseau (Ledger API, SIRENE, …) — voir
 * [com.ledgerhub.db.DatabaseDriverFactory] pour le même principe côté SQLDelight.
 *
 * L'authentification n'y figure plus : depuis l'US-26 elle est entièrement locale et ne passe par
 * aucune URL (voir [com.ledgerhub.data.auth.SqlDelightAuthRepository]).
 */
expect fun createPlatformHttpClient(): HttpClient

/** Backend local de l'API métier (factures) — voir [com.ledgerhub.data.repository.LedgerRepositoryImpl]. */
expect val ledgerApiBaseUrl: String
