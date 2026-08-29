package com.ledgerhub.presentation.invoicedetail

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceDetailViewModelTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun invoice(status: InvoiceStatus) = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL)),
        status = status,
    )

    @Test
    fun draftInvoice_canNotCreateCreditNote() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = InvoiceDetailViewModel(invoice(InvoiceStatus.DRAFT), dispatcher = dispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canCreateCreditNote)
    }

    @Test
    fun validatedInvoice_withoutExistingCreditNote_canCreateCreditNote() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = InvoiceDetailViewModel(invoice(InvoiceStatus.DEPOSITED), dispatcher = dispatcher)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canCreateCreditNote)
    }

    @Test
    fun validatedInvoice_withExistingCreditNote_canNotCreateCreditNoteAgain() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = invoice(InvoiceStatus.DEPOSITED)
        val repository = MockCreditNoteRepository(simulatedDelayMillis = 0L)
        val existingCreditNote = CreditNote(
            number = "AV-2026-0001",
            issueDate = "2026-08-01",
            invoiceId = source.number,
            originalInvoiceDate = source.issueDate,
            reason = "Erreur tarifaire",
            lines = emptyList(),
            issuer = issuer,
            recipient = recipient,
            totalHt = Money(-10000),
            totalVat = Money(-2000),
            totalTtc = Money(-12000),
        )

        val viewModel = InvoiceDetailViewModel(source, creditNoteRepository = repository, dispatcher = dispatcher)
        advanceUntilIdle()
        // L'avoir est soumis après la construction du ViewModel — on force un second rafraîchissement
        // en reconstruisant un ViewModel une fois l'avoir déjà en base (scénario réel : l'utilisateur
        // rouvre le détail de la facture après avoir déjà généré un avoir).
        repository.submitCreditNote(existingCreditNote)

        val viewModelAfter = InvoiceDetailViewModel(source, creditNoteRepository = repository, dispatcher = dispatcher)
        advanceUntilIdle()

        assertFalse(viewModelAfter.uiState.value.canCreateCreditNote)
        assertTrue(viewModelAfter.uiState.value.hasCreditNote)
    }
}
