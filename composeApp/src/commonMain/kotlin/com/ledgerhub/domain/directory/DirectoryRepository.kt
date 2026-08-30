package com.ledgerhub.domain.directory

/**
 * Accès à l'annuaire centralisé DGFIP/PPF.
 *
 * À ce stade (US-09 mobile), l'implémentation est locale : un jeu d'entrées de démonstration
 * ([com.ledgerhub.data.directory.MockDirectoryRepository]) doublé d'un cache SQLDelight
 * ([com.ledgerhub.data.directory.SqlDelightDirectoryRepository]). La synchronisation réseau
 * réelle (Ktor) est hors périmètre.
 */
interface DirectoryRepository {

    /** Fiche associée au SIREN, ou `null` si absente. */
    suspend fun findBySiren(siren: String): DirectoryEntry?

    /** Fiche associée au SIRET, ou `null` si absente. */
    suspend fun findBySiret(siret: String): DirectoryEntry?

    /** Toutes les fiches connues (cache). */
    suspend fun all(): List<DirectoryEntry>

    /** Insère ou remplace une fiche dans le cache. */
    suspend fun cache(entry: DirectoryEntry)
}
