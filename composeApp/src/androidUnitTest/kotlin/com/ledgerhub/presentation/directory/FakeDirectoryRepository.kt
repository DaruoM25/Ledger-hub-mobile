package com.ledgerhub.presentation.directory

import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.directory.RoutingMode

/** Dépôt d'annuaire en mémoire, partagé par les tests de présentation. */
internal class FakeDirectoryRepository(
    entries: List<DirectoryEntry> = emptyList(),
) : DirectoryRepository {
    private val store = entries.associateBy { it.siren }.toMutableMap()
    override suspend fun findBySiren(siren: String) = store[siren]
    override suspend fun findBySiret(siret: String) = store.values.firstOrNull { it.siret == siret }
    override suspend fun all() = store.values.toList()
    override suspend fun cache(entry: DirectoryEntry) { store[entry.siren] = entry }
}

internal fun renaultEntry() = DirectoryEntry(
    siren = "732829320",
    siret = "73282932000074",
    companyName = "RENAULT SAS",
    vatNumber = FrenchVatNumber.format("732829320"),
    routingMode = RoutingMode.PPF,
    pdpIdentifier = null,
    isVatSubject = true,
    status = DirectoryStatus.ACTIVE,
    lastSyncAt = "2026-08-30T09:00:00Z",
)

internal fun danonePdpEntry() = DirectoryEntry(
    siren = "552081317",
    siret = null,
    companyName = "DANONE SA",
    vatNumber = FrenchVatNumber.format("552081317"),
    routingMode = RoutingMode.PDP,
    pdpIdentifier = "PDP-0001-FR",
    isVatSubject = true,
    status = DirectoryStatus.ACTIVE,
    lastSyncAt = "2026-08-30T09:00:00Z",
)

internal fun orangeEntry() = DirectoryEntry(
    siren = "380129866",
    siret = null,
    companyName = "ORANGE SA",
    vatNumber = FrenchVatNumber.format("380129866"),
    routingMode = RoutingMode.PPF,
    pdpIdentifier = null,
    isVatSubject = true,
    status = DirectoryStatus.ACTIVE,
    lastSyncAt = "2026-08-30T09:00:00Z",
)

