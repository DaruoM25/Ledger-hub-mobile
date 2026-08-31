package com.ledgerhub.presentation.invoices

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.components.InvoicePreviewSheet
import com.ledgerhub.presentation.invoices.components.InvoicePreviewTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * N3b (US-14) — audit visuel de l'aperçu A4 sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`) : ordre vertical des blocs, largeur de la feuille, et export
 * d'une capture servant de preuve QA.
 *
 * La capture porte sur [InvoicePreviewSheet] rendu directement, et non sur
 * `InvoicePreviewDialog` : une fenêtre de dialogue vit dans sa propre `Window`, dont le contenu
 * ne se capture pas de façon fiable via l'arbre de la vue hôte.
 *
 * L'émetteur de la facture de test est « Roux Expertise » : l'identité imprimée provient
 * toujours de la facture (gelée à l'émission), jamais du papier à en-tête.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class InvoicePreviewInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val invoice = Invoice(
        number = "FAC-2026-0184",
        issueDate = "2026-08-16",
        issuer = Party("Roux Expertise", "820329331", "82032933100027", "contact@roux-expertise.fr"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(
            InvoiceLine("Prestation de conseil", quantity = 3, unitPriceHt = Money(125_050), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Documentation fiscale", quantity = 1, unitPriceHt = Money(4_500), vatRate = VatRate.TAUX_REDUIT),
            InvoiceLine("Frais de dossier", quantity = 1, unitPriceHt = Money(9_000), vatRate = VatRate.TAUX_NORMAL),
        ),
        status = InvoiceStatus.DEPOSITED,
        dueDate = "2026-09-15",
    )

    private fun renderSheet() {
        composeRule.setContent {
            InvoicePreviewSheet(
                invoice = invoice,
                recipientAddressLines = listOf("4 place du Marché", "69001 Lyon"),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun sheet_rendersEverySectionOfTheOfficialDocument() {
        renderSheet()

        composeRule.onNodeWithTag(InvoicePreviewTags.SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.ISSUER_BLOCK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.CLIENT_BLOCK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.METADATA_BLOCK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.LINES_TABLE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.TOTALS_BLOCK).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.LEGAL_BLOCK).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithContentDescription(
            "Membre d'une association agréée, le règlement par chèque et carte bancaire est accepté.",
        ).performScrollTo().assertIsDisplayed()
    }

    /**
     * 5e colonne au standard B2B PPF 2026 : en-tête « Total HT » et net hors taxe de chaque
     * ligne (quantité x prix unitaire HT). La TVA n'apparaît qu'au pied de page, ventilée par
     * taux — une colonne TTC réintroduirait la taxe deux fois dans le même document.
     */
    @Test
    fun linesTable_showsTheNetAmountExcludingVat_inItsFifthColumn() {
        renderSheet()

        composeRule.onNodeWithTag(InvoicePreviewTags.LINES_TABLE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Total HT").performScrollTo().assertIsDisplayed()

        invoice.lines.forEachIndexed { index, line ->
            composeRule.onNodeWithTag(InvoicePreviewTags.lineTotalHt(index))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithContentDescription(line.totalHt.format(AppLanguage.FR))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun sheetSections_followTheVerticalOrderOfAPrintedInvoice() {
        renderSheet()

        val issuer = composeRule.onNodeWithTag(InvoicePreviewTags.ISSUER_BLOCK)
            .performScrollTo().getUnclippedBoundsInRoot()
        val metadata = composeRule.onNodeWithTag(InvoicePreviewTags.METADATA_BLOCK)
            .performScrollTo().getUnclippedBoundsInRoot()
        val table = composeRule.onNodeWithTag(InvoicePreviewTags.LINES_TABLE)
            .performScrollTo().getUnclippedBoundsInRoot()
        val totals = composeRule.onNodeWithTag(InvoicePreviewTags.TOTALS_BLOCK)
            .performScrollTo().getUnclippedBoundsInRoot()
        val legal = composeRule.onNodeWithTag(InvoicePreviewTags.LEGAL_BLOCK)
            .performScrollTo().getUnclippedBoundsInRoot()

        assertTrue(issuer.top < metadata.top, "en-tête au-dessus des métadonnées")
        assertTrue(metadata.top < table.top, "métadonnées au-dessus du tableau")
        assertTrue(table.top < totals.top, "tableau au-dessus des totaux")
        assertTrue(totals.top < legal.top, "totaux au-dessus des mentions légales")
    }

    @Test
    fun totalsBlock_isAlignedToTheRightMarginOfTheSheet() {
        renderSheet()

        val sheet = composeRule.onNodeWithTag(InvoicePreviewTags.SHEET).getUnclippedBoundsInRoot()
        val totals = composeRule.onNodeWithTag(InvoicePreviewTags.TOTALS_BLOCK)
            .performScrollTo().getUnclippedBoundsInRoot()

        // Bloc de totaux aligné sur la marge droite de la feuille, avec une tolérance de
        // quelques dp pour tenir compte du padding interne du cartouche.
        val rightGap = sheet.right - totals.right
        val rightPaddingTolerance = 24.dp
        assertTrue(rightGap <= rightPaddingTolerance, "les totaux sont alignés sur la marge droite de la feuille")
        assertTrue(totals.bottom - totals.top >= 48.dp, "cartouche de totaux ≥ 48dp de haut")
    }

    @Test
    fun exportsAScreenshotForVisualAudit() {
        renderSheet()
        composeRule.onNodeWithTag(InvoicePreviewTags.SHEET).assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(InvoicePreviewTags.SHEET).captureToImage().asAndroidBitmap()

        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("screenshots")!!
            .apply { mkdirs() }
        val file = File(dir, "US14_mobile_invoice_preview_${Build.MODEL}.png".replace(' ', '_'))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("[screenshot] ${file.absolutePath}")
        assertTrue(file.exists() && file.length() > 0L, "la capture d'écran doit être écrite et non vide")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "le bitmap capturé doit avoir des dimensions")
    }
}
