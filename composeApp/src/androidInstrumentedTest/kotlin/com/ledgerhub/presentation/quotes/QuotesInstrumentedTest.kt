package com.ledgerhub.presentation.quotes

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import com.ledgerhub.presentation.quoteform.QuoteFormScreen
import com.ledgerhub.presentation.quoteform.QuoteFormTags
import com.ledgerhub.presentation.quoteform.QuoteFormViewModel
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Tests d'intégration instrumentés sur terminal physique (Niveau N3a & N3b)
 * pour le module Devis (US-07, US-08, US-09).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class QuotesInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.Before
    fun setUp() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun exportScreenshot(bitmap: Bitmap, name: String) {
        val appDir = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("screenshots")!!
            .apply { mkdirs() }
        val appFile = File(appDir, name)
        appFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        try {
            val downloadDir = File("/sdcard/Download")
            if (downloadDir.exists()) {
                val downloadFile = File(downloadDir, name)
                downloadFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                println("[screenshot-download] ${downloadFile.absolutePath}")
            }
        } catch (e: Throwable) {
            println("[screenshot-download-error] ${e.message}")
        }

        println("[screenshot] ${appFile.absolutePath}")
        assertTrue(appFile.exists() && appFile.length() > 0L, "capture applicative écrite et non vide")
    }

    @Test
    fun quotesView_rendersNominalList_andCapturesScreenshot() {
        val viewModel = QuotesViewModel(
            quoteRepository = MockQuoteRepository(simulatedDelayMillis = 0L),
            convertQuoteToInvoiceUseCase = ConvertQuoteToInvoiceUseCase(),
            submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 0L)),
        )

        composeRule.setContent {
            LedgerHubTheme {
                QuotesView(viewModel = viewModel)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(QuotesTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(QuotesTags.filterChipTag(QuoteStatusFilter.TOUS)).assertIsDisplayed()

        // Capture d'écran nominale QuotesView (N3b)
        val bitmap = composeRule.onNodeWithTag(QuotesTags.SCREEN).captureToImage().asAndroidBitmap()
        exportScreenshot(bitmap, "US07_quotes_view_nominal.png")
    }

    @Test
    fun quoteForm_rendersNominalState_andCapturesScreenshot() {
        val viewModel = QuoteFormViewModel(
            submitQuoteUseCase = com.ledgerhub.domain.quote.SubmitQuoteUseCase(MockQuoteRepository(simulatedDelayMillis = 0L)),
        )

        composeRule.setContent {
            LedgerHubTheme {
                QuoteFormScreen(viewModel = viewModel)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(QuoteFormTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(QuoteFormTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(QuoteFormTags.QUOTE_NUMBER).assertIsDisplayed()

        // Capture d'écran nominale QuoteFormScreen (N3b)
        val bitmap = composeRule.onNodeWithTag(QuoteFormTags.SCREEN).captureToImage().asAndroidBitmap()
        exportScreenshot(bitmap, "US09_quote_form_nominal.png")
    }

    @Test
    fun quotesView_filterAndConvertWorkflow() {
        val viewModel = QuotesViewModel(
            quoteRepository = MockQuoteRepository(simulatedDelayMillis = 0L),
            convertQuoteToInvoiceUseCase = ConvertQuoteToInvoiceUseCase(),
            submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 0L)),
        )

        composeRule.setContent {
            LedgerHubTheme {
                QuotesView(viewModel = viewModel)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        // Vérification affichage badge et bouton convertir sur devis accepté
        composeRule.onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-003")))
        composeRule.onNodeWithTag(QuotesTags.statusBadgeTag("DEV-2026-003")).assertIsDisplayed()
        composeRule.onNodeWithTag(QuotesTags.convertButtonTag("DEV-2026-003")).assertIsDisplayed()

        // Clic sur convertir
        composeRule.onNodeWithTag(QuotesTags.convertButtonTag("DEV-2026-003")).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(QuotesTags.convertedInvoiceTag("DEV-2026-003")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(QuotesTags.convertedInvoiceTag("DEV-2026-003")).assertIsDisplayed()
    }
}
