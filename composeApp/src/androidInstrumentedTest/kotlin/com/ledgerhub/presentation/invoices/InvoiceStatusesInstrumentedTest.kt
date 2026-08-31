package com.ledgerhub.presentation.invoices

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.InvoiceListTags
import com.ledgerhub.presentation.invoices.InvoiceListUiState
import com.ledgerhub.presentation.invoices.InvoiceListView
import com.ledgerhub.presentation.invoices.components.InvoiceCardTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b — audit visuel des statuts réglementaires PPF 2026 sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`) : les trois pastilles rendues côte à côte, plus l'export d'une
 * capture d'écran servant de preuve QA. Assertions en [assertIsDisplayed] uniquement, jamais
 * `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceStatusesInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun invoice(number: String, status: InvoiceStatus, issueDate: String) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Prestation de conseil", 1, Money(220_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    /** Les trois statuts PPF, plus un brouillon en contraste pour situer la nouvelle charte. */
    private val invoices = listOf(
        invoice("FAC-2026-0203", InvoiceStatus.REJECTED, "2026-08-22"),
        invoice("FAC-2026-0202", InvoiceStatus.APPROVED, "2026-08-18"),
        invoice("FAC-2026-0201", InvoiceStatus.DEPOSITED, "2026-08-10"),
        invoice("FAC-2026-0200", InvoiceStatus.DRAFT, "2026-08-04"),
    )

    private fun renderScreen() {
        composeRule.setContent {
            InvoiceListView(
                uiState = InvoiceListUiState(isLoading = false, invoices = invoices),
                onIntent = {},
                onInvoiceClick = {},
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theThreeRegulatoryBadges_areRenderedWithTheirOfficialWording() {
        renderScreen()

        listOf(
            "FAC-2026-0201" to "Déposée",
            "FAC-2026-0202" to "Approuvée par l'administration",
            "FAC-2026-0203" to "Rejetée par la plateforme",
        ).forEach { (number, wording) ->
            // LazyColumn : un statusTag hors viewport n'est pas composé, il faut donc faire
            // défiler la liste jusqu'à lui avant de l'interroger.
            composeRule.onNodeWithTag(InvoiceListTags.LIST)
                .performScrollToNode(hasTestTag(InvoiceCardTags.card(number)))
            composeRule.onNodeWithTag(InvoiceCardTags.statusTag(number), useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(wording, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun badgesKeepATouchFriendlyHeight_andStayInsideTheScreen() {
        renderScreen()

        val screen = composeRule.onNodeWithTag(InvoiceListTags.SCREEN).getUnclippedBoundsInRoot()
        listOf("FAC-2026-0201", "FAC-2026-0202", "FAC-2026-0203").forEach { number ->
            composeRule.onNodeWithTag(InvoiceListTags.LIST)
                .performScrollToNode(hasTestTag(InvoiceCardTags.card(number)))
            val badge = composeRule.onNodeWithTag(InvoiceCardTags.statusTag(number), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

            // Le point de 8dp plus le libellé imposent un minimum : une pastille écrasée
            // signalerait une contrainte de largeur mal absorbée par le libellé long.
            assertTrue(badge.bottom - badge.top >= 20.dp, "pastille $number ≥ 20dp de haut")
            assertTrue(badge.right <= screen.right, "pastille $number ne déborde pas à droite")
            assertTrue(badge.left >= screen.left, "pastille $number ne déborde pas à gauche")
        }
    }

    @Test
    fun exportsAScreenshotForVisualAudit() {
        renderScreen()
        composeRule.onNodeWithTag(InvoiceListTags.LIST)
            .performScrollToNode(hasTestTag(InvoiceCardTags.card("FAC-2026-0202")))
        composeRule.onNodeWithTag(InvoiceCardTags.statusTag("FAC-2026-0202"), useUnmergedTree = true).assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(InvoiceListTags.SCREEN).captureToImage().asAndroidBitmap()

        val dir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("screenshots")!!
            .apply { mkdirs() }
        val file = File(dir, "US13_mobile_invoices_statuses_${Build.MODEL}.png".replace(' ', '_'))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val sharedFile = File("/sdcard/Download", file.name).apply { parentFile?.mkdirs() }
        sharedFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("[screenshot] ${file.absolutePath}")
        assertTrue(file.exists() && file.length() > 0L, "la capture d'écran doit être écrite et non vide")
        assertTrue(sharedFile.exists() && sharedFile.length() > 0L, "la capture partagée doit être écrite et non vide")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "le bitmap capturé doit avoir des dimensions")
    }
}
