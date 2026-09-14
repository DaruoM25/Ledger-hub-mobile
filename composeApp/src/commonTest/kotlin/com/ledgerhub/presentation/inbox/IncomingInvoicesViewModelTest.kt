package com.ledgerhub.presentation.inbox

import com.ledgerhub.domain.inbox.InboxRepository
import com.ledgerhub.domain.inbox.ProcessReceivedInvoiceUseCase
import com.ledgerhub.domain.inbox.ReceivedInvoice
import com.ledgerhub.domain.inbox.ReceivedInvoiceStatus
import com.ledgerhub.domain.invoice.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class IncomingInvoicesViewModelTest {

    private class FakeInboxRepository : InboxRepository {
        val invoices = mutableListOf<ReceivedInvoice>()

        override suspend fun saveReceivedInvoice(invoice: ReceivedInvoice): Result<Unit> {
            invoices.removeAll { it.id == invoice.id }
            invoices.add(invoice)
            return Result.success(Unit)
        }

        override suspend fun fetchReceivedInvoices(): Result<List<ReceivedInvoice>> = Result.success(invoices.toList())

        override suspend fun getReceivedInvoiceById(id: String): Result<ReceivedInvoice?> {
            return Result.success(invoices.find { it.id == id })
        }

        override suspend fun findByFileHash(fileHash: String): Result<ReceivedInvoice?> {
            return Result.success(invoices.find { it.fileHash == fileHash })
        }

        override suspend fun findDuplicateTriptych(
            supplierSiren: String,
            invoiceNumber: String,
            totalTtcCents: Long,
            excludeId: String,
        ): Result<ReceivedInvoice?> {
            return Result.success(
                invoices.find {
                    it.supplierSiren == supplierSiren &&
                        it.invoiceNumber == invoiceNumber &&
                        it.totalTtc.cents == totalTtcCents &&
                        it.id != excludeId
                }
            )
        }

        override suspend fun updateStatus(
            id: String,
            status: ReceivedInvoiceStatus,
            duplicateReason: String?,
        ): Result<Unit> {
            val idx = invoices.indexOfFirst { it.id == id }
            if (idx != -1) {
                val current = invoices[idx]
                invoices[idx] = current.copy(status = status, duplicateReason = duplicateReason)
            }
            return Result.success(Unit)
        }
    }

    @Test
    fun loadInitialState_calculatesKpisAndFilteredList() = runTest {
        val repo = FakeInboxRepository()
        val useCase = ProcessReceivedInvoiceUseCase(repo)
        val dispatcher = UnconfinedTestDispatcher()

        val sample1 = ReceivedInvoice(
            id = "REC-01",
            supplierName = "Fournisseur A",
            supplierSiren = "111222333",
            supplierSiret = "11122233300014",
            invoiceNumber = "F-01",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_00),
            totalVat = Money(20_00),
            totalTtc = Money(120_00),
            fileHash = "hash1",
            status = ReceivedInvoiceStatus.RECEIVED,
            receivedAt = "2026-09-14T10:00:00",
        )
        val sample2 = ReceivedInvoice(
            id = "REC-02",
            supplierName = "Fournisseur B",
            supplierSiren = "444555666",
            supplierSiret = "44455566600018",
            invoiceNumber = "F-02",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(50_00),
            totalVat = Money(10_00),
            totalTtc = Money(60_00),
            fileHash = "hash2",
            status = ReceivedInvoiceStatus.DUPLICATE_ALERT,
            duplicateReason = "Doublon détecté",
            receivedAt = "2026-09-14T10:30:00",
        )
        repo.saveReceivedInvoice(sample1)
        repo.saveReceivedInvoice(sample2)

        val viewModel = IncomingInvoicesViewModel(repo, useCase, dispatcher)
        val state = viewModel.uiState.value

        assertEquals(2, state.totalReceivedCount)
        assertEquals(1, state.totalAlertsCount)
        assertEquals(0, state.totalApprovedCount)
        assertEquals(2, state.filteredInvoices.size)

        // Filtrer sur ALERTS_ONLY
        viewModel.processIntent(IncomingInvoicesIntent.SelectTab(IncomingInvoicesFilterTab.ALERTS_ONLY))
        assertEquals(1, viewModel.uiState.value.filteredInvoices.size)
        assertEquals("REC-02", viewModel.uiState.value.filteredInvoices.first().id)
    }

    @Test
    fun approveAndReject_updatesStateProperly() = runTest {
        val repo = FakeInboxRepository()
        val useCase = ProcessReceivedInvoiceUseCase(repo)
        val dispatcher = UnconfinedTestDispatcher()

        val invoice = ReceivedInvoice(
            id = "REC-01",
            supplierName = "Fournisseur A",
            supplierSiren = "111222333",
            supplierSiret = "11122233300014",
            invoiceNumber = "F-01",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(100_00),
            totalVat = Money(20_00),
            totalTtc = Money(120_00),
            fileHash = "hash1",
            status = ReceivedInvoiceStatus.RECEIVED,
            receivedAt = "2026-09-14T10:00:00",
        )
        repo.saveReceivedInvoice(invoice)

        val viewModel = IncomingInvoicesViewModel(repo, useCase, dispatcher)
        viewModel.processIntent(IncomingInvoicesIntent.ApproveInvoice("REC-01"))

        assertEquals(1, viewModel.uiState.value.totalApprovedCount)
        assertEquals(ReceivedInvoiceStatus.APPROVED, repo.invoices.first().status)
        assertNotNull(viewModel.uiState.value.infoMessage)

        viewModel.processIntent(IncomingInvoicesIntent.DismissMessage)
        assertNull(viewModel.uiState.value.infoMessage)
    }
}
