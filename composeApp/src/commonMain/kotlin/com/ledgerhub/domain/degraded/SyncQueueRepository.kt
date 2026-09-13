package com.ledgerhub.domain.degraded

/**
 * Contrat de persistance de la file de synchronisation du mode dégradé (US-29).
 */
interface SyncQueueRepository {
    suspend fun getPendingEntries(): Result<List<SyncQueueEntry>>
    suspend fun getAllEntries(): Result<List<SyncQueueEntry>>
    suspend fun getEntryByInvoiceId(invoiceId: String): Result<SyncQueueEntry?>
    suspend fun countPending(): Result<Long>
    suspend fun enqueue(entry: SyncQueueEntry): Result<Unit>
    suspend fun updateStatus(
        id: String,
        status: SyncStatus,
        lastError: String? = null,
        syncedAt: String? = null,
    ): Result<Unit>
    suspend fun remove(invoiceId: String): Result<Unit>
}
