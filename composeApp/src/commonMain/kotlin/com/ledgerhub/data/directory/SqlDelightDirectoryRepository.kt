package com.ledgerhub.data.directory

import com.ledgerhub.db.DirectoryEntry as DirectoryEntryRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.RoutingMode
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock

/**
 * Cache SQLDelight des fiches d'annuaire (voir `DirectoryEntry.sq`).
 *
 * @param clock horloge injectée : `lastSyncAt` est fixé ici au moment de la mise en cache,
 *   jamais fourni par l'appelant, pour rester aligné avec le reste des horodatages du domaine.
 */
class SqlDelightDirectoryRepository(
    private val database: LedgerHubDatabase,
    private val clock: Clock = SystemClock,
) : DirectoryRepository {

    override suspend fun findBySiren(siren: String): DirectoryEntry? =
        database.directoryEntryQueries.selectBySiren(siren).executeAsOneOrNull()?.toDomain()

    override suspend fun findBySiret(siret: String): DirectoryEntry? =
        database.directoryEntryQueries.selectBySiret(siret).executeAsOneOrNull()?.toDomain()

    override suspend fun all(): List<DirectoryEntry> =
        database.directoryEntryQueries.selectAll().executeAsList().map { it.toDomain() }

    override suspend fun cache(entry: DirectoryEntry) {
        database.directoryEntryQueries.upsert(
            siren = entry.siren,
            siret = entry.siret,
            companyName = entry.companyName,
            vatNumber = entry.vatNumber,
            routingMode = entry.routingMode.name,
            pdpIdentifier = entry.pdpIdentifier,
            isVatSubject = if (entry.isVatSubject) 1L else 0L,
            status = entry.status.name,
            lastSyncAt = clock.nowIso(),
        )
    }

    private fun DirectoryEntryRow.toDomain(): DirectoryEntry = DirectoryEntry(
        siren = siren,
        siret = siret,
        companyName = companyName,
        vatNumber = vatNumber,
        // Une valeur devenue inconnue (référentiel qui évoluerait) ne doit pas rendre la fiche
        // illisible : repli sur une valeur par défaut plutôt que de lever.
        routingMode = RoutingMode.entries.firstOrNull { it.name == routingMode } ?: RoutingMode.PPF,
        pdpIdentifier = pdpIdentifier,
        isVatSubject = isVatSubject != 0L,
        status = DirectoryStatus.entries.firstOrNull { it.name == status } ?: DirectoryStatus.UNKNOWN,
        lastSyncAt = lastSyncAt,
    )
}
