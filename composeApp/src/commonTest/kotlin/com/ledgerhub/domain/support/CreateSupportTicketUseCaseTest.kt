package com.ledgerhub.domain.support

import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateSupportTicketUseCaseTest {

    private class FakeSupportRepository : SupportRepository {
        val tickets = mutableMapOf<String, SupportTicket>()

        override suspend fun getAllTickets(): Result<List<SupportTicket>> =
            Result.success(tickets.values.toList())

        override suspend fun getTicketsByUser(userId: String): Result<List<SupportTicket>> =
            Result.success(tickets.values.filter { it.userId == userId })

        override suspend fun getTicketById(id: String): Result<SupportTicket?> =
            Result.success(tickets[id])

        override suspend fun createTicket(ticket: SupportTicket): Result<Unit> {
            tickets[ticket.id] = ticket
            return Result.success(Unit)
        }

        override suspend fun updateTicketStatus(id: String, status: TicketStatus, updatedAt: String): Result<Unit> {
            val existing = tickets[id] ?: return Result.failure(NoSuchElementException())
            tickets[id] = existing.copy(status = status, updatedAt = updatedAt)
            return Result.success(Unit)
        }

        override suspend fun countOpenTicketsByUser(userId: String): Result<Long> =
            Result.success(tickets.values.count { it.userId == userId && it.status != TicketStatus.RESOLVED && it.status != TicketStatus.CLOSED }.toLong())
    }

    @Test
    fun createSupportTicket_success() = runTest {
        val repo = FakeSupportRepository()
        val clock = FixedClock("2026-09-13T10:00:00Z")
        val useCase = CreateSupportTicketUseCase(repo, clock)

        val result = useCase(
            userId = "user-123",
            userEmail = "expert@comptable.fr",
            category = SupportCategory.MANDATORY_MENTIONS_2026,
            subject = "Mention TVA sur encaissements",
            description = "Comment ajouter la mention obligatoire d'option pour le paiement de la taxe d'après les débits ?",
        )

        assertTrue(result.isSuccess)
        val ticket = result.getOrThrow()
        assertEquals("user-123", ticket.userId)
        assertEquals("expert@comptable.fr", ticket.userEmail)
        assertEquals(SupportCategory.MANDATORY_MENTIONS_2026, ticket.category)
        assertEquals("Mention TVA sur encaissements", ticket.subject)
        assertEquals(TicketStatus.OPEN, ticket.status)
        assertEquals("2026-09-13T10:00:00Z", ticket.createdAt)
        assertEquals(1, repo.tickets.size)
    }

    @Test
    fun createSupportTicket_failsOnShortSubject() = runTest {
        val repo = FakeSupportRepository()
        val useCase = CreateSupportTicketUseCase(repo)

        assertFailsWith<InvalidTicketException> {
            useCase(
                userId = "user-123",
                userEmail = "expert@comptable.fr",
                category = SupportCategory.FACTUR_X_FORMAT,
                subject = "AB",
                description = "Description valide et détaillée",
            ).getOrThrow()
        }
    }

    @Test
    fun createSupportTicket_failsOnShortDescription() = runTest {
        val repo = FakeSupportRepository()
        val useCase = CreateSupportTicketUseCase(repo)

        assertFailsWith<InvalidTicketException> {
            useCase(
                userId = "user-123",
                userEmail = "expert@comptable.fr",
                category = SupportCategory.E_REPORTING,
                subject = "Question e-reporting",
                description = "Aide",
            ).getOrThrow()
        }
    }

    @Test
    fun createSupportTicket_failsOnInvalidEmail() = runTest {
        val repo = FakeSupportRepository()
        val useCase = CreateSupportTicketUseCase(repo)

        assertFailsWith<InvalidTicketException> {
            useCase(
                userId = "user-123",
                userEmail = "invalid-email",
                category = SupportCategory.VAT_CALCULATION,
                subject = "Calcul TVA 20%",
                description = "Erreur sur le calcul de la base HT",
            ).getOrThrow()
        }
    }
}
