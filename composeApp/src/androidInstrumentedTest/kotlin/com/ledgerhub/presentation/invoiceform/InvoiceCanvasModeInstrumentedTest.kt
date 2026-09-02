package com.ledgerhub.presentation.invoiceform

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
 * Niveau 3b (US-23) — le mode canvas au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * ## Ce que ce niveau ajoute
 *
 * Le parcours tactile du mode page blanche est déjà éprouvé par
 * `InvoiceNotionModeInstrumentedTest` (US-15) — focus d'une cellule sous le doigt, cibles de
 * 48 dp, ventilation TVA. Ce qui restait à prouver sur appareil, c'est que **les nœuds normalisés
 * par l'US-23 sont joignables** — feuille, encart client, tableau, trois totaux — et que la
 * capture officielle cadre **le document seul**, sans le bureau qui l'entoure.
 *
 * ## Défilement
 *
 * `performScrollTo()` avant toute interaction ou assertion sur un nœud bas. La feuille est plus
 * haute qu'un écran de téléphone dès qu'elle porte trois lignes : exiger qu'elle tienne d'un coup
 * serait une promesse que la police système de l'utilisateur suffit à briser (leçon de l'US-22).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceCanvasModeInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US23_mobile_invoice_canvas_mode_sdk_gphone64_x86_64.png"

    /**
     * Facture de démonstration renseignée par intentions, puis basculée en mode canvas.
     *
     * Trois lignes dont une au taux réduit : la capture montre ainsi une feuille **vivante**, avec
     * sa ventilation TVA, et non un document vide qui ne prouverait rien des totaux en direct.
     */
    private fun renderFilledCanvas() {
        val viewModel = InvoiceFormViewModel()

        composeRule.setContent { InvoiceFormScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0231"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-09-02"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-10-02"))
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

        composeRule.onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).performClick()
        composeRule.waitForIdle()
    }

    // ── Entrée dans le mode ─────────────────────────────────────────────────

    @Test
    fun theCanvasModeButton_opensTheCanvasOnDevice() {
        renderFilledCanvas()

        composeRule.onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithTag(InvoiceCanvasTags.CONTAINER).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.PAGE).assertIsDisplayed()
    }

    /** Les huit nœuds normalisés sont **joignables** sur l'appareil cible. */
    @Test
    fun everySpecifiedNode_isReachableOnDevice() {
        renderFilledCanvas()

        composeRule.onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.CONTAINER).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.CLIENT_CARD).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.ITEMS_TABLE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.TOTAL_HT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.TOTAL_VAT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoiceCanvasTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
    }

    // ── Édition en place, au doigt ──────────────────────────────────────────

    /**
     * L'encart client s'édite **sur le document**, sous le doigt : un appui lui donne le focus, et
     * la frappe y entre. C'est le geste que le tag `invoice_canvas_client_card` désigne.
     */
    @Test
    fun tappingTheClientCard_letsItBeEditedInPlace() {
        renderFilledCanvas()

        composeRule.onNodeWithTag(InvoicePaperCanvasTags.CLIENT_NAME)
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoicePaperCanvasTags.CLIENT_NAME)
            .assertTextEquals("Boulangerie Moreau SARL")
    }

    /** Cibles tactiles : une cellule du tableau reste tapable au doigt, pas seulement son texte. */
    @Test
    fun everyEditableCellOfTheTable_meetsTheMinimumTouchTarget() {
        renderFilledCanvas()

        listOf(0, 1, 2).forEach { index ->
            composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(index))
                .performScrollTo()
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineQuantityTag(index))
                .performScrollTo()
                .assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(index))
                .performScrollTo()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    /**
     * Les totaux affichés au pied de la feuille sont ceux des trois lignes saisies :
     * 200,00 + 59,70 + 450,00 = 709,70 € HT, 40,00 + 3,28 + 90,00 = 133,28 € de TVA,
     * soit 842,98 € TTC. Le calcul est prouvé ailleurs ; ce qui est prouvé ici, c'est qu'il
     * **arrive à l'écran de l'appareil** sous les tags imposés.
     */
    @Test
    fun theFooterTotals_showTheLiveAmountsUnderTheirSpecifiedTags() {
        renderFilledCanvas()

        composeRule.onNodeWithTag(InvoiceCanvasTags.TOTAL_HT).performScrollTo()
            .assertTextEquals("Total HT : ${formatMoney(70_970, AppLanguage.FR)}")
        composeRule.onNodeWithTag(InvoiceCanvasTags.TOTAL_VAT).performScrollTo()
            .assertTextEquals("Total TVA : ${formatMoney(13_328, AppLanguage.FR)}")
        composeRule.onNodeWithTag(InvoiceCanvasTags.TOTAL_TTC).performScrollTo()
            .assertTextEquals("Total TTC : ${formatMoney(84_298, AppLanguage.FR)}")
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-23, cadrée sur **`invoice_canvas_page`** et non sur le conteneur :
     * ce que l'US doit démontrer, c'est le document — la feuille A4 avec son émetteur à gauche, son
     * encart client à droite, son tableau éditable et ses totaux en direct. Le bureau gris qui
     * l'entoure n'est qu'un décor, et l'inclure diluerait la preuve.
     *
     * Écrite **aux deux emplacements** : les fichiers de l'application (traçabilité) et
     * `/sdcard/Download`, d'où `scripts/run-qa.ps1 -ScreenshotPrefix "US23"` la rapatrie sans
     * `adb pull` manuel (même procédé que les US-20 à US-22).
     */
    @Test
    fun exportsTheCanvasPageScreenshot() {
        renderFilledCanvas()

        composeRule.onNodeWithTag(InvoiceCanvasTags.PAGE).assertIsDisplayed()
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(InvoiceCanvasTags.PAGE)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFile = File(context.getExternalFilesDir(null), screenshotName)
        appFile.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        val sharedFile = File("/sdcard/Download", screenshotName).apply { parentFile?.mkdirs() }
        runCatching {
            sharedFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        assertTrue(appFile.exists(), "Capture US-23 non écrite : ${appFile.absolutePath}")
        assertTrue(appFile.length() > 0, "Capture US-23 vide : ${appFile.absolutePath}")
    }
}
