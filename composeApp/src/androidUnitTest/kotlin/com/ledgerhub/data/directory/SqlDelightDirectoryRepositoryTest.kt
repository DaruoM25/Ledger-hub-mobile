package com.ledgerhub.data.directory

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.RoutingMode
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Niveau 2 — persistance de [SqlDelightDirectoryRepository] sur base SQLite in-memory
 * (`JdbcSqliteDriver`). Placé en `androidUnitTest` comme les autres tests de dépôt SQLDelight
 * (le pilote JDBC n'est pas résoluble depuis `commonTest`, partagé avec la cible iOS).
 */
class SqlDelightDirectoryRepositoryTest {

    private val clock = FixedClock("2026-08-30T09:00:00Z")

    private fun newDatabase(): LedgerHubDatabase {
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun ppfEntry() = DirectoryEntry(
        siren = "443061841",
        siret = null,
        companyName = "ATELIER MARTIN",
        vatNumber = null,
        routingMode = RoutingMode.PPF,
        pdpIdentifier = null,
        isVatSubject = false,
        status = DirectoryStatus.ACTIVE,
        lastSyncAt = "ignored-overwritten-by-clock",
    )

    private fun pdpEntry() = DirectoryEntry(
        siren = "552081317",
        siret = "55208131700018",
        companyName = "DANONE SA",
        vatNumber = "FR03552081317",
        routingMode = RoutingMode.PDP,
        pdpIdentifier = "PDP-0001-FR",
        isVatSubject = true,
        status = DirectoryStatus.ACTIVE,
        lastSyncAt = "ignored",
    )

    @Test
    fun cache_thenFindBySiren_roundTripsEveryColumn_includingNullsAndBooleanFalse() = runTest {
        val repository = SqlDelightDirectoryRepository(newDatabase(), clock)
        repository.cache(ppfEntry())

        val loaded = repository.findBySiren("443061841")
        assertNotNull(loaded)
        assertNull(loaded.siret)
        assertNull(loaded.vatNumber)
        assertNull(loaded.pdpIdentifier)
        assertEquals("ATELIER MARTIN", loaded.companyName)
        assertEquals(RoutingMode.PPF, loaded.routingMode)
        assertEquals(false, loaded.isVatSubject)
        assertEquals(DirectoryStatus.ACTIVE, loaded.status)
        // lastSyncAt est réécrit par l'horloge injectée, jamais repris de l'appelant.
        assertEquals("2026-08-30T09:00:00Z", loaded.lastSyncAt)
    }

    @Test
    fun cache_pdpEntry_roundTripsRoutingModeAndPdpIdentifier() = runTest {
        val repository = SqlDelightDirectoryRepository(newDatabase(), clock)
        repository.cache(pdpEntry())

        val loaded = repository.findBySiren("552081317")
        assertNotNull(loaded)
        assertEquals(RoutingMode.PDP, loaded.routingMode)
        assertEquals("PDP-0001-FR", loaded.pdpIdentifier)
        assertEquals("FR03552081317", loaded.vatNumber)
        assertEquals(true, loaded.isVatSubject)
    }

    @Test
    fun findBySiret_locatesEntryByEstablishmentIdentifier() = runTest {
        val repository = SqlDelightDirectoryRepository(newDatabase(), clock)
        repository.cache(pdpEntry())

        val loaded = repository.findBySiret("55208131700018")
        assertNotNull(loaded)
        assertEquals("552081317", loaded.siren)
    }

    @Test
    fun cache_isUpsert_replacesInsteadOfDuplicating() = runTest {
        val repository = SqlDelightDirectoryRepository(newDatabase(), clock)
        repository.cache(ppfEntry())
        repository.cache(ppfEntry().copy(companyName = "ATELIER MARTIN & FILS", status = DirectoryStatus.INACTIVE))

        assertEquals(1, repository.all().size)
        val loaded = repository.findBySiren("443061841")
        assertNotNull(loaded)
        assertEquals("ATELIER MARTIN & FILS", loaded.companyName)
        assertEquals(DirectoryStatus.INACTIVE, loaded.status)
    }

    @Test
    fun findBySiren_returnsNull_whenAbsent() = runTest {
        val repository = SqlDelightDirectoryRepository(newDatabase(), clock)
        assertNull(repository.findBySiren("999999999"))
    }
}
