package com.ledgerhub.presentation.degraded

import com.ledgerhub.domain.degraded.DegradedChannel
import com.ledgerhub.domain.degraded.ProcessSyncQueueBatchUseCase
import com.ledgerhub.domain.degraded.SyncBatchResult
import com.ledgerhub.domain.degraded.SyncQueueEntry
import com.ledgerhub.domain.degraded.SyncQueueRepository
import com.ledgerhub.domain.degraded.SyncStatus
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.InvoiceStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncQueueViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
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

        override suspend fun getAllEntries(): Result<List<SyncQueueEntry>> {
            return Result.success(entries.values.toList())
        }

        override suspend fun remove(invoiceId: String): Result<Unit> {
            entries.values.removeAll { it.invoiceId == invoiceId }
            return Result.success(Unit)
        }
    }

    private class FakeInvoiceStatusRepository : InvoiceStatusRepository {
        override suspend fun changeStatus(
            invoiceNumber: String,
            from: InvoiceStatus,
            to: InvoiceStatus,
            reason: String?,
        ): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun initialState_loadsPendingCount() = runTest(testDispatcher) {
        val syncRepo = FakeSyncQueueRepository()
        syncRepo.enqueue(
            SyncQueueEntry(
                id = "1",
                invoiceId = "FAC-001",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T10:00:00Z",
            )
        )
        syncRepo.enqueue(
            SyncQueueEntry(
                id = "2",
                invoiceId = "FAC-002",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T11:00:00Z",
            )
        )

        val useCase = ProcessSyncQueueBatchUseCase(syncRepo, FakeInvoiceStatusRepository())
        val viewModel = SyncQueueViewModel(syncRepo, useCase, testDispatcher)

        testScheduler.advanceUntilIdle()

        assertEquals(2L, viewModel.uiState.value.pendingCount)
        assertFalse(viewModel.uiState.value.isSyncing)
    }

    @Test
    fun processBatch_updatesStateAndTriggersOnComplete() = runTest(testDispatcher) {
        val syncRepo = FakeSyncQueueRepository()
        syncRepo.enqueue(
            SyncQueueEntry(
                id = "1",
                invoiceId = "FAC-001",
                syncStatus = SyncStatus.PENDING,
                createdAt = "2026-09-12T10:00:00Z",
            )
        )

        val useCase = ProcessSyncQueueBatchUseCase(syncRepo, FakeInvoiceStatusRepository())
        val viewModel = SyncQueueViewModel(syncRepo, useCase, testDispatcher)
        testScheduler.advanceUntilIdle()

        var completed = false
        viewModel.processBatch(onComplete = { completed = true })

        testScheduler.advanceUntilIdle()

        assertTrue(completed)
        assertFalse(viewModel.uiState.value.isSyncing)
        assertEquals(0L, viewModel.uiState.value.pendingCount)
        assertNotNull(viewModel.uiState.value.lastSyncResult)
        assertEquals(1, viewModel.uiState.value.lastSyncResult?.successCount)
    }
}
