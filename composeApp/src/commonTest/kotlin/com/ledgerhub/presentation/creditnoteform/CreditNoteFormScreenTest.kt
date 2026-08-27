package com.ledgerhub.presentation.creditnoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test

/**
 * Tests d'interface — Skill 2 : QA Automatisé.
 * Teste [CreditNoteFormScreen] (stateful, collectAsState) avec un vrai [CreditNoteFormViewModel].
 */
@OptIn(ExperimentalTestApi::class)
class CreditNoteFormScreenTest {

    private fun sourceInvoice() = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = Party("Vendeur SARL", "123456789", "12345678900012"),
        recipient = Party("Client SAS", "987654321", "98765432100045"),
        lines = listOf(InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.VALIDATED,
    )

    @Test
    fun initialState_prefillsInvertedNegativeTotal_andSubmitButtonIsDisabled() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun typingBlankReason_displaysFieldError() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.CREDIT_NOTE_NUMBER).performScrollTo().performTextInput("AV-2026-001")

        onNodeWithTag(CreditNoteFormTags.errorTagFor(CreditNoteFormField.REASON))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun fillingAllFields_enablesSubmitButton() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.CREDIT_NOTE_NUMBER).performScrollTo().performTextInput("AV-2026-001")
        onNodeWithTag(CreditNoteFormTags.ISSUE_DATE).performScrollTo().performTextInput("2026-08-06")
        onNodeWithTag(CreditNoteFormTags.REASON).performScrollTo().performTextInput("Erreur tarifaire")

        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    }
}
