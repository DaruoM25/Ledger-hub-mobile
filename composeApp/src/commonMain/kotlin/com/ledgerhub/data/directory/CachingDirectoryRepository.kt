package com.ledgerhub.data.directory

import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository

/**
 * Compose une source d'annuaire ([source], ici [MockDirectoryRepository]) et un cache persistant
 * ([cache], ici [SqlDelightDirectoryRepository]) : toute résolution réussie côté source est
 * écrite dans le cache, et le cache est interrogé en premier.
 *
 * C'est l'expression concrète du choix US-09 « résolution locale + persistance/cache » tant
 * qu'aucun backend réseau n'est branché.
 */
class CachingDirectoryRepository(
    private val source: DirectoryRepository,
    private val cache: DirectoryRepository,
) : DirectoryRepository {

    override suspend fun findBySiren(siren: String): DirectoryEntry? =
        cache.findBySiren(siren) ?: source.findBySiren(siren)?.also { cache.cache(it) }

    override suspend fun findBySiret(siret: String): DirectoryEntry? =
        cache.findBySiret(siret) ?: source.findBySiret(siret)?.also { cache.cache(it) }

    override suspend fun all(): List<DirectoryEntry> =
        cache.all().ifEmpty { source.all() }

    override suspend fun cache(entry: DirectoryEntry) = cache.cache(entry)
}
