package com.ledgerhub.presentation.invoiceform

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.VatRate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-16) — audit tactile et visuel des mentions légales B2B sur émulateur Pixel 5
 * API 35 (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que la case se coche réellement sous le doigt (et non
 * seulement via l'action sémantique), et qu'elle offre une cible tactile décente malgré un
 * libellé long. S'y ajoute l'export de la capture d'écran servant de preuve QA.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceB2bPenaltiesInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US16_mobile_invoice_b2b_penalties_sdk_gphone64_x86_64.png"

    private val legalMention = AppTranslations.get(StringKey.B2B_LEGAL_MENTION, AppLanguage.FR)
    private val courtesyMention = AppTranslations.get(StringKey.B2B_COURTESY_MENTION, AppLanguage.FR)

    /** Facture de démonstration renseignée par intentions, pour une capture représentative. */
    private fun renderFilledForm(): InvoiceFormViewModel {
        val viewModel = InvoiceFormViewModel()

        composeRule.setContent { InvoiceFormScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0216"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-31"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Boulangerie Moreau SARL"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("78410233600021"))
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("compta@moreau.fr"))
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil réglementaire PPF", "2", "100.00", VatRate.TAUX_NORMAL),
        )
        composeRule.waitForIdle()
        return viewModel
    }

    private fun switchToBlankPage() {
        composeRule.onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
    }

    // ── Interaction tactile réelle ──────────────────────────────────────────

    /**
     * Un vrai appui du doigt sur la ligne coche/décoche la case et commute le pied de page. Le
     * `toggleable` portant la ligne entière, le clic tombe sur le libellé — pas sur le carré de
     * 20 dp — ce qui est précisément l'ergonomie que ce test doit valider.
     */
    @Test
    fun tappingTheRow_togglesTheBox_andSwapsTheLegalFooter() {
        renderFilledForm()

        composeRule.onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(legalMention)

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX)
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).assertIsOff()
        composeRule.onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(courtesyMention)

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX)
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).assertIsOn()
        composeRule.onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(legalMention)
    }

    /** Le même geste fonctionne en mode Page Blanche, sur le pied de la feuille A4. */
    @Test
    fun tappingTheBoxOnTheBlankPage_swapsTheSheetFooter() {
        renderFilledForm()
        switchToBlankPage()

        composeRule.onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(legalMention)

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX)
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(courtesyMention)
    }

    /**
     * Accessibilité tactile : la ligne cochable respecte la cible minimale Material de 48 dp,
     * dans les deux modes de saisie.
     */
    @Test
    fun theCheckboxRow_meetsTheMinimumTouchTargetHeight() {
        renderFilledForm()

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        switchToBlankPage()

        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture de l'US-16 : mode Page Blanche, case cochée, mention de l'article L.441-10 visible
     * en pied de feuille — c'est exactement ce que la capture doit démontrer.
     *
     * Export sous `US16_mobile_invoice_b2b_penalties_sdk_gphone64_x86_64.png`, à rapatrier dans
     * `screenshots/` aux côtés des captures US-10 à US-15.
     */
    @Test
    fun exportsTheB2bPenaltiesScreenshot() {
        renderFilledForm()
        switchToBlankPage()

        composeRule.onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).assertIsOn()
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), screenshotName)
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-16 non écrite : ${output.absolutePath}")
        assertTrue(output.length() > 0, "Capture US-16 vide : ${output.absolutePath}")
    }
}
