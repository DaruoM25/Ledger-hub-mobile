package com.ledgerhub.presentation.dashboard

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.time.FixedClock
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3 — audit visuel de l'activité commerciale des devis (US-12) sur émulateur Pixel 5
 * API 35 (`connectedDebugAndroidTest`) : grille KPI 2×2, position de la section « Devis à
 * relancer », et export d'une capture d'écran.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DashboardQuotesActivityInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Horloge figée : un délai avant échéance ne se teste pas contre la date d'exécution. */
    private val clock = FixedClock("2026-08-30T09:00:00Z")

    private fun renderDashboard() {
        composeRule.setContent {
            LedgerHubTheme {
                DashboardScreen(
                    viewModel = DashboardViewModel(
                        invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = 0L),
                        creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 0L),
                        quoteRepository = MockQuoteRepository(simulatedDelayMillis = 0L),
                        clock = clock,
                    ),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(DashboardTags.KPI_QUOTES_PENDING).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /** Enregistre [bitmap] dans les fichiers externes de l'application pour extraction via adb root. */
    private fun exportScreenshot(bitmap: Bitmap, name: String) {
        val appDir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("screenshots")!!
            .apply { mkdirs() }
        val appFile = File(appDir, name)
        appFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        println("[screenshot] ${appFile.absolutePath}")
        assertTrue(appFile.exists() && appFile.length() > 0L, "capture applicative écrite et non vide")
    }

    @Test
    fun kpiCards_areLaidOutAsATwoByTwoGrid() {
        renderDashboard()

        composeRule.onNodeWithTag(DashboardTags.KPI_GRID).performScrollTo()
        val collected = composeRule.onNodeWithTag(DashboardTags.COLLECTED_CARD).getUnclippedBoundsInRoot()
        val pending = composeRule.onNodeWithTag(DashboardTags.PENDING_CARD).getUnclippedBoundsInRoot()
        val issued = composeRule.onNodeWithTag(DashboardTags.ISSUED_CARD).getUnclippedBoundsInRoot()
        val quotes = composeRule.onNodeWithTag(DashboardTags.KPI_QUOTES_PENDING).getUnclippedBoundsInRoot()
        val grid = composeRule.onNodeWithTag(DashboardTags.KPI_GRID).getUnclippedBoundsInRoot()

        // 1. Deux cartes par ligne, à la même hauteur.
        assertTrue(collected.top == pending.top, "1ʳᵉ ligne alignée (${collected.top} vs ${pending.top})")
        assertTrue(issued.top == quotes.top, "2ᵉ ligne alignée (${issued.top} vs ${quotes.top})")

        // 2. Deux lignes empilées : la seconde commence sous la première.
        assertTrue(issued.top >= collected.bottom, "2ᵉ ligne sous la 1ʳᵉ")

        // 3. Colonne de gauche puis colonne de droite.
        assertTrue(collected.left < pending.left, "encaissé à gauche de en-attente")
        assertTrue(issued.left < quotes.left, "émises à gauche de devis-en-attente")

        // 4. Chaque carte occupe environ la moitié de la grille (gouttière de 12 dp).
        val gridWidth = grid.right - grid.left
        listOf(collected, pending, issued, quotes).forEach { card ->
            val cardWidth = card.right - card.left
            assertTrue(cardWidth < gridWidth * 0.6f, "carte en demi-largeur (mesuré $cardWidth / $gridWidth)")
            assertTrue(cardWidth > gridWidth * 0.4f, "carte pas trop étroite (mesuré $cardWidth / $gridWidth)")
        }

        // 5. Hauteur minimale garantissant la lisibilité du montant et de la légende.
        listOf(collected, pending, issued, quotes).forEach { card ->
            val cardHeight = card.bottom - card.top
            assertTrue(cardHeight >= 88.dp, "carte KPI ≥ 88dp (mesuré $cardHeight)")
        }
    }

    @Test
    fun quotesToFollowUpSection_sitsBelowTheRecentInvoices() {
        renderDashboard()

        // Mesurer les deux blocs dans le même état de défilement afin de comparer
        // leurs coordonnées sans que performScrollTo ne déplace le viewport entre mesures.
        val recent = composeRule.onNodeWithTag(DashboardTags.RECENT_ACTIVITY_LIST).getUnclippedBoundsInRoot()
        val followUp = composeRule.onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_SECTION).getUnclippedBoundsInRoot()

        composeRule.onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_LIST).assertIsDisplayed()
        // La relance commerciale se lit dans le prolongement de l'activité facturée.
        assertTrue(followUp.top > recent.top, "section de relance sous les factures récentes")

        // La ligne du devis envoyé porte bien son délai.
        composeRule.onNodeWithTag(DashboardTags.quoteFollowUpTag("DEV-2026-002"))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.quoteFollowUpDeadlineTag("DEV-2026-002")).assertIsDisplayed()
    }

    @Test
    fun exportsDashboardScreenshotForVisualAudit() {
        renderDashboard()

        composeRule.onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_SECTION).performScrollTo().assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(DashboardTags.SCREEN).captureToImage().asAndroidBitmap()

        exportScreenshot(bitmap, "US12_dashboard_quotes_activity_${Build.MODEL}.png".replace(' ', '_'))
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "le bitmap capturé doit avoir des dimensions")
    }
}
