package com.ledgerhub.data.directory

import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.directory.LuhnChecksum
import com.ledgerhub.domain.directory.RoutingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Annuaire local de démonstration — jeu d'entreprises fictives couvrant les cas de routage
 * (PPF / PDP), l'assujettissement à la TVA et les statuts. Aucun backend réel à ce stade
 * (calqué sur `MockQuoteRepository`).
 *
 * @param simulatedDelayMillis délai simulé, pour observer l'indicateur de chargement.
 */
class MockDirectoryRepository(
    private val simulatedDelayMillis: Long = 600L,
) : DirectoryRepository {

    private val mutex = Mutex()
    private val entries: MutableMap<String, DirectoryEntry> =
        SEED.associateBy { it.siren }.toMutableMap()

    override suspend fun findBySiren(siren: String): DirectoryEntry? {
        delay(simulatedDelayMillis)
        if (siren == UNKNOWN_SIREN) return null
        return mutex.withLock {
            entries[siren] ?: if (LuhnChecksum.isValidSiren(siren)) {
                generateDynamicEntry(siren, null)
            } else null
        }
    }

    override suspend fun findBySiret(siret: String): DirectoryEntry? {
        delay(simulatedDelayMillis)
        if (siret == UNKNOWN_SIRET) return null
        return mutex.withLock {
            entries.values.firstOrNull { it.siret == siret }
                ?: entries[siret.take(9)]?.copy(siret = siret)
                ?: if (LuhnChecksum.isValidSiret(siret)) {
                    generateDynamicEntry(siret.take(9), siret)
                } else null
        }
    }

    override suspend fun all(): List<DirectoryEntry> =
        mutex.withLock { entries.values.sortedBy { it.companyName } }

    override suspend fun cache(entry: DirectoryEntry) {
        mutex.withLock { entries[entry.siren] = entry }
    }

    private fun generateDynamicEntry(siren: String, siret: String?): DirectoryEntry = DirectoryEntry(
        siren = siren,
        siret = siret,
        companyName = "ENTREPRISE $siren",
        vatNumber = FrenchVatNumber.format(siren),
        routingMode = RoutingMode.PPF,
        pdpIdentifier = null,
        isVatSubject = true,
        status = DirectoryStatus.ACTIVE,
        lastSyncAt = SYNCED_AT,
    )

    companion object {
        const val SYNCED_AT = "2026-08-30T09:00:00Z"
        const val UNKNOWN_SIREN = "000000000"
        const val UNKNOWN_SIRET = "00000000000000"

        val SEED: List<DirectoryEntry> = listOf(
            DirectoryEntry(
                siren = "732829320",
                siret = "73282932000074",
                companyName = "RENAULT SAS",
                vatNumber = FrenchVatNumber.format("732829320"),
                routingMode = RoutingMode.PPF,
                pdpIdentifier = null,
                isVatSubject = true,
                status = DirectoryStatus.ACTIVE,
                lastSyncAt = SYNCED_AT,
            ),
            DirectoryEntry(
                siren = "380129866",
                siret = "38012986648625",
                companyName = "ORANGE SA",
                vatNumber = FrenchVatNumber.format("380129866"),
                routingMode = RoutingMode.PPF,
                pdpIdentifier = null,
                isVatSubject = true,
                status = DirectoryStatus.ACTIVE,
                lastSyncAt = SYNCED_AT,
            ),
            DirectoryEntry(
                siren = "397480930",
                siret = "39748093003464",
                companyName = "BOUYGUES TELECOM",
                vatNumber = FrenchVatNumber.format("397480930"),
                routingMode = RoutingMode.PPF,
                pdpIdentifier = null,
                isVatSubject = true,
                status = DirectoryStatus.ACTIVE,
                lastSyncAt = SYNCED_AT,
            ),
            DirectoryEntry(
                siren = "552081317",
                siret = null,
                companyName = "DANONE SA",
                vatNumber = FrenchVatNumber.format("552081317"),
                routingMode = RoutingMode.PDP,
                pdpIdentifier = "PDP-0001-FR",
                isVatSubject = true,
                status = DirectoryStatus.ACTIVE,
                lastSyncAt = SYNCED_AT,
            ),
            DirectoryEntry(
                siren = "443061841",
                siret = null,
                companyName = "ATELIER MARTIN",
                vatNumber = null,
                routingMode = RoutingMode.PPF,
                pdpIdentifier = null,
                isVatSubject = false,
                status = DirectoryStatus.ACTIVE,
                lastSyncAt = SYNCED_AT,
            ),
            DirectoryEntry(
                siren = "410037121",
                siret = null,
                companyName = "SOCIÉTÉ INACTIVE SARL",
                vatNumber = FrenchVatNumber.format("410037121"),
                routingMode = RoutingMode.PPF,
                pdpIdentifier = null,
                isVatSubject = true,
                status = DirectoryStatus.INACTIVE,
                lastSyncAt = SYNCED_AT,
            ),
        )
    }
}
