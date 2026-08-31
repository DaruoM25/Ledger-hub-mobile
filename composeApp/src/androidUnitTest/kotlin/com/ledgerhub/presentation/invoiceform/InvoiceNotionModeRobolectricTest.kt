package com.ledgerhub.presentation.invoiceform

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.invoices.formatMoney
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Niveau 3a (US-15) — le sélecteur de mode et la saisie sur feuille blanche, rendus sous
 * Robolectric (JVM, exécutable en CI sans émulateur).
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists` : un nœud présent dans
 * l'arbre mais invisible ne prouve rien à l'utilisateur. Le scénario tactile réel (focus, cible
 * de 48 dp, capture d'écran) est couvert par le niveau 3b sur émulateur Pixel 5.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceNotionModeRobolectricTest {

    private val fr = formatMoney(10_000, AppLanguage.FR)

    // ── Sélecteur de mode ───────────────────────────────────────────────────

    @Test
    fun onOpening_theSelectorIsShown_andFormModeIsSelectedByDefault() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.MODE_SELECTOR).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC)).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).assertIsDisplayed()

        // Le formulaire classique reste l'entrée par défaut — la feuille est un choix délibéré.
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC)).assertIsSelected()
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).assertIsNotSelected()
        onNodeWithTag(InvoiceFormTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun switchingBetweenModes_isReversible_andUpdatesTheSelectedSegment() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()

        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).assertIsSelected()
        onNodeWithTag(InvoiceFormTags.SCREEN).assertDoesNotExist()

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC)).performClick()

        onNodeWithTag(InvoiceFormTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC)).assertIsSelected()
        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertDoesNotExist()
    }

    /**
     * L'invariant central de l'US-15 : le mode est une **représentation**, pas une source de
     * vérité. Une saisie faite dans un mode se retrouve intacte dans l'autre, dans les deux sens.
     */
    @Test
    fun switchingModes_preservesEverythingTypedInTheOtherMode() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        // Saisie côté formulaire classique.
        onNodeWithTag(InvoiceFormTags.lineLabelTag(0)).performScrollTo().performTextInput("Conseil")
        onNodeWithTag(InvoiceFormTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("100.00")

        // La feuille blanche montre la même ligne, et le même total.
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()
        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().assertTextEquals("Conseil")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().assertTextEquals("100.00")
        onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0)).performScrollTo().assertTextEquals(fr)

        // Correction sur la feuille, puis retour au formulaire : la valeur corrigée a suivi.
        onNodeWithTag(InvoicePaperCanvasTags.CLIENT_NAME).performScrollTo().performTextInput("Boulangerie Moreau SARL")
        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC)).performClick()
        onNodeWithTag(InvoiceFormTags.CLIENT_NAME).performScrollTo().assertTextEquals("Boulangerie Moreau SARL")
    }

    // ── Saisie directe sur la feuille ───────────────────────────────────────

    /**
     * Description, quantité et prix unitaire saisis « sur le papier » : le total de ligne (HT)
     * et les trois totaux du bas de feuille suivent la frappe, sans validation intermédiaire.
     */
    @Test
    fun typingInTheSheetCells_recalculatesLineAndSummaryTotalsLive() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()

        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().performTextInput("Prestation")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("250.00")

        // Quantité par défaut 1, TVA 20 % : 250,00 € HT -> 50,00 € de TVA -> 300,00 € TTC.
        onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0)).performScrollTo()
            .assertTextEquals(formatMoney(25_000, AppLanguage.FR))
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_HT).performScrollTo()
            .assertTextEquals("Total HT : ${formatMoney(25_000, AppLanguage.FR)}")
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_VAT).performScrollTo()
            .assertTextEquals("Total TVA : ${formatMoney(5_000, AppLanguage.FR)}")
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_TTC).performScrollTo()
            .assertTextEquals("Total TTC : ${formatMoney(30_000, AppLanguage.FR)}")
    }

    /**
     * La 5e colonne porte le **Total HT** (US-15, arbitrage d'alignement strict sur la
     * présentation PPF 2026 de l'aperçu A4 de l'US-14) — et non le TTC : le total de ligne et le
     * premier total du récapitulatif doivent donc coïncider sur une facture mono-ligne.
     */
    @Test
    fun theFifthColumn_showsTheLineTotalExcludingVat() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()
        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().performTextInput("Prestation")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("100.00")

        onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0)).performScrollTo().assertTextEquals(fr)
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_HT).performScrollTo().assertTextEquals("Total HT : $fr")
    }

    /**
     * Les cellules numériques appliquent les mêmes filtres que le formulaire classique : une
     * frappe parasite n'entre pas dans la feuille.
     */
    @Test
    fun numericCells_rejectNonNumericKeystrokes() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()

        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("12a,b50")

        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).assertTextEquals("12,50")
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun theSelectorSegments_useTheSpecifiedFrenchWording() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                InvoiceFormScreen(viewModel = InvoiceFormViewModel())
            }
        }

        onNodeWithText("Mode Formulaire").assertIsDisplayed()
        onNodeWithText("Mode Page Blanche").assertIsDisplayed()
    }

    /**
     * En anglais, la feuille blanche est traduite de bout en bout — elle est sortie du
     * « hors périmètre i18n » avec l'US-15. Les montants suivent eux aussi la locale.
     */
    @Test
    fun inEnglish_bothTheSelectorAndTheSheetAreFullyTranslated() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                InvoiceFormScreen(viewModel = InvoiceFormViewModel())
            }
        }

        onNodeWithText("Form Mode").assertIsDisplayed()
        onNodeWithText("Blank Page Mode").assertIsDisplayed()

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).performClick()

        // En-têtes de colonnes et encart client traduits — plus aucun libellé français en dur.
        onNodeWithText("Bill to").performScrollTo().assertIsDisplayed()
        onNodeWithText("Total excl. VAT").performScrollTo().assertIsDisplayed()

        onNodeWithTag(InvoicePaperCanvasTags.lineLabelTag(0)).performScrollTo().performTextInput("Consulting")
        onNodeWithTag(InvoicePaperCanvasTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("100.00")

        // Format anglais : symbole préfixé, point décimal (voir `formatMoney`).
        onNodeWithTag(InvoicePaperCanvasTags.lineTotalTag(0)).performScrollTo()
            .assertTextEquals(formatMoney(10_000, AppLanguage.EN))
        onNodeWithTag(InvoicePaperCanvasTags.TOTAL_TTC).performScrollTo()
            .assertTextEquals("Total incl. tax : ${formatMoney(12_000, AppLanguage.EN)}")
    }
}
