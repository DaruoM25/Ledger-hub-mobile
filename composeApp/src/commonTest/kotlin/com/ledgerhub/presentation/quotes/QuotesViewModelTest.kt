package com.ledgerhub.presentation.quotes

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import com.ledgerhub.domain.quote.QuoteStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests QA (Skill 2) du chargement de la liste des devis et de la cinématique de conversion. */
@OptIn(ExperimentalCoroutinesApi::class)
class QuotesViewModelTest {

    private fun viewModel(
        quoteDelay: Long = 500L,
        invoiceDelay: Long = 500L,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = QuotesViewModel(
        quoteRepository = MockQuoteRepository(simulatedDelayMillis = quoteDelay),
        convertQuoteToInvoiceUseCase = ConvertQuoteToInvoiceUseCase(),
        submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = invoiceDelay)),
        dispatcher = dispatcher,
    )

    // ── Chargement de la liste ────────────────────────────────────────────────

    @Test
    fun loadQuotes_transitionsThroughLoadingToPopulatedList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher = dispatcher)

        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.quotes.isEmpty())

        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.quotes.isNotEmpty())
        // Le mock pré-remplit un devis par statut, dont un Accepté (base du test de conversion).
        assertTrue(state.quotes.any { it.status == QuoteStatus.ACCEPTED })
        assertTrue(state.quotes.any { it.status == QuoteStatus.DRAFT })
    }

    // ── "Le bouton magique" : conversion d'un devis Accepté ──────────────────

    @Test
    fun convertAcceptedQuote_transitionsThroughLoadingToConvertedInvoice() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()
        val accepted = vm.uiState.value.quotes.first { it.status == QuoteStatus.ACCEPTED }

        vm.processIntent(QuotesIntent.ConvertToInvoice(accepted.number))

        runCurrent()
        assertEquals(accepted.number, vm.uiState.value.convertingQuoteNumber)
        assertNull(vm.uiState.value.invoiceFor(accepted))

        advanceUntilIdle()
        val state = vm.uiState.value
        assertNull(state.convertingQuoteNumber)
        val invoice = state.invoiceFor(accepted)
        assertNotNull(invoice)
        assertEquals(accepted.number, invoice.sourceQuoteId)
        assertEquals(accepted.lines.size, invoice.lines.size)
    }

    // ── Règle métier : la conversion est un no-op hors statut Accepté ────────

    @Test
    fun convertNonAcceptedQuote_isNoOp() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher = dispatcher)
        advanceUntilIdle()
        val draft = vm.uiState.value.quotes.first { it.status == QuoteStatus.DRAFT }

        vm.processIntent(QuotesIntent.ConvertToInvoice(draft.number))
        advanceUntilIdle()

        assertNull(vm.uiState.value.convertingQuoteNumber)
        assertNull(vm.uiState.value.invoiceFor(draft))
    }
}
