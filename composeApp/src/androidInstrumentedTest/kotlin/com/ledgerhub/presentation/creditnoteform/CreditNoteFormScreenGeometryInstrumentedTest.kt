package com.ledgerhub.app.presentation.creditnoteform

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormTags
import com.ledgerhub.presentation.creditnoteform.CreditNoteFormViewModel
import android.graphics.Bitmap
import android.os.Build
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3 — audit visuel de [CreditNoteFormScreen] sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`) : disposition géométrique des blocs + export d'une capture
 * d'écran. Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CreditNoteFormScreenGeometryInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun sourceInvoice() = Invoice(
        number = "FAC-2026-0137",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(220_000), vatRate = VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
    )

    private fun renderScreen() {
        composeRule.setContent {
            CreditNoteFormScreen(
                viewModel = CreditNoteFormViewModel(
                    sourceInvoice = sourceInvoice(),
                    creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 0L),
                ),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun verticalOrderAndMinimumHeights_matchTheSpec() {
        renderScreen()

        // Chaque bloc est amené dans le viewport avant mesure.
        composeRule.onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo()

        val badge = composeRule.onNodeWithTag(CreditNoteFormTags.DRAFT_BADGE).performScrollTo().getUnclippedBoundsInRoot()
        val reference = composeRule.onNodeWithTag(CreditNoteFormTags.INVOICE_ID).performScrollTo().getUnclippedBoundsInRoot()
        val issueDate = composeRule.onNodeWithTag(CreditNoteFormTags.ISSUE_DATE).performScrollTo().getUnclippedBoundsInRoot()
        val reason = composeRule.onNodeWithTag(CreditNoteFormTags.REASON_SECTION).performScrollTo().getUnclippedBoundsInRoot()
        val lines = composeRule.onNodeWithTag(CreditNoteFormTags.LINES).performScrollTo().getUnclippedBoundsInRoot()
        val cartridge = composeRule.onNodeWithTag(CreditNoteFormTags.TOTALS_CARTRIDGE).performScrollTo().getUnclippedBoundsInRoot()
        val submit = composeRule.onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().getUnclippedBoundsInRoot()

        // 1. Ordre vertical strict des sept blocs (coordonnée `top` croissante).
        assertTrue(badge.top < reference.top, "badge au-dessus de la référence")
        assertTrue(reference.top < issueDate.top, "référence au-dessus de la date")
        assertTrue(issueDate.top < reason.top, "date au-dessus du motif")
        assertTrue(reason.top < lines.top, "motif au-dessus des lignes")
        assertTrue(lines.top < cartridge.top, "lignes au-dessus du cartouche")
        assertTrue(cartridge.top < submit.top, "cartouche au-dessus du bouton")

        // 2. Le bouton de validation est le dernier élément.
        listOf(badge, reference, issueDate, reason, lines, cartridge).forEach {
            assertTrue(it.bottom <= submit.bottom, "aucun bloc sous le bouton de validation")
        }

        // 3. Hauteurs minimales.
        val badgeHeight = badge.bottom - badge.top
        val cartridgeHeight = cartridge.bottom - cartridge.top
        val submitHeight = submit.bottom - submit.top
        assertTrue(badgeHeight >= 35.99.dp, "badge ≥ 36dp (mesuré $badgeHeight)")
        assertTrue(cartridgeHeight >= 88.dp, "cartouche ≥ 88dp (mesuré $cartridgeHeight)")
        assertTrue(submitHeight >= 48.dp, "bouton ≥ 48dp — cible tactile M3 (mesuré $submitHeight)")

        // 4. topBar collée en haut, contenu sous la topBar (inset respecté).
        val topBar = composeRule.onNodeWithTag(CreditNoteFormTags.TOP_BAR).getUnclippedBoundsInRoot()
        assertTrue(topBar.top <= 1.dp, "topBar collée au bord supérieur")
        assertTrue(badge.top >= topBar.bottom, "le contenu commence sous la topBar")

        // 5. Cartouche quasi pleine largeur (marge latérale ≈ 16dp).
        val screen = composeRule.onNodeWithTag(CreditNoteFormTags.SCREEN).getUnclippedBoundsInRoot()
        assertTrue(cartridge.left <= screen.left + 20.dp, "marge gauche du cartouche ≤ 20dp")
        assertTrue(cartridge.right >= screen.right - 20.dp, "marge droite du cartouche ≤ 20dp")
    }

    @Test
    fun exportsAScreenshotForVisualAudit() {
        renderScreen()
        composeRule.onNodeWithTag(CreditNoteFormTags.DRAFT_BADGE).performScrollTo().assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(CreditNoteFormTags.SCREEN).captureToImage().asAndroidBitmap()

        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("screenshots")!!
            .apply { mkdirs() }
        val file = File(dir, "US10_credit_note_form_${Build.MODEL}.png".replace(' ', '_'))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val sharedFile = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)!!,
            file.name,
        ).apply { parentFile?.mkdirs() }
        sharedFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("[screenshot] ${file.absolutePath}")
        assertTrue(file.exists() && file.length() > 0L, "la capture d'écran doit être écrite et non vide")
        assertTrue(sharedFile.exists() && sharedFile.length() > 0L, "la capture partagée doit être écrite et non vide")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "le bitmap capturé doit avoir des dimensions")
    }
}
