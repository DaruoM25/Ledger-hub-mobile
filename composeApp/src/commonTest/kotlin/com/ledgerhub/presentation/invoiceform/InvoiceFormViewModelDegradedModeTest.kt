package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.degraded.DegradedChannel
import com.ledgerhub.domain.degraded.DegradedModeNetworkState
import com.ledgerhub.domain.degraded.EnqueueDegradedInvoiceUseCase
import com.ledgerhub.domain.degraded.SyncQueueEntry
import com.ledgerhub.domain.degraded.SyncQueueRepository
import com.ledgerhub.domain.degraded.SyncStatus
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.VatRate
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceFormViewModelDegradedModeTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeInvoiceRepository : InvoiceRepository {
        val invoices = mutableMapOf<String, Invoice>()

        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> {
            invoices[invoice.number] = invoice
            return Result.success(Unit)
        }

        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices.values.toList())
    }

    private class FakeSyncQueueRepository : SyncQueueRepository {
        val entries = mutableMapOf<String, SyncQueueEntry>()

        override suspend fun enqueue(entry: SyncQueueEntry): Result<Unit> {
            entries[entry.invoiceId] = entry
            return Result.success(Unit)
        }

        override suspend fun getPendingEntries(): Result<List<SyncQueueEntry>> =
            Result.success(entries.values.filter { it.syncStatus == SyncStatus.PENDING })

        override suspend fun getEntryByInvoiceId(invoiceId: String): Result<SyncQueueEntry?> =
            Result.success(entries[invoiceId])

        override suspend fun updateStatus(id: String, status: SyncStatus, lastError: String?, syncedAt: String?): Result<Unit> =
            Result.success(Unit)

        override suspend fun countPending(): Result<Long> =
            Result.success(entries.values.count { it.syncStatus == SyncStatus.PENDING }.toLong())

        override suspend fun getAllEntries(): Result<List<SyncQueueEntry>> =
            Result.success(entries.values.toList())

        override suspend fun remove(invoiceId: String): Result<Unit> {
            entries.remove(invoiceId)
            return Result.success(Unit)
        }
    }

    @Test
    fun submittingInDegradedMode_enqueuesWithPendingRegularizationStatus() = runTest(testDispatcher) {
        val invoiceRepo = FakeInvoiceRepository()
        val syncRepo = FakeSyncQueueRepository()
        val enqueueUseCase = EnqueueDegradedInvoiceUseCase(invoiceRepo, syncRepo)

        val viewModel = InvoiceFormViewModel(
            submitInvoiceUseCase = SubmitInvoiceUseCase(invoiceRepo),
            dispatcher = testDispatcher,
            enqueueDegradedInvoiceUseCase = enqueueUseCase,
            initialNetworkState = DegradedModeNetworkState.OUTAGE,
        )

        // Remplir un formulaire valide
        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-OUTAGE-01"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-09-12"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-10-12"))
        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Client Test Degrade"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("78410233600021"))
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("contact@client-test.fr"))
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(
                index = 0,
                label = "Prestation informatique secours",
                quantity = "1",
                unitPriceHt = "500",
                vatRate = VatRate.TAUX_NORMAL,
            )
        )

        // Émission de la facture
        viewModel.processIntent(InvoiceFormIntent.ValidateAndIssue)
        testScheduler.advanceUntilIdle()

        // 1. Succès de la soumission
        assertIs<SubmissionStatus.Success>(viewModel.uiState.value.submissionStatus)

        // 2. Facture soumise avec statut PENDING_REGULARIZATION
        val submitted = viewModel.uiState.value.submittedInvoice
        assertEquals("FAC-2026-OUTAGE-01", submitted?.number)
        assertEquals(InvoiceStatus.PENDING_REGULARIZATION, submitted?.status)

        // 3. Présente dans la SyncQueue
        val queued = syncRepo.getEntryByInvoiceId("FAC-2026-OUTAGE-01").getOrNull()
        assertEquals("FAC-2026-OUTAGE-01", queued?.invoiceId)
        assertEquals(SyncStatus.PENDING, queued?.syncStatus)
    }
}
