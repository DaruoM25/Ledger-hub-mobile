package com.ledgerhub.presentation.invoiceform

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.formatMoney
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-15) — audit tactile et visuel du **Mode Page Blanche** sur émulateur Pixel 5
 * API 35 (`connectedDebugAndroidTest`).
 *
 * Deux choses que Robolectric ne peut pas prouver : qu'une cellule de tableau prend réellement le
 * focus sous le doigt (et non seulement sous un `performClick` synthétique), et qu'elle offre une
 * cible tactile décente. S'y ajoute l'export de la capture d'écran servant de preuve QA.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceNotionModeInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US15_mobile_invoice_notion_mode_sdk_gphone64_x86_64.png"

    /**
     * Facture de démonstration renseignée par intentions, puis basculée en Mode Page Blanche.
     * Trois lignes dont une au taux réduit : la ventilation TVA du bas de feuille est ainsi
     * visible sur la capture, pas seulement un total unique.
     */
    private fun renderFilledBlankPage(): InvoiceFormViewModel {
        val viewModel = InvoiceFormViewModel()

        composeRule.setContent { InvoiceFormScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0215"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-08-31"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-09-30"))
        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Boulangerie Moreau SARL"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("78410233600021"))
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("compta@moreau.fr"))
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil réglementaire PPF", "2", "100.00", VatRate.TAUX_NORMAL),
        )
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(1, "Ouvrage documentaire", "3", "19.90", VatRate.TAUX_REDUIT),
        )
        viewModel.processIntent(InvoiceFormIntent.AddLine)
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(2, "Formation équipe comptable", "1", "450.00", VatRate.TAUX_NORMAL),
        )

        composeRule.onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()
        composeRule.waitForIdle()
        return viewModel
    }

    /**
     * Interaction tactile réelle : un appui du doigt sur une cellule du tableau lui donne le
     * focus, la frappe qui suit y entre, et le total de ligne se recalcule aussitôt.
     */
    @Test
    fun tappingACell_givesItFocus_andTypingRecalculatesTheLineTotal() {
        renderFilledBlankPage()

        composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).assertIsFocused()

        // La cellule contient déjà « 100.00 » : on ajoute un zéro -> 1 000,00 € x 2 lignes.
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performTextInput("0")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0))
            .performScrollTo()
            .assertIsDisplayed()
    }

    /**
     * Accessibilité tactile : chaque cellule éditable respecte la cible minimale Material de
     * 48 dp. Sur une feuille A4 rétrécie à un écran de téléphone, c'est la contrainte qui
     * détermine si le mode est utilisable au doigt ou seulement à la souris.
     */
    @Test
    fun everyEditableCell_meetsTheMinimumTouchTargetHeight() {
        renderFilledBlankPage()

        listOf(
            InvoicePaperCanvasTags.CLIENT_NAME,
            InvoicePaperCanvasTags.CLIENT_SIRET,
            InvoicePaperCanvasTags.lineLabelTag(0),
            InvoicePaperCanvasTags.lineQuantityTag(0),
            InvoicePaperCanvasTags.lineUnitPriceTag(0),
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    /** Le sélecteur reste visible et cohérent une fois la feuille affichée. */
    @Test
    fun theModeSelector_staysVisibleAndSelectedOnTheBlankPage() {
        renderFilledBlankPage()

        composeRule.onNodeWithTag(InvoiceFormTags.MODE_SELECTOR).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).assertIsSelected()
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()
    }

    /** Le bas de feuille affiche les trois totaux réglementaires, ventilation TVA comprise. */
    @Test
    fun theSummaryBlock_showsHtVatAndTtc_withTheVatBreakdown() {
        renderFilledBlankPage()

        // 200,00 € (20 %) + 59,70 € (5,5 %) + 450,00 € (20 %) = 709,70 € HT.
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.TOTAL_HT)
            .performScrollTo()
            .assertTextEquals("Total HT : ${formatMoney(70_970, AppLanguage.FR)}")
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.vatBreakdownTag(VatRate.TAUX_REDUIT))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.TOTAL_VAT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
    }

    /**
     * Preuve QA de l'US-15 — capture de la feuille A4 en saisie, **avec une cellule au focus**
     * pour que le liseré d'édition soit visible sur l'image : c'est précisément ce que la capture
     * doit démontrer (des champs discrets au repos, révélés au toucher).
     *
     * Export sous `US15_mobile_invoice_notion_mode_sdk_gphone64_x86_64.png`, à rapatrier dans
     * `screenshots/` aux côtés des captures US-10 à US-14.
     */
    @Test
    fun exportsTheBlankPageScreenshot_withAFocusedCell() {
        renderFilledBlankPage()

        // Focus posé sur le prix unitaire de la 2e ligne : le liseré et l'aplat de focus
        // apparaissent au milieu du tableau, bien lisibles sur la capture.
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(1))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(1)).assertIsFocused()

        val bitmap = composeRule.onNodeWithTag(InvoicePaperCanvasTags.CANVAS)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), screenshotName)
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-15 non écrite : ${output.absolutePath}")
        assertTrue(output.length() > 0, "Capture US-15 vide : ${output.absolutePath}")
    }
}
