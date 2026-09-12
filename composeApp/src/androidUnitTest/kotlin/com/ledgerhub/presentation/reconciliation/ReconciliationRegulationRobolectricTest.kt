package com.ledgerhub.presentation.reconciliation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.BankTransactionRepository
import com.ledgerhub.domain.reconciliation.ReconciliationMatch
import com.ledgerhub.domain.reconciliation.ReconciliationRepository
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class ReconciliationRegulationRobolectricTest {

    private class FakeBankRepo(private val list: List<BankTransaction>) : BankTransactionRepository {
        override suspend fun fetchTransactions(): Result<List<BankTransaction>> = Result.success(list)
    }

    private class FakeInvoiceRepo(private val list: List<Invoice>) : InvoiceRepository {
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(list)
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
    }

    private class FakeReconciliationRepo : ReconciliationRepository {
        val matches = mutableListOf<ReconciliationMatch>()
        override suspend fun fetchMatches(): Result<List<ReconciliationMatch>> = Result.success(matches)
        override suspend fun reconcile(match: ReconciliationMatch, fromStatus: String): Result<Unit> {
            matches.add(match)
            return Result.success(Unit)
        }
    }

    @Test
    fun testReconciliation_matchingServiceInvoice_displaysEreportingReadyBanner() = runComposeUiTest {
        val tx = BankTransaction(
            id = "TX-001",
            label = "Virement Client",
            amount = Money(120_00),
            valueDateIso = "2026-09-12",
        )
        val invoice = Invoice(
            number = "FAC-2026-0100",
            issueDate = "2026-09-01",
            issuer = Party("Moi", "123456789", "12345678900012"),
            recipient = Party("Client", "987654321", "98765432100034"),
            lines = listOf(InvoiceLine("Prestation Service", 1, Money(100_00), VatRate.TAUX_NORMAL)),
            status = InvoiceStatus.DEPOSITED,
            natureOperation = NatureOperation.PRESTATION_SERVICES,
        )

        val bankRepo = FakeBankRepo(listOf(tx))
        val invoiceRepo = FakeInvoiceRepo(listOf(invoice))
        val reconciliationRepo = FakeReconciliationRepo()

        val viewModel = ReconciliationViewModel(
            invoiceRepository = invoiceRepo,
            bankTransactionRepository = bankRepo,
            reconciliationRepository = reconciliationRepo,
        )

        setContent {
            LedgerHubTheme {
                ReconciliationScreen(viewModel = viewModel)
            }
        }

        // Sélection transaction
        onNodeWithTag(ReconciliationTags.transactionCard(tx.id))
            .assertIsDisplayed()
            .performClick()

        // Bascule onglet factures si mode compact
        onNodeWithTag(ReconciliationTags.TAB_INVOICES)
            .performClick()

        // Sélection facture
        onNodeWithTag(ReconciliationTags.invoiceCard(invoice.number))
            .assertIsDisplayed()
            .performClick()

        // Clic sur le bouton de lettrage
        onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON)
            .assertIsDisplayed()
            .performClick()

        waitForIdle()

        // Vérification de l'apparition de la bannière e-Reporting
        onNodeWithTag(ReconciliationTags.RECONCILIATION_EREPORTING_BANNER)
            .assertIsDisplayed()
    }
}
