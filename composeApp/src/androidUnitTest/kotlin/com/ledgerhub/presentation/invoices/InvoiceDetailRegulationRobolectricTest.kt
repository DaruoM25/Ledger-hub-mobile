package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceDetailRegulationRobolectricTest {

    private fun testInvoice(
        number: String = "F-2026-REG-01",
        status: InvoiceStatus = InvoiceStatus.DEPOSITED,
    ) = Invoice(
        number = number,
        issueDate = "2026-09-12",
        issuer = Party(name = "Mon Entreprise", siren = "123456789", siret = "12345678900012"),
        recipient = Party(name = "Client Test", siren = "987654321", siret = "98765432100034"),
        lines = listOf(InvoiceLine(label = "Conseil", quantity = 1, unitPriceHt = Money(100_00), vatRate = VatRate.TAUX_NORMAL)),
        status = status,
    )

    @Test
    fun testRefusalDialog_disablesConfirmWhenReasonUnder10Chars_andEnablesWhen10CharsOrMore() = runComposeUiTest {
        val invoice = testInvoice()
        val repository = FakeLedgerRepository(detailResult = Result.success(invoice))
        val viewModel = InvoiceDetailViewModel(
            invoiceNumber = invoice.number,
            ledgerRepository = repository,
        )

        setContent {
            LedgerHubTheme {
                InvoiceDetailScreen(viewModel = viewModel)
            }
        }

        // Clic sur le bouton de refus de facture
        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.REFUSED))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        // Le dialogue de motif de refus doit s'ouvrir
        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_DIALOG)
            .assertIsDisplayed()

        // Au départ (vide) : bouton Confirmer désactivé
        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_CONFIRM)
            .assertIsNotEnabled()

        // Saisie d'un motif trop court (< 10 caractères) : reste désactivé
        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_REASON)
            .performTextInput("Court")

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_CONFIRM)
            .assertIsNotEnabled()

        // Compléter le motif >= 10 caractères : s'active
        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_REASON)
            .performTextInput(" supplémentaire")

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_CONFIRM)
            .assertIsEnabled()
            .performClick()
    }
}
