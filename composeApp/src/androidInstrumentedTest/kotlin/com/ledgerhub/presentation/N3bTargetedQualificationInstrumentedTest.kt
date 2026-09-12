package com.ledgerhub.presentation

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.data.directory.MockDirectoryRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.data.subscription.MockSubscriptionRepository
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import com.ledgerhub.domain.subscription.PremiumFeature
import com.ledgerhub.presentation.directory.DirectoryScreen
import com.ledgerhub.presentation.directory.DirectoryTags
import com.ledgerhub.presentation.directory.DirectoryViewModel
import com.ledgerhub.domain.directory.ResolveDirectoryEntryUseCase
import com.ledgerhub.presentation.quoteform.QuoteFormField
import com.ledgerhub.presentation.quoteform.QuoteFormScreen
import com.ledgerhub.presentation.quoteform.QuoteFormTags
import com.ledgerhub.presentation.quoteform.QuoteFormViewModel
import com.ledgerhub.presentation.subscription.PaywallScreen
import com.ledgerhub.presentation.subscription.PaywallTags
import com.ledgerhub.presentation.subscription.PaywallViewModel
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class N3bTargetedQualificationInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun saveScreenshot(tag: String, fileName: String) {
        val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)!!
            .apply { mkdirs() }
        val file = File(directory, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.exists() && file.length() > 0L, "Capture absente ou vide: ${file.absolutePath}")

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/n3b")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Impossible de créer la capture MediaStore: $fileName")
        try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: error("Impossible d'ouvrir la capture MediaStore: $fileName")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Test
    fun dgfipDirectory_resolvesRequestedSiret_andCapturesResult() {
        val repository = MockDirectoryRepository(simulatedDelayMillis = 0L)
        composeRule.setContent {
            LedgerHubTheme {
                DirectoryScreen(
                    DirectoryViewModel(
                        repository,
                        ResolveDirectoryEntryUseCase(repository),
                        UnconfinedTestDispatcher(),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(DirectoryTags.SEARCH_FIELD).performTextInput("38012986648625")
        composeRule.onNodeWithTag(DirectoryTags.SEARCH_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(DirectoryTags.RESULT_CARD).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(DirectoryTags.RESULT_CARD).assertIsDisplayed()
        saveScreenshot(DirectoryTags.SCREEN, "n3b_dgfip.png")
    }

    @Test
    fun quoteForm_opensWithoutValidationErrors_andCapturesNominalState() {
        val viewModel = QuoteFormViewModel(
            submitQuoteUseCase = com.ledgerhub.domain.quote.SubmitQuoteUseCase(
                MockQuoteRepository(simulatedDelayMillis = 0L),
            ),
        )
        composeRule.setContent { LedgerHubTheme { QuoteFormScreen(viewModel) } }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(QuoteFormTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(QuoteFormTags.SCREEN).assertIsDisplayed()
        QuoteFormField.entries.forEach { field ->
            composeRule.onAllNodesWithTag(QuoteFormTags.errorTagFor(field)).assertCountEquals(0)
        }
        saveScreenshot(QuoteFormTags.SCREEN, "n3b_quote.png")
    }

    @Test
    fun paywall_acceptsDevpostCode_andCapturesProActivation() {
        val repository = MockSubscriptionRepository()
        val viewModel = PaywallViewModel(
            subscriptionRepository = repository,
            reasonFeature = PremiumFeature.UNLIMITED_INVOICES,
            dispatcher = UnconfinedTestDispatcher(),
        )
        composeRule.setContent { LedgerHubTheme { PaywallScreen(viewModel) } }
        composeRule.onNodeWithTag(PaywallTags.PROMO_CODE_INPUT)
            .performScrollTo()
            .performTextInput("DEVPOST2026")
        composeRule.onNodeWithTag(PaywallTags.PROMO_CODE_SUBMIT).performScrollTo().performClick()
        composeRule.onNodeWithTag(PaywallTags.PROMO_SUCCESS_MESSAGE)
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(repository.currentStatus.isPro)
        saveScreenshot(PaywallTags.SCREEN, "n3b_paywall.png")
    }
}
