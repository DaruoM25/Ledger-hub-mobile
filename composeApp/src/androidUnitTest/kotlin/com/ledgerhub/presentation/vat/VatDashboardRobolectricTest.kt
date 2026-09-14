package com.ledgerhub.presentation.vat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.vat.CalculateVatMetricsUseCase
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class VatDashboardRobolectricTest {

    private val fakeIssuer = Party(name = "Mon Entreprise", siren = "123456782", siret = "12345678200015")
    private val fakeClient = Party(name = "Client Test", siren = "987654321", siret = "98765432100010")

    private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @Test
    fun vatDashboard_rendersKpisAndCa3Section() = runComposeUiTest {
        val invoices = listOf(
            Invoice(
                number = "FAC-2026-0001",
                issueDate = "2026-09-14",
                issuer = fakeIssuer,
                recipient = fakeClient,
                status = InvoiceStatus.PAID,
                natureOperation = NatureOperation.LIVRAISON_BIENS,
                lines = listOf(
                    InvoiceLine(label = "Matériel", quantity = 1, unitPriceHt = Money(100_000), vatRate = VatRate.TAUX_NORMAL),
                ),
            ),
        )

        val testDispatcher = UnconfinedTestDispatcher()
        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(invoices))
        val viewModel = VatDashboardViewModel(
            calculateVatMetricsUseCase = useCase,
            dispatcher = testDispatcher,
        )

        setContent {
            LedgerHubTheme {
                VatDashboardScreen(viewModel = viewModel)
            }
        }

        // Vérification de la présence de l'écran et des KPIs
        onNodeWithTag(VatDashboardTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(VatDashboardTags.KPI_COLLECTED_EXIGIBLE).assertIsDisplayed()
        onNodeWithTag(VatDashboardTags.KPI_PENDING_COLLECTION).assertIsDisplayed()
        onNodeWithTag(VatDashboardTags.KPI_DEDUCTIBLE).assertIsDisplayed()
        onNodeWithTag(VatDashboardTags.KPI_NET_BALANCE).assertIsDisplayed()

        // Section CA3 et ligne L01
        onNodeWithTag(VatDashboardTags.CA3_SECTION).assertExists()
        onNodeWithTag(VatDashboardTags.ca3Line("01")).assertExists()
    }
}
