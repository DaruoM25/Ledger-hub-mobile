package com.ledgerhub.presentation.invoiceform

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.invoices.formatMoney
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Niveau 3a (US-23) — la page A4 vue **par le contrat de tags normalisé**.
 *
 * ## Ce que ce niveau ajoute à l'existant
 *
 * Le comportement du mode canvas est déjà couvert par `InvoiceNotionModeRobolectricTest`
 * (US-15) : bascule réversible, saisie en place, filtres numériques, traduction. Le dupliquer
 * n'apporterait rien. Ce qui restait à éprouver, c'est que **les huit tags imposés désignent bien
 * les nœuds que le cahier des charges décrit** — et en particulier les trois qui n'existaient
 * pas : la feuille distincte du bureau, l'encart client, le tableau des lignes.
 *
 * ## Défilement
 *
 * Le bureau défile, et la feuille est haute. Toute assertion sur un nœud bas passe donc par
 * `performScrollTo()` — le geste de l'utilisateur, pas un contournement : une assertion qui exige
 * qu'une feuille entière tienne d'un coup dans un écran est une promesse que la taille de police
 * système suffit à briser (leçon retenue de l'US-22).
 *
 * Gabarit Pixel 5 (`w411dp-h891dp`) plutôt que l'appareil Robolectric par défaut (320 × 470 dp),
 * sur lequel la feuille serait à l'étroit au point de rendre les assertions illisibles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class)
class InvoiceCanvasModeRobolectricTest {

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    /**
     * La bascule passe par l'action sémantique et non par un `performClick` : le segment est un
     * `SegmentedButton`, dont la cible tactile réelle relève du niveau 3b sur appareil.
     */
    private fun SemanticsNodeInteraction.selectMode() =
        performSemanticsAction(SemanticsActions.OnClick)

    // ── Entrée dans le mode canvas ──────────────────────────────────────────

    /** Le bouton imposé ouvre le canvas, et se donne pour sélectionné une fois dedans. */
    @Test
    fun theCanvasModeButton_opensTheCanvas() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag("invoice_canvas_container").assertDoesNotExist()

        onNodeWithTag("invoice_mode_canvas_btn").assertIsDisplayed().selectMode()

        onNodeWithTag("invoice_mode_canvas_btn").assertIsSelected()
        onNodeWithTag("invoice_canvas_container").assertIsDisplayed()
    }

    /** Les huit tags du cahier des charges, présents dans un même rendu. */
    @Test
    fun theEightSpecifiedTags_areAllPresentOnTheCanvas() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        onNodeWithTag("invoice_mode_canvas_btn").assertIsDisplayed()
        onNodeWithTag("invoice_canvas_container").assertIsDisplayed()
        onNodeWithTag("invoice_canvas_page").assertIsDisplayed()
        onNodeWithTag("invoice_canvas_client_card").performScrollTo().assertIsDisplayed()
        onNodeWithTag("invoice_canvas_items_table").performScrollTo().assertIsDisplayed()
        onNodeWithTag("invoice_canvas_total_ht").performScrollTo().assertIsDisplayed()
        onNodeWithTag("invoice_canvas_total_tva").performScrollTo().assertIsDisplayed()
        onNodeWithTag("invoice_canvas_total_ttc").performScrollTo().assertIsDisplayed()
    }

    // ── Structure de la feuille ─────────────────────────────────────────────

    /**
     * Le bureau et la feuille sont deux nœuds : la feuille est **contenue** dans le conteneur,
     * jamais l'inverse. C'est ce qui permet à la capture QA de cadrer le document seul.
     */
    @Test
    fun thePage_sitsInsideTheContainer() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        val container = onNodeWithTag(InvoiceCanvasTags.CONTAINER).fetchSemanticsNode().boundsInRoot
        val page = onNodeWithTag(InvoiceCanvasTags.PAGE).fetchSemanticsNode().boundsInRoot

        kotlin.test.assertTrue(page.left >= container.left, "La feuille déborde du bureau à gauche")
        kotlin.test.assertTrue(page.right <= container.right, "La feuille déborde du bureau à droite")
        kotlin.test.assertTrue(
            page.width < container.width,
            "La feuille devrait être encadrée par le bureau, marges comprises",
        )
    }

    /**
     * L'encart client est tagué **sans fusionner** : ses deux champs restent atteignables un par
     * un. Les fusionner en ferait un nœud unique, et l'édition en place cesserait d'être testable
     * — comme elle cesserait d'être annonçable à un lecteur d'écran.
     */
    @Test
    fun theClientCard_keepsItsFieldsIndividuallyAddressable() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        onNodeWithTag(InvoiceCanvasTags.CLIENT_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePaperCanvasTags.CLIENT_NAME).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePaperCanvasTags.CLIENT_SIRET).performScrollTo().assertIsDisplayed()
    }

    /** Même exigence pour le tableau : ses cellules restent des nœuds à part entière. */
    @Test
    fun theItemsTable_keepsItsCellsIndividuallyAddressable() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        onNodeWithTag(InvoiceCanvasTags.ITEMS_TABLE).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePaperCanvasTags.lineQuantityTag(0)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().assertIsDisplayed()
    }

    // ── Saisie directe et totaux en direct ──────────────────────────────────

    /**
     * Le cœur de l'US : on écrit **sur le document**, et le pied de page suit la frappe.
     * Les trois totaux sont visés par leurs tags imposés.
     */
    @Test
    fun typingOnThePage_updatesTheThreeTotalsUnderTheirSpecifiedTags() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0))
            .performScrollTo()
            .performTextInput("Conseil réglementaire")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0))
            .performScrollTo()
            .performTextInput("250.00")

        // Quantité par défaut 1, TVA 20 % : 250,00 € HT -> 50,00 € de TVA -> 300,00 € TTC.
        onNodeWithTag("invoice_canvas_total_ht").performScrollTo()
            .assertTextEquals("${tr(StringKey.PREVIEW_TOTAL_HT)} : ${formatMoney(25_000, AppLanguage.FR)}")
        onNodeWithTag("invoice_canvas_total_tva").performScrollTo()
            .assertTextEquals("${tr(StringKey.PREVIEW_TOTAL_VAT)} : ${formatMoney(5_000, AppLanguage.FR)}")
        onNodeWithTag("invoice_canvas_total_ttc").performScrollTo()
            .assertTextEquals("${tr(StringKey.PREVIEW_TOTAL_TTC)} : ${formatMoney(30_000, AppLanguage.FR)}")
    }

    /** L'encart client s'édite en place : la frappe y entre et y reste. */
    @Test
    fun typingInTheClientCard_writesOnTheDocument() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        onNodeWithTag(InvoicePaperCanvasTags.CLIENT_NAME)
            .performScrollTo()
            .performTextInput("Boulangerie Moreau SARL")

        onNodeWithTag(InvoicePaperCanvasTags.CLIENT_NAME)
            .assertTextEquals("Boulangerie Moreau SARL")
    }

    // ── Internationalisation ────────────────────────────────────────────────

    /** Le contrat de tags ne dépend pas de la langue : seuls les libellés changent. */
    @Test
    fun inEnglish_theSameTagsDesignateTheSameNodes() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                InvoiceFormScreen(viewModel = InvoiceFormViewModel())
            }
        }
        onNodeWithTag(InvoiceCanvasTags.MODE_BUTTON).selectMode()

        onNodeWithTag("invoice_canvas_page").assertIsDisplayed()
        onNodeWithTag("invoice_canvas_client_card").performScrollTo().assertIsDisplayed()
        onNodeWithTag("invoice_canvas_items_table").performScrollTo().assertIsDisplayed()
        onNodeWithTag("invoice_canvas_total_ttc").performScrollTo()
            .assertTextEquals("${tr(StringKey.PREVIEW_TOTAL_TTC, AppLanguage.EN)} : ${formatMoney(0, AppLanguage.EN)}")
    }
}
