package com.ledgerhub.presentation.invoiceform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.invoices.formatMoney
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Équivalent Android de [InvoiceFormScreenTest] (commonTest, qui sert iosTest sans modification).
 * Voir HelloScreenRobolectricTest pour le détail de cette duplication ciblée, imposée par
 * l'impossibilité d'ajouter @RunWith(RobolectricTestRunner) à une classe partagée commonTest.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceFormScreenRobolectricTest {

    @Test
    fun initialState_bothActionsAreOfferedAndClickable() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        // Le formulaire est long/scrollable : les boutons peuvent être hors du viewport de test
        // tant qu'on ne les scrolle pas explicitement dans la vue.
        // Ils restent actifs sur formulaire vierge : c'est l'appui qui révèle les erreurs.
        onNodeWithTag(InvoiceFormTags.SAVE_DRAFT_BUTTON).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SAVE_DRAFT_BUTTON).assertIsEnabled()
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test
    fun clickingIssueOnEmptyForm_revealsErrorsWithoutSubmitting() = runComposeUiTest {
        val viewModel = InvoiceFormViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON).performScrollTo().performClick()

        onNodeWithTag(InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_NAME))
            .performScrollTo()
            .assertIsDisplayed()
        assertNull(viewModel.uiState.value.submittedInvoice)
    }

    @Test
    fun typingInvalidSiret_displaysFieldError() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.CLIENT_SIRET).performScrollTo().performTextInput("123")

        onNodeWithTag(InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_SIRET))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun typingQuantityAndPrice_updatesTotalTtc() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.lineQuantityTag(0)).performScrollTo().performTextInput("2")
        onNodeWithTag(InvoiceFormTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("50.00")

        onNodeWithTag(InvoiceFormTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addLineButton_appendsSecondLine_withOwnRemoveButton() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.lineRemoveButtonTag(0)).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(InvoiceFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()

        onNodeWithTag(InvoiceFormTags.lineLabelTag(1)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun removeLineButton_removesLine() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()
        onNodeWithTag(InvoiceFormTags.lineLabelTag(1)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(InvoiceFormTags.lineRemoveButtonTag(1)).performScrollTo().performClick()

        onNodeWithTag(InvoiceFormTags.lineLabelTag(1)).assertDoesNotExist()
        onNodeWithTag(InvoiceFormTags.lineLabelTag(0)).performScrollTo().assertIsDisplayed()
    }

    // ── Mode Page Blanche — feuille A4 éditable (US-15) ─────────────────────────

    @Test
    fun modeSelector_switchesFromFormToBlankPage() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        // Le sélecteur de mode (US-15) coiffe les deux vues et reste affiché dans les deux.
        onNodeWithTag(InvoiceFormTags.MODE_SELECTOR).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SCREEN).assertIsDisplayed()

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()

        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SCREEN).assertDoesNotExist()
        onNodeWithTag(InvoiceFormTags.MODE_SELECTOR).assertIsDisplayed()
    }

    @Test
    fun blankPageMode_editingLinePriceInPlace_recalculatesLiveTotals() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()
        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()

        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().performTextInput("Prestation")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("100.00")

        // 100,00 € HT x 1 : la 5e colonne porte le total de ligne **HT** (US-15, aligné sur la
        // présentation PPF 2026 de l'aperçu A4), le bas de feuille porte le TTC après 20 % de
        // TVA — preuve que la recalculation est bien en direct, sur les deux horizons.
        onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0)).performScrollTo()
            .assertTextEquals(formatMoney(10_000, AppLanguage.FR))
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_HT).performScrollTo()
            .assertTextEquals("Total HT : ${formatMoney(10_000, AppLanguage.FR)}")
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_TTC).performScrollTo()
            .assertTextEquals("Total TTC : ${formatMoney(12_000, AppLanguage.FR)}")
    }
}
