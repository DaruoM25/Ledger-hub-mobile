package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.invoice.InvoiceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Tests QA (Skill 2) du cycle de vie de l'écran liste des factures (US-02). */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceListViewModelTest {

    private val sampleInvoices = listOf(
        testInvoice("F-2026-001", InvoiceStatus.PAID, issueDate = "2026-02-01"),
        testInvoice("F-2026-002", InvoiceStatus.PAID, issueDate = "2026-05-15"),
        testInvoice("F-2026-003", InvoiceStatus.DEPOSITED, issueDate = "2026-03-10"),
        testInvoice("F-2026-004", InvoiceStatus.DRAFT, issueDate = "2026-07-20"),
    )

    @Test
    fun initialLoad_transitionsThroughLoadingToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLedgerRepository(Result.success(sampleInvoices), delayMillis = 500L)
        val viewModel = InvoiceListViewModel(ledgerRepository = repository, dispatcher = dispatcher)

        runCurrent()
        assertIs<InvoiceListContent.Loading>(viewModel.uiState.value.content)

        advanceUntilIdle()
        val content = viewModel.uiState.value.content
        assertIs<InvoiceListContent.Success>(content)
        assertEquals(4, content.invoices.size)
        assertTrue(viewModel.uiState.value.errorMessage == null)
    }

    @Test
    fun load_whenRepositoryFails_exposesErrorContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLedgerRepository(Result.failure(RuntimeException("boom")))
        val viewModel = InvoiceListViewModel(ledgerRepository = repository, dispatcher = dispatcher)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<InvoiceListContent.Error>(state.content)
        assertTrue(state.errorMessage != null)
    }

    @Test
    fun load_whenListEmpty_contentIsEmpty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = InvoiceListViewModel(
            ledgerRepository = FakeLedgerRepository(Result.success(emptyList())),
            dispatcher = dispatcher,
        )

        advanceUntilIdle()

        assertIs<InvoiceListContent.Empty>(viewModel.uiState.value.content)
    }

    @Test
    fun filterSelected_restrictsVisibleInvoices_withoutReloading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLedgerRepository(Result.success(sampleInvoices))
        val viewModel = InvoiceListViewModel(ledgerRepository = repository, dispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.processIntent(InvoiceListIntent.FilterSelected(InvoiceStatusFilter.PAID))

        val visible = viewModel.uiState.value.visibleInvoices
        assertEquals(listOf("F-2026-002", "F-2026-001"), visible.map { it.number })
        assertEquals(1, repository.fetchInvoicesCallCount)
    }

    @Test
    fun visibleInvoices_areSortedByIssueDateDescending() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = InvoiceListViewModel(
            ledgerRepository = FakeLedgerRepository(Result.success(sampleInvoices)),
            dispatcher = dispatcher,
        )

        advanceUntilIdle()

        assertEquals(
            listOf("F-2026-004", "F-2026-002", "F-2026-003", "F-2026-001"),
            viewModel.uiState.value.visibleInvoices.map { it.number },
        )
    }

    @Test
    fun counts_reflectStatusDistribution() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = InvoiceListViewModel(
            ledgerRepository = FakeLedgerRepository(Result.success(sampleInvoices)),
            dispatcher = dispatcher,
        )

        advanceUntilIdle()

        val counts = viewModel.uiState.value.counts
        assertEquals(4, counts[InvoiceStatusFilter.TOUTES])
        assertEquals(2, counts[InvoiceStatusFilter.PAID])
        assertEquals(1, counts[InvoiceStatusFilter.DEPOSITED])
        assertEquals(1, counts[InvoiceStatusFilter.DRAFT])
        assertEquals(0, counts[InvoiceStatusFilter.CANCELLED])
    }

    @Test
    fun retry_afterError_reloadsAndSucceeds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeLedgerRepository(Result.failure(RuntimeException("offline")))
        val viewModel = InvoiceListViewModel(ledgerRepository = repository, dispatcher = dispatcher)
        advanceUntilIdle()
        assertIs<InvoiceListContent.Error>(viewModel.uiState.value.content)

        repository.invoicesResult = Result.success(sampleInvoices)
        viewModel.processIntent(InvoiceListIntent.Retry)
        advanceUntilIdle()

        assertIs<InvoiceListContent.Success>(viewModel.uiState.value.content)
        assertEquals(2, repository.fetchInvoicesCallCount)
    }
}
