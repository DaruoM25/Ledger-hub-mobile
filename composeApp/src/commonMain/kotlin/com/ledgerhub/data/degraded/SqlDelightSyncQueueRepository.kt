package com.ledgerhub.data.degraded

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.SyncQueue as SyncQueueRow
import com.ledgerhub.domain.degraded.DegradedChannel
import com.ledgerhub.domain.degraded.SyncQueueEntry
import com.ledgerhub.domain.degraded.SyncQueueRepository
import com.ledgerhub.domain.degraded.SyncStatus

/**
 * Implémentation SQLDelight de [SyncQueueRepository] pour la gestion locale de la file de régularisation (US-29).
 */
class SqlDelightSyncQueueRepository(
    private val database: LedgerHubDatabase,
) : SyncQueueRepository {

    override suspend fun getPendingEntries(): Result<List<SyncQueueEntry>> = runCatching {
        database.syncQueueQueries.selectPending().executeAsList().map { it.toDomain() }
    }

    override suspend fun getAllEntries(): Result<List<SyncQueueEntry>> = runCatching {
        database.syncQueueQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun getEntryByInvoiceId(invoiceId: String): Result<SyncQueueEntry?> = runCatching {
        database.syncQueueQueries.selectByInvoiceId(invoiceId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun countPending(): Result<Long> = runCatching {
        database.syncQueueQueries.countPending().executeAsOne()
    }

    override suspend fun enqueue(entry: SyncQueueEntry): Result<Unit> = runCatching {
        database.syncQueueQueries.insertOrReplace(
            id = entry.id,
            invoiceId = entry.invoiceId,
            syncStatus = entry.syncStatus.rawValue,
            retryCount = entry.retryCount,
            lastError = entry.lastError,
            originalChannel = entry.originalChannel.rawValue,
            createdAt = entry.createdAt,
            syncedAt = entry.syncedAt,
        )
    }

    override suspend fun updateStatus(
        id: String,
        status: SyncStatus,
        lastError: String?,
        syncedAt: String?,
    ): Result<Unit> = runCatching {
        database.syncQueueQueries.updateStatus(
            syncStatus = status.rawValue,
            lastError = lastError,
            syncedAt = syncedAt,
            id = id,
        )
    }

    override suspend fun remove(invoiceId: String): Result<Unit> = runCatching {
        database.syncQueueQueries.deleteByInvoiceId(invoiceId)
    }

    private fun SyncQueueRow.toDomain(): SyncQueueEntry = SyncQueueEntry(
        id = id,
        invoiceId = invoiceId,
        syncStatus = SyncStatus.fromRaw(syncStatus),
        retryCount = retryCount,
        lastError = lastError,
        originalChannel = DegradedChannel.fromRaw(originalChannel),
        createdAt = createdAt,
        syncedAt = syncedAt,
    )
}
