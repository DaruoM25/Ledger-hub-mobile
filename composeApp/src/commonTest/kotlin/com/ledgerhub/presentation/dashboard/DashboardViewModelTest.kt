package com.ledgerhub.presentation.dashboard

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.time.FixedClock
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

    /**
     * Horloge figée : les relances de devis se mesurent en jours avant échéance, elles ne
     * peuvent pas dépendre de la date d'exécution de la suite.
     * Le jeu de `MockQuoteRepository` fixe toutes les validités au 2026-09-01, soit J+2 ici.
     */
    private val clock = FixedClock("2026-08-30T09:00:00Z")

    private fun viewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher, delay: Long = 500L) = DashboardViewModel(
        invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = delay),
        creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = delay),
        quoteRepository = MockQuoteRepository(simulatedDelayMillis = delay),
        dispatcher = dispatcher,
        clock = clock,
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
        assertEquals(0L, state.overdueRevenueCents) // aucun calcul de retard câblé — voir DashboardAnalytics.kt
        assertEquals(8, state.issuedCount) // MockInvoiceRepository sème 8 factures (F-2026-101..108)
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

    // ── Activité commerciale des devis (US-12) ──────────────────────────────

    @Test
    fun loadDashboard_exposesThePendingQuotesKpi() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher, delay = 0L)

        advanceUntilIdle()

        val state = vm.uiState.value
        // MockQuoteRepository sème un seul devis Envoyé (DEV-2026-002) : 2 × 50,00 HT.
        // Le KPI cumule le HT (100,00 €), pas le TTC (120,00 €).
        assertEquals(1, state.pendingQuotesCount)
        assertEquals(10_000L, state.pendingQuotesTotalCents)
    }

    @Test
    fun loadDashboard_exposesTheQuotesToFollowUp_withTheirRemainingDays() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher, delay = 0L)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hasQuotesToFollowUp)
        val item = state.quotesToFollowUp.single()
        assertEquals("DEV-2026-002", item.number)
        assertEquals("2026-09-01", item.validityDate)
        // Horloge figée au 2026-08-30 : il reste deux jours.
        assertEquals(2, item.daysRemaining)
        assertFalse(item.isExpired)
    }

    @Test
    fun beforeLoading_theQuoteProjectionsAreNeutral() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = viewModel(dispatcher)

        runCurrent()

        val state = vm.uiState.value
        assertEquals(0L, state.pendingQuotesTotalCents)
        assertFalse(state.hasQuotesToFollowUp)
    }
}
