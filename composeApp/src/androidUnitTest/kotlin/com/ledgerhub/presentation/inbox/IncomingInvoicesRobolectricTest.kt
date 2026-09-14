package com.ledgerhub.presentation.inbox

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.inbox.InboxRepository
import com.ledgerhub.domain.inbox.ProcessReceivedInvoiceUseCase
import com.ledgerhub.domain.inbox.ReceivedInvoice
import com.ledgerhub.domain.inbox.ReceivedInvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class IncomingInvoicesRobolectricTest {

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
    fun incomingInvoices_rendersKpisTabsAndDuplicateBanner() = runComposeUiTest {
        val repo = FakeInboxRepository()
        val sampleAlert = ReceivedInvoice(
            id = "REC-ALERT-01",
            supplierName = "Fournisseur Suspect",
            supplierSiren = "111222333",
            supplierSiret = "11122233300014",
            invoiceNumber = "DOUBLON-99",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(200_00),
            totalVat = Money(40_00),
            totalTtc = Money(240_00),
            fileHash = "hash-alert",
            status = ReceivedInvoiceStatus.DUPLICATE_ALERT,
            duplicateReason = "Doublon détecté avec la facture F-01",
            receivedAt = "2026-09-14T10:00:00",
        )
        repo.invoices.add(sampleAlert)

        val testDispatcher = UnconfinedTestDispatcher()
        val useCase = ProcessReceivedInvoiceUseCase(repo)
        val viewModel = IncomingInvoicesViewModel(repo, useCase, testDispatcher)

        setContent {
            LedgerHubTheme {
                IncomingInvoicesScreen(viewModel = viewModel)
            }
        }

        onNodeWithTag(IncomingInvoicesTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.DUPLICATE_BANNER).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.KPI_RECEIVED).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.KPI_ALERTS).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.KPI_APPROVED).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.TAB_ALL).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.TAB_ALERTS).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.TAB_APPROVED).assertIsDisplayed()
        onNodeWithTag(IncomingInvoicesTags.INVOICE_LIST).assertExists()
        onNodeWithTag(IncomingInvoicesTags.invoiceCard("REC-ALERT-01")).assertExists()
    }
}
