package com.ledgerhub.presentation.dashboard

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests QA (Skill 2) du chargement des indicateurs du tableau de bord. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private fun viewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher, delay: Long = 500L) = DashboardViewModel(
        invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = delay),
        creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = delay),
        quoteRepository = MockQuoteRepository(simulatedDelayMillis = delay),
        dispatcher = dispatcher,
    )

    @Test
    fun loadDashboard_transitionsThroughLoadingToPopulatedAnalytics() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher)

        runCurrent()
        assertTrue(vm.uiState.value.isLoading)

        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        // Le mock pré-remplit plusieurs factures Payées sur 6 mois distincts (voir MockInvoiceRepository).
        assertTrue(state.collectedRevenueCents > 0)
        assertTrue(state.pendingRevenueCents > 0)
        assertEquals(0L, state.overdueRevenueCents) // aucune date d'échéance en v1 — voir DashboardAnalytics.kt
        assertEquals(6, state.monthlyRevenue.size)
        assertEquals(3, state.recentDocuments.size)
    }

    @Test
    fun loadFailure_surfacesErrorMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = DashboardViewModel(
            invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = 0L, simulateFailure = true),
            creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 0L),
            quoteRepository = MockQuoteRepository(simulatedDelayMillis = 0L),
            dispatcher = dispatcher,
        )

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadErrorMessage != null)
        assertEquals(null, state.analytics)
    }
}
