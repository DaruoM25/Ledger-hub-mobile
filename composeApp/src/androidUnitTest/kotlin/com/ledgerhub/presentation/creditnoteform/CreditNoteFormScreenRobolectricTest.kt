package com.ledgerhub.presentation.creditnoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Équivalent Android de [CreditNoteFormScreenTest] (commonTest, qui sert iosTest sans
 * modification) — voir HelloScreenRobolectricTest pour le rationnel de cette duplication ciblée.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class CreditNoteFormScreenRobolectricTest {

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
        onNodeWithContentDescription("AVOIR EN BROUILLON").assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.TOTALS_CARTRIDGE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theFourLegalReasonOptionsAreDisplayed() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.REASON_SECTION).performScrollTo().assertIsDisplayed()
        CreditNoteReason.entries.forEach { kind ->
            onNodeWithTag(CreditNoteFormTags.reasonChip(kind)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun choosingOther_revealsFreeTextField_thenClearingIt_showsReasonError() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.reasonChip(CreditNoteReason.OTHER)).performScrollTo().performClick()
        onNodeWithTag(CreditNoteFormTags.REASON_FREE_TEXT).performScrollTo().assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.REASON_FREE_TEXT).performTextInput("Erreur")
        onNodeWithTag(CreditNoteFormTags.REASON_FREE_TEXT).performTextClearance()

        onNodeWithTag(CreditNoteFormTags.errorTagFor(CreditNoteFormField.REASON))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun pickingAPresetReasonAndADate_enablesSubmitButton() = runComposeUiTest {
        setContent { CreditNoteFormScreen(viewModel = CreditNoteFormViewModel(sourceInvoice = sourceInvoice())) }

        onNodeWithTag(CreditNoteFormTags.ISSUE_DATE).performScrollTo().performTextInput("2026-08-06")
        onNodeWithTag(CreditNoteFormTags.reasonChip(CreditNoteReason.BILLING_ERROR)).performScrollTo().performClick()

        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    }
}
