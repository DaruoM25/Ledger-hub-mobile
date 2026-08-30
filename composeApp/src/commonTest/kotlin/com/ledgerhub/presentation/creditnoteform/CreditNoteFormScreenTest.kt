package com.ledgerhub.presentation.creditnoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.creditnote.CreditNoteReason
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
        status = InvoiceStatus.DEPOSITED,
    )

    @Test
    fun initialState_showsDraftBadgeAndCartridge_andSubmitButtonIsDisabled() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.DRAFT_BADGE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.TOTALS_CARTRIDGE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun choosingOtherThenClearingTheFreeText_displaysTheReasonError() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.reasonChip(CreditNoteReason.OTHER)).performScrollTo().performClick()
        onNodeWithTag(CreditNoteFormTags.REASON_FREE_TEXT).performScrollTo().performTextInput("Erreur")
        onNodeWithTag(CreditNoteFormTags.REASON_FREE_TEXT).performTextClearance()

        onNodeWithTag(CreditNoteFormTags.errorTagFor(CreditNoteFormField.REASON))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun pickingAPresetReasonAndADate_enablesSubmitButton() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.ISSUE_DATE).performScrollTo().performTextInput("2026-08-06")
        onNodeWithTag(CreditNoteFormTags.reasonChip(CreditNoteReason.COMMERCIAL_DISCOUNT)).performScrollTo().performClick()

        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    }
}
