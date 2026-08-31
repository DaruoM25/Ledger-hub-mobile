package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.components.InvoicePreviewSheet
import com.ledgerhub.presentation.invoices.components.InvoicePreviewTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * N3a (US-14) — ouverture de l'aperçu et présence de ses sections.
 *
 * Deux niveaux distincts, volontairement séparés :
 * - l'**ouverture** est vérifiée depuis l'écran de détail, car c'est le seul chemin réel vers
 *   l'aperçu ;
 * - le **contenu** est vérifié sur [InvoicePreviewSheet] rendu seul, pour que l'échec d'une
 *   section désigne la feuille et non la fenêtre de dialogue qui la transporte.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoicePreviewDialogRobolectricTest {

    private val invoice = Invoice(
        number = "FAC-2026-0184",
        issueDate = "2026-08-16",
        issuer = Party("Roux Expertise", "820329331", "82032933100027", "contact@roux-expertise.fr"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(
            InvoiceLine("Prestation de conseil", quantity = 3, unitPriceHt = Money(125_050), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Documentation fiscale", quantity = 1, unitPriceHt = Money(4_500), vatRate = VatRate.TAUX_REDUIT),
        ),
        status = InvoiceStatus.DEPOSITED,
        dueDate = "2026-09-15",
    )

    private fun detailState() = InvoiceDetailUiState(isLoading = false, invoice = invoice)

    // ── Ouverture depuis l'écran de détail ───────────────────────────────────────────────────

    @Test
    fun previewButton_isDisplayedOnTheDetailScreen() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = detailState()) }

        onNodeWithTag(InvoiceDetailScreenTags.PREVIEW_BUTTON).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clickingPreview_opensTheSheetDialog() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = detailState()) }

        onNodeWithTag(InvoiceDetailScreenTags.PREVIEW_BUTTON).performScrollTo().performClick()

        onNodeWithTag(InvoicePreviewTags.SHEET).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.CLOSE_BUTTON).assertIsDisplayed()
    }

    /**
     * Une facture annulée reste consultable : l'aperçu montre la pièce telle qu'elle a été
     * émise, il ne la modifie pas. Le verrouillage fiscal ne doit donc pas le fermer.
     */
    @Test
    fun previewStaysAvailable_onACancelledInvoice() = runComposeUiTest {
        setContent {
            InvoiceDetailView(
                uiState = InvoiceDetailUiState(
                    isLoading = false,
                    invoice = invoice.copy(status = InvoiceStatus.CANCELLED),
                    creditNoteNumber = "AV-2026-0007",
                ),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.PREVIEW_BUTTON).performScrollTo().performClick()
        onNodeWithTag(InvoicePreviewTags.SHEET).assertIsDisplayed()
    }

    // ── Contenu de la feuille ────────────────────────────────────────────────────────────────

    @Test
    fun sheet_showsTheIssuerBlock_withSiretAndVatNumber() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        onNodeWithTag(InvoicePreviewTags.ISSUER_BLOCK).assertIsDisplayed()
        onNodeWithText("Roux Expertise").assertIsDisplayed()
        onNodeWithContentDescription("SIRET 82032933100027").assertIsDisplayed()
        onNodeWithContentDescription("TVA intracommunautaire FR82820329331").assertIsDisplayed()
    }

    @Test
    fun sheet_showsTheClientBlock_withAddressAndSiren() = runComposeUiTest {
        setContent {
            InvoicePreviewSheet(
                invoice = invoice,
                recipientAddressLines = listOf("4 place du Marché", "69001 Lyon"),
            )
        }

        onNodeWithTag(InvoicePreviewTags.CLIENT_BLOCK).assertIsDisplayed()
        onNodeWithText("Boulangerie Moreau SARL").assertIsDisplayed()
        onNodeWithText("4 place du Marché").assertIsDisplayed()
        onNodeWithContentDescription("SIREN 784102336").assertIsDisplayed()
    }

    @Test
    fun sheet_showsInvoiceMetadata_numberIssueDateAndDueDate() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        onNodeWithContentDescription("Facture n° FAC-2026-0184").assertIsDisplayed()
        onNodeWithContentDescription("Date d'émission : 16/08/2026").assertIsDisplayed()
        onNodeWithContentDescription("Date d'échéance : 15/09/2026").assertIsDisplayed()
    }

    @Test
    fun sheet_showsTheLinesTable_withOneRowPerLine() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        onNodeWithTag(InvoicePreviewTags.LINES_TABLE).performScrollTo().assertIsDisplayed()
        onNodeWithText("Prestation de conseil").performScrollTo().assertIsDisplayed()
        onNodeWithText("Documentation fiscale").performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.lineRow(0)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.lineRow(1)).performScrollTo().assertIsDisplayed()
    }

    /**
     * Les cinq en-têtes du tableau, dont la 5e colonne « Total HT » : standard B2B PPF 2026,
     * la TVA n'apparaît qu'une fois, ventilée par taux, au pied de page.
     */
    @Test
    fun sheet_showsEveryTableColumnHeader_includingTheHtColumn() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        onNodeWithTag(InvoicePreviewTags.LINES_TABLE).performScrollTo()
        listOf("Description", "Qté", "Prix unitaire HT", "Taux TVA", "Total HT").forEach { header ->
            onNodeWithText(header).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * La 5e colonne porte le net hors taxe de la ligne (quantité x prix unitaire HT), et non
     * son TTC : 3 x 1 250,50 = 3 751,50 € pour la première ligne.
     */
    @Test
    fun sheet_showsTheNetAmountExcludingVat_onEachLine() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        val expected = invoice.lines.first().totalHt.format(AppLanguage.FR)
        onNodeWithTag(InvoicePreviewTags.lineTotalHt(0)).performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription(expected).performScrollTo().assertIsDisplayed()
    }

    /** Les totaux affichés sont ceux de la facture — voir `InvoicePreviewFormattingTest`. */
    @Test
    fun sheet_showsTheTotalsBlock_withHtVatAndTtc() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        onNodeWithTag(InvoicePreviewTags.TOTALS_BLOCK).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.TOTAL_HT).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.TOTAL_VAT).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.TOTAL_TTC).assertIsDisplayed()
    }

    @Test
    fun sheet_showsTheMandatoryLegalFooter() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice) }

        onNodeWithTag(InvoicePreviewTags.LEGAL_BLOCK).performScrollTo().assertIsDisplayed()
        onNodeWithContentDescription(
            "Membre d'une association agréée, le règlement par chèque et carte bancaire est accepté.",
        ).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.LEGAL_LATE_PENALTY).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.LEGAL_INDEMNITY).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.BANK_DETAILS).assertIsDisplayed()
    }

    /**
     * Une facture héritée sans échéance ne doit pas imprimer de ligne vide : la mention
     * disparaît, elle ne s'affiche pas à blanc.
     */
    @Test
    fun sheet_omitsTheDueDateLine_whenTheInvoiceHasNone() = runComposeUiTest {
        setContent { InvoicePreviewSheet(invoice = invoice.copy(dueDate = "")) }

        onNodeWithTag(InvoicePreviewTags.METADATA_BLOCK).assertIsDisplayed()
        onNodeWithTag(InvoicePreviewTags.DUE_DATE).assertDoesNotExist()
    }
}
