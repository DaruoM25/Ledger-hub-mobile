package com.ledgerhub.domain.directory

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Dépôt d'annuaire en mémoire pour les tests de cas d'usage. */
private class FakeDirectoryRepository(entries: List<DirectoryEntry> = emptyList()) : DirectoryRepository {
    private val store = entries.associateBy { it.siren }.toMutableMap()
    override suspend fun findBySiren(siren: String): DirectoryEntry? = store[siren]
    override suspend fun findBySiret(siret: String): DirectoryEntry? = store.values.firstOrNull { it.siret == siret }
    override suspend fun all(): List<DirectoryEntry> = store.values.toList()
    override suspend fun cache(entry: DirectoryEntry) { store[entry.siren] = entry }
}

class ResolveDirectoryEntryUseCaseTest {

    private fun entry(
        siren: String,
        siret: String? = null,
        routingMode: RoutingMode = RoutingMode.PPF,
        pdpIdentifier: String? = null,
    ) = DirectoryEntry(
        siren = siren,
        siret = siret,
        companyName = "ACME $siren",
        vatNumber = FrenchVatNumber.format(siren),
        routingMode = routingMode,
        pdpIdentifier = pdpIdentifier,
        isVatSubject = true,
        status = DirectoryStatus.ACTIVE,
        lastSyncAt = "2026-08-30T09:00:00Z",
    )

    @Test
    fun invalidChecksum_returnsInvalidChecksum_withoutTouchingRepository() = runTest {
        val useCase = ResolveDirectoryEntryUseCase(FakeDirectoryRepository())

        assertIs<DirectoryLookupResult.InvalidChecksum>(useCase("123456789"))   // 9 chiffres, Luhn KO
        assertIs<DirectoryLookupResult.InvalidChecksum>(useCase("1234"))        // longueur non exploitable
        assertIs<DirectoryLookupResult.InvalidChecksum>(useCase("73282932000075")) // 14 chiffres, Luhn KO
    }

    @Test
    fun validButUnknownSiren_returnsNotFound() = runTest {
        val useCase = ResolveDirectoryEntryUseCase(FakeDirectoryRepository())
        assertIs<DirectoryLookupResult.NotFound>(useCase("732829320"))
    }

    @Test
    fun knownSiren_returnsResolvedEntry() = runTest {
        val useCase = ResolveDirectoryEntryUseCase(FakeDirectoryRepository(listOf(entry("732829320"))))

        val result = useCase("732829320")

        assertIs<DirectoryLookupResult.Resolved>(result)
        assertEquals(RoutingMode.PPF, result.entry.routingMode)
        assertEquals("ACME 732829320", result.entry.companyName)
    }

    @Test
    fun knownSiret_resolvesToPdpRoutedEntry_withIdentifier() = runTest {
        val pdpEntry = entry(
            siren = "552081317",
            siret = "55208131700018", // Luhn-valide
            routingMode = RoutingMode.PDP,
            pdpIdentifier = "PDP-0001-FR",
        )
        val useCase = ResolveDirectoryEntryUseCase(FakeDirectoryRepository(listOf(pdpEntry)))

        val result = useCase("55208131700018")

        assertIs<DirectoryLookupResult.Resolved>(result)
        assertEquals(RoutingMode.PDP, result.entry.routingMode)
        assertEquals("PDP-0001-FR", result.entry.pdpIdentifier)
    }

    @Test
    fun formattingCharactersInQuery_areIgnored() = runTest {
        val useCase = ResolveDirectoryEntryUseCase(FakeDirectoryRepository(listOf(entry("732829320"))))
        assertIs<DirectoryLookupResult.Resolved>(useCase("732 829 320"))
    }
}
