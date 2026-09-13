package com.ledgerhub.domain.degraded

import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.InvoiceStatusRepository
import com.ledgerhub.domain.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessSyncQueueBatchUseCaseTest {

    private class FakeInvoiceStatusRepository : InvoiceStatusRepository {
        val statusChanges = mutableListOf<StatusChange>()
        var failureForInvoice: String? = null

        data class StatusChange(
            val number: String,
            val from: InvoiceStatus,
            val to: InvoiceStatus,
            val reason: String?,
        )

        override suspend fun changeStatus(
            invoiceNumber: String,
            from: InvoiceStatus,
            to: InvoiceStatus,
            reason: String?,
        ): Result<Unit> {
            if (invoiceNumber == failureForInvoice) {
                return Result.failure(IllegalStateException("Simulated status change failure"))
            }
            statusChanges.add(StatusChange(invoiceNumber, from, to, reason))
            return Result.success(Unit)
        }
    }

    private class FakeSyncQueueRepository : SyncQueueRepository {
        val entries = mutableMapOf<String, SyncQueueEntry>()

        override suspend fun enqueue(entry: SyncQueueEntry): Result<Unit> {
            entries[entry.id] = entry
            return Result.success(Unit)
        }

        override suspend fun getPendingEntries(): Result<List<SyncQueueEntry>> {
            return Result.success(entries.values.filter { it.syncStatus == SyncStatus.PENDING })
        }

        override suspend fun getAllEntries(): Result<List<SyncQueueEntry>> {
            return Result.success(entries.values.toList())
        }

        override suspend fun getEntryByInvoiceId(invoiceId: String): Result<SyncQueueEntry?> {
            return Result.success(entries.values.firstOrNull { it.invoiceId == invoiceId })
        }

        override suspend fun updateStatus(
            id: String,
            status: SyncStatus,
            lastError: String?,
            syncedAt: String?,
        ): Result<Unit> {
            val entry = entries[id] ?: return Result.failure(NoSuchElementException())
            entries[id] = entry.copy(
                syncStatus = status,
                lastError = lastError,
                syncedAt = syncedAt,
            )
            return Result.success(Unit)
        }

        override suspend fun countPending(): Result<Long> {
            return Result.success(entries.values.count { it.syncStatus == SyncStatus.PENDING }.toLong())
        }

        override suspend fun remove(invoiceId: String): Result<Unit> {
            entries.values.removeAll { it.invoiceId == invoiceId }
            return Result.success(Unit)
        }
    }

    private class FakeClock(private val fixedIso: String = "2026-09-12T20:00:00Z") : Clock {
        override fun nowIso(): String = fixedIso
        override fun nowEpochMillis(): Long = 1789243200000L
    }

    @Test
    fun processSyncQueueBatch_allSuccess_transitionsAllToDepositedAndMarksSynced() = runTest {
        val syncRepo = FakeSyncQueueRepository()
        val invoiceStatusRepo = FakeInvoiceStatusRepository()
        val clock = FakeClock("2026-09-12T20:00:00Z")
        val useCase = ProcessSyncQueueBatchUseCase(syncRepo, invoiceStatusRepo, clock)

        syncRepo.enqueue(
            SyncQueueEntry(
                id = "1",
                invoiceId = "FAC-2026-001",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T10:00:00Z",
            )
        )
        syncRepo.enqueue(
            SyncQueueEntry(
                id = "2",
                invoiceId = "FAC-2026-002",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T11:00:00Z",
            )
        )

        val result = useCase()
        assertTrue(result.isSuccess)
        val batchResult = result.getOrThrow()

        assertEquals(2, batchResult.totalProcessed)
        assertEquals(2, batchResult.successCount)
        assertEquals(0, batchResult.failureCount)
        assertEquals(listOf("FAC-2026-001", "FAC-2026-002"), batchResult.syncedInvoiceNumbers)

        // Statuts passés à DEPOSITED
        assertEquals(2, invoiceStatusRepo.statusChanges.size)
        assertEquals(InvoiceStatus.PENDING_REGULARIZATION, invoiceStatusRepo.statusChanges[0].from)
        assertEquals(InvoiceStatus.DEPOSITED, invoiceStatusRepo.statusChanges[0].to)

        // Queue entries marquées comme SYNCED
        val entry1 = syncRepo.entries["1"]
        val entry2 = syncRepo.entries["2"]
        assertEquals(SyncStatus.SYNCED, entry1?.syncStatus)
        assertEquals("2026-09-12T20:00:00Z", entry1?.syncedAt)
        assertEquals(SyncStatus.SYNCED, entry2?.syncStatus)
        assertEquals("2026-09-12T20:00:00Z", entry2?.syncedAt)
    }

    @Test
    fun processSyncQueueBatch_withFailure_recordsFailureAndMarksFailed() = runTest {
        val syncRepo = FakeSyncQueueRepository()
        val invoiceStatusRepo = FakeInvoiceStatusRepository()
        invoiceStatusRepo.failureForInvoice = "FAC-2026-FAIL"
        val useCase = ProcessSyncQueueBatchUseCase(syncRepo, invoiceStatusRepo)

        syncRepo.enqueue(
            SyncQueueEntry(
                id = "1",
                invoiceId = "FAC-2026-OK",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T10:00:00Z",
            )
        )
        syncRepo.enqueue(
            SyncQueueEntry(
                id = "2",
                invoiceId = "FAC-2026-FAIL",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T11:00:00Z",
            )
        )

        val result = useCase()
        assertTrue(result.isSuccess)
        val batchResult = result.getOrThrow()

        assertEquals(2, batchResult.totalProcessed)
        assertEquals(1, batchResult.successCount)
        assertEquals(1, batchResult.failureCount)

        assertEquals(SyncStatus.SYNCED, syncRepo.entries["1"]?.syncStatus)
        assertEquals(SyncStatus.FAILED, syncRepo.entries["2"]?.syncStatus)
        assertEquals("Simulated status change failure", syncRepo.entries["2"]?.lastError)
    }
}
