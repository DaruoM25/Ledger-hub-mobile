package com.ledgerhub.data.support

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.SupportTicket as SupportTicketRow
import com.ledgerhub.domain.support.SupportCategory
import com.ledgerhub.domain.support.SupportRepository
import com.ledgerhub.domain.support.SupportTicket
import com.ledgerhub.domain.support.TicketStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implémentation SQLDelight du dépôt de tickets d'assistance (US-30).
 */
class SqlDelightSupportRepository(
    private val database: LedgerHubDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SupportRepository {

    override suspend fun getAllTickets(): Result<List<SupportTicket>> = runCatching {
        withContext(ioDispatcher) {
            database.supportTicketQueries.selectAll().executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getTicketsByUser(userId: String): Result<List<SupportTicket>> = runCatching {
        withContext(ioDispatcher) {
            database.supportTicketQueries.selectByUser(userId).executeAsList().map { it.toDomain() }
        }
    }

    override suspend fun getTicketById(id: String): Result<SupportTicket?> = runCatching {
        withContext(ioDispatcher) {
            database.supportTicketQueries.selectById(id).executeAsOneOrNull()?.toDomain()
        }
    }

    override suspend fun createTicket(ticket: SupportTicket): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            database.supportTicketQueries.insertOrReplace(
                id = ticket.id,
                userId = ticket.userId,
                userEmail = ticket.userEmail,
                category = ticket.category.rawValue,
                subject = ticket.subject,
                description = ticket.description,
                status = ticket.status.rawValue,
                createdAt = ticket.createdAt,
                updatedAt = ticket.updatedAt,
            )
        }
    }

    override suspend fun updateTicketStatus(
        id: String,
        status: TicketStatus,
        updatedAt: String,
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            database.supportTicketQueries.updateStatus(
                status = status.rawValue,
                updatedAt = updatedAt,
                id = id,
            )
        }
    }

    override suspend fun countOpenTicketsByUser(userId: String): Result<Long> = runCatching {
        withContext(ioDispatcher) {
            database.supportTicketQueries.countOpenByUser(userId).executeAsOne()
        }
    }

    private fun SupportTicketRow.toDomain(): SupportTicket = SupportTicket(
        id = id,
        userId = userId,
        userEmail = userEmail,
        category = SupportCategory.fromRaw(category),
        subject = subject,
        description = description,
        status = TicketStatus.fromRaw(status),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
