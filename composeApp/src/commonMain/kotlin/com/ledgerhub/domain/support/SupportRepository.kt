package com.ledgerhub.domain.support

/**
 * Contrat d'accès aux tickets d'assistance et questions réglementaires (US-30).
 */
interface SupportRepository {
    suspend fun getAllTickets(): Result<List<SupportTicket>>
    suspend fun getTicketsByUser(userId: String): Result<List<SupportTicket>>
    suspend fun getTicketById(id: String): Result<SupportTicket?>
    suspend fun createTicket(ticket: SupportTicket): Result<Unit>
    suspend fun updateTicketStatus(id: String, status: TicketStatus, updatedAt: String): Result<Unit>
    suspend fun countOpenTicketsByUser(userId: String): Result<Long>
}
