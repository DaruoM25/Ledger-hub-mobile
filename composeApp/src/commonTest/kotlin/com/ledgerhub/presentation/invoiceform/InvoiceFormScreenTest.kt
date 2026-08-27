package com.ledgerhub.presentation.invoiceform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

/**
 * Tests d'interface — Skill 2 : QA Automatisé.
 * Teste [InvoiceFormScreen] (stateful, collectAsState) avec un vrai [InvoiceFormViewModel]
 * pour exercer le cycle complet saisie -> recomposition -> validation -> totaux, sans mock.
 */
@OptIn(ExperimentalTestApi::class)
class InvoiceFormScreenTest {

    @Test
    fun initialState_submitButtonIsDisabled() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        // Le formulaire est long/scrollable : le bouton peut être hors du viewport de test
        // tant qu'on ne le scrolle pas explicitement dans la vue.
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingInvalidSiren_displaysFieldError() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.ISSUER_SIREN).performScrollTo().performTextInput("123")

        onNodeWithTag(InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUER_SIREN))
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

        // Une seule ligne au départ : son bouton de suppression est désactivé.
        onNodeWithTag(InvoiceFormTags.lineRemoveButtonTag(0)).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(InvoiceFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()

        // Deux lignes désormais : les boutons de suppression redeviennent actifs.
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

    // ── Aperçu WYSIWYG (feuille A4) ─────────────────────────────────────────────

    @Test
    fun previewModeToggle_switchesFromFormToPaperCanvas() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.SCREEN).assertIsDisplayed()

        onNodeWithTag(InvoiceFormTags.PREVIEW_MODE_TOGGLE).performClick()

        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun paperCanvas_editingLinePriceInPlace_recalculatesLiveTotals() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        // Bascule vers l'aperçu visuel de la feuille.
        onNodeWithTag(InvoiceFormTags.PREVIEW_MODE_TOGGLE).performClick()
        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()

        // Saisie directement "sur le papier" : la quantité par défaut de la première ligne est
        // "1", taux de TVA par défaut = 20 %. Le libellé doit aussi être renseigné, sinon la
        // ligne reste invalide côté ViewModel et n'entre dans aucun total (même règle que le
        // formulaire classique).
        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().performTextInput("Prestation")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("100.00")

        // 100.00 € HT * 1 + 20 % de TVA = 120.00 € TTC, reflété à la fois sur le total de ligne
        // et sur le total général — preuve que la recalculation est bien en direct (WYSIWYG).
        onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0)).performScrollTo()
            .assertTextEquals("120.00 €")
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_TTC).performScrollTo()
            .assertTextEquals("Total TTC : 120.00 €")
    }
}
