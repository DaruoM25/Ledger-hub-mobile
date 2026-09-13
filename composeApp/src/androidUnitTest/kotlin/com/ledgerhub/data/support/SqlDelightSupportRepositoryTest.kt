package com.ledgerhub.data.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.support.SupportCategory
import com.ledgerhub.domain.support.SupportTicket
import com.ledgerhub.domain.support.TicketStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightSupportRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: LedgerHubDatabase
    private lateinit var repository: SqlDelightSupportRepository

    @BeforeTest
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        database = LedgerHubDatabase(driver)
        repository = SqlDelightSupportRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun createAndRetrieveTicket_success() = runTest {
        val ticket = SupportTicket(
            id = "ticket-1",
            userId = "usr-1",
            userEmail = "user1@domain.fr",
            category = SupportCategory.MANDATORY_MENTIONS_2026,
            subject = "Problème mentions TVA",
            description = "Détail de la demande",
            status = TicketStatus.OPEN,
            createdAt = "2026-09-13T10:00:00Z",
        )

        repository.createTicket(ticket).getOrThrow()

        val retrieved = repository.getTicketById("ticket-1").getOrThrow()
        assertEquals(ticket, retrieved)

        val userTickets = repository.getTicketsByUser("usr-1").getOrThrow()
        assertEquals(1, userTickets.size)
        assertEquals(ticket, userTickets.first())

        val openCount = repository.countOpenTicketsByUser("usr-1").getOrThrow()
        assertEquals(1L, openCount)
    }

    @Test
    fun updateTicketStatus_updatesProperly() = runTest {
        val ticket = SupportTicket(
            id = "ticket-2",
            userId = "usr-2",
            userEmail = "user2@domain.fr",
            category = SupportCategory.FACTUR_X_FORMAT,
            subject = "Profil BASIC Factur-X",
            description = "Validation profil",
            status = TicketStatus.OPEN,
            createdAt = "2026-09-13T10:00:00Z",
        )

        repository.createTicket(ticket).getOrThrow()
        repository.updateTicketStatus("ticket-2", TicketStatus.RESOLVED, "2026-09-13T11:00:00Z").getOrThrow()

        val updated = repository.getTicketById("ticket-2").getOrThrow()
        assertEquals(TicketStatus.RESOLVED, updated?.status)
        assertEquals("2026-09-13T11:00:00Z", updated?.updatedAt)

        val openCount = repository.countOpenTicketsByUser("usr-2").getOrThrow()
        assertEquals(0L, openCount)
    }

    @Test
    fun getTicketById_returnsNullWhenNotFound() = runTest {
        val ticket = repository.getTicketById("unknown-id").getOrThrow()
        assertNull(ticket)
    }
}
