package com.ledgerhub.presentation.vat

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.vat.CalculateVatMetricsUseCase
import com.ledgerhub.domain.vat.VatPeriodFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class VatDashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakeIssuer = Party(name = "Mon Entreprise", siren = "123456782", siret = "12345678200015")
    private val fakeClient = Party(name = "Client Test", siren = "987654321", siret = "98765432100010")

    private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_computesMetricsAndEmitsSuccessState() = testScope.runTest {
        val invoice = Invoice(
            number = "FAC-2026-0001",
            issueDate = "2026-09-14",
            issuer = fakeIssuer,
            recipient = fakeClient,
            status = InvoiceStatus.PAID,
            natureOperation = NatureOperation.LIVRAISON_BIENS,
            lines = listOf(
                InvoiceLine(label = "Matériel", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val viewModel = VatDashboardViewModel(
            calculateVatMetricsUseCase = useCase,
            dispatcher = testDispatcher,
            coroutineScope = this,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(Money(20_000), state.metrics.totalVatCollectedExigible)
        assertEquals(Money(20_000), state.metrics.netVatBalance)
    }

    @Test
    fun changePeriod_recomputesMetrics() = testScope.runTest {
        val invoice = Invoice(
            number = "FAC-2026-0001",
            issueDate = "2026-01-15", // Hors mois 09
            issuer = fakeIssuer,
            recipient = fakeClient,
            status = InvoiceStatus.PAID,
            natureOperation = NatureOperation.LIVRAISON_BIENS,
            lines = listOf(
                InvoiceLine(label = "Matériel", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(listOf(invoice)))
        val viewModel = VatDashboardViewModel(
            calculateVatMetricsUseCase = useCase,
            dispatcher = testDispatcher,
            coroutineScope = this,
        )

        advanceUntilIdle()
        assertEquals(Money(20_000), viewModel.uiState.value.metrics.totalVatCollectedExigible)

        // Bascule vers le mois en cours (09) -> la facture de janvier ne doit pas être comptée
        viewModel.processIntent(VatDashboardIntent.ChangePeriod(VatPeriodFilter.CURRENT_MONTH))
        advanceUntilIdle()

        assertEquals(VatPeriodFilter.CURRENT_MONTH, viewModel.uiState.value.selectedPeriod)
        assertEquals(Money.ZERO, viewModel.uiState.value.metrics.totalVatCollectedExigible)
    }
}
