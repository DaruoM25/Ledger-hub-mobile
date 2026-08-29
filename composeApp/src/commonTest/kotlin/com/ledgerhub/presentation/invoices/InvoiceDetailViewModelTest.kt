package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests QA (Skill 2) de l'écran de détail d'une facture (US-02) — chargement + verrouillage. */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceDetailViewModelTest {

    private fun viewModel(
        repository: FakeLedgerRepository,
        number: String = "F-2026-001",
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ) = InvoiceDetailViewModel(
        invoiceNumber = number,
        ledgerRepository = repository,
        dispatcher = StandardTestDispatcher(scheduler),
    )

    @Test
    fun load_success_exposesInvoiceAndComputesVatBreakdown() = runTest {
        val invoice = testInvoice(
            "F-2026-001",
            status = InvoiceStatus.DEPOSITED,
            unitPriceHtCents = 10_000,
            quantity = 2,
            vatRate = VatRate.TAUX_NORMAL,
        )
        val repository = FakeLedgerRepository(detailResult = Result.success(invoice))
        val vm = viewModel(repository, scheduler = testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("F-2026-001", state.invoice?.number)
        assertEquals(1, state.vatBreakdown.size)
        // 2 x 100,00 HT => base 200,00 ; TVA 20% => 40,00
        assertEquals(Money(20_000), state.vatBreakdown.single().baseHt)
        assertEquals(Money(4_000), state.vatBreakdown.single().vatAmount)
        assertEquals("F-2026-001", repository.lastRequestedNumber)
    }

    @Test
    fun load_whenServerReturnsNull_setsNotFound() = runTest {
        val repository = FakeLedgerRepository(detailResult = Result.success(null))
        val vm = viewModel(repository, number = "F-INCONNUE", scheduler = testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.notFound)
        assertFalse(state.isLoading)
        assertEquals(null, state.invoice)
    }

    @Test
    fun load_whenRepositoryFails_setsErrorMessage() = runTest {
        val repository = FakeLedgerRepository(detailResult = Result.failure(RuntimeException("offline")))
        val vm = viewModel(repository, scheduler = testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.errorMessage != null)
        assertFalse(state.notFound)
    }

    @Test
    fun draftInvoice_allowsEdit_blocksCreditNote() = runTest {
        val repository = FakeLedgerRepository(
            detailResult = Result.success(testInvoice("F-D", status = InvoiceStatus.DRAFT)),
        )
        val vm = viewModel(repository, number = "F-D", scheduler = testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.canEdit)
        assertFalse(state.canCancelByCreditNote)
        assertFalse(state.isLocked)
    }

    @Test
    fun paidInvoice_blocksEdit_allowsCreditNote() = runTest {
        val repository = FakeLedgerRepository(
            detailResult = Result.success(testInvoice("F-P", status = InvoiceStatus.PAID)),
        )
        val vm = viewModel(repository, number = "F-P", scheduler = testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.canEdit)
        assertTrue(state.canCancelByCreditNote)
        assertFalse(state.isLocked)
    }

    @Test
    fun cancelledInvoice_isLocked_andBlocksAllActions() = runTest {
        val repository = FakeLedgerRepository(
            detailResult = Result.success(testInvoice("F-C", status = InvoiceStatus.CANCELLED)),
        )
        val vm = viewModel(repository, number = "F-C", scheduler = testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isLocked)
        assertFalse(state.canEdit)
        assertFalse(state.canCancelByCreditNote)
    }

    @Test
    fun retry_afterFailure_loadsInvoice() = runTest {
        val repository = FakeLedgerRepository(detailResult = Result.failure(RuntimeException("offline")))
        val vm = viewModel(repository, scheduler = testScheduler)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errorMessage != null)

        repository.detailResult = Result.success(testInvoice("F-2026-001", status = InvoiceStatus.DEPOSITED))
        vm.retry()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals("F-2026-001", state.invoice?.number)
    }
}
