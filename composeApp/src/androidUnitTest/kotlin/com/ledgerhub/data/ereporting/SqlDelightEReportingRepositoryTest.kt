package com.ledgerhub.data.ereporting

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingStatus
import com.ledgerhub.domain.ereporting.EReportingType
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Niveau 2 — persistance réelle de [SqlDelightEReportingRepository] sur base SQLite in-memory
 * (`JdbcSqliteDriver`). Base neuve par test.
 *
 * Placé dans `androidUnitTest` comme les autres tests de dépôt SQLDelight du projet
 * (`SqlDelightClientRepositoryTest`, `SqlDelightCreditNoteRepositoryTest`…) : le pilote JDBC
 * n'est pas résoluble depuis `commonTest`, partagé avec les cibles natives.
 */
class SqlDelightEReportingRepositoryTest {

    private val clock = FixedClock("2026-08-30T09:00:00Z")

    private fun newDatabase(): LedgerHubDatabase {
        // Voir la note dans SqlDelightCreditNoteRepositoryTest : le classloader sandboxé de
        // Robolectric peut faire perdre au DriverManager la trace de org.sqlite.JDBC entre deux
        // classes de test — chargement explicite pour être robuste à l'ordre d'exécution.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private fun report(
        id: String = "rep-001",
        status: EReportingStatus = EReportingStatus.DRAFT,
        ackNumber: String? = null,
    ) = EReportingReport(
        id = id,
        period = "2026-01",
        type = EReportingType.B2C,
        status = status,
        ackNumber = ackNumber,
        totalHt = 183.33,
        totalVat = 26.83,
        totalTtc = 210.16,
        transactionCount = 3,
        createdAt = "2026-08-30T09:00:00Z",
    )

    @Test
    fun tcInt01_insertAndRead_roundTripsEveryField() = runTest {
        val repository = SqlDelightEReportingRepository(newDatabase(), clock)

        repository.create(report())

        val loaded = repository.getById("rep-001")
        assertNotNull(loaded)
        assertEquals("2026-01", loaded.period)
        assertEquals(EReportingType.B2C, loaded.type)
        assertEquals(EReportingStatus.DRAFT, loaded.status)
        assertEquals(183.33, loaded.totalHt, 0.001)
        assertEquals(26.83, loaded.totalVat, 0.001)
        assertEquals(210.16, loaded.totalTtc, 0.001)
        assertEquals(3, loaded.transactionCount)
        assertEquals("2026-08-30T09:00:00Z", loaded.createdAt)
        assertNull(loaded.ackNumber)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun tcInt02_markAsAcknowledged_persistsStatusAndAckNumber() = runTest {
        val repository = SqlDelightEReportingRepository(newDatabase(), clock)
        repository.create(report())

        repository.markAsAcknowledged("rep-001", "ACK-2026-0042")

        val loaded = repository.getById("rep-001")
        assertNotNull(loaded)
        assertEquals(EReportingStatus.ACKNOWLEDGED, loaded.status)
        assertEquals("ACK-2026-0042", loaded.ackNumber)
    }
}
