package com.ledgerhub.presentation.degraded

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.degraded.SyncQueueEntry
import com.ledgerhub.domain.degraded.SyncStatus
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.InvoiceListTags
import com.ledgerhub.presentation.invoices.InvoiceListUiState
import com.ledgerhub.presentation.invoices.InvoiceListView
import com.ledgerhub.presentation.invoices.InvoiceStatusFilter
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.test.assertTrue

/**
 * Niveau N3b US-29 : Qualification sur terminal physique (Samsung S23+) du Mode Dégradé & Continuité Économique.
 * Capture de preuve visuelle enregistrée dans Pictures/us-29/us29_degraded_mode_n3b.png.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Us29DegradedModeN3bInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val invoiceNumber = "FAC-2026-0290"
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    @Test
    fun degradedModeAndSyncBatch_areValidatedAndCapturedOnDevice() {
        val degradedInvoice = Invoice(
            number = invoiceNumber,
            issueDate = "2026-09-13",
            issuer = issuer,
            recipient = recipient,
            lines = listOf(
                InvoiceLine("Prestation de secours informatique", 1, Money(150_000), VatRate.TAUX_NORMAL),
            ),
            status = InvoiceStatus.PENDING_REGULARIZATION,
            dueDate = "2026-10-13",
        )

        var syncClicked = false

        composeRule.setContent {
            LedgerHubTheme {
                InvoiceListView(
                    uiState = InvoiceListUiState(
                        invoices = listOf(degradedInvoice),
                        statusFilter = InvoiceStatusFilter.PENDING_REGULARIZATION,
                    ),
                    onIntent = {},
                    onInvoiceClick = {},
                    onCreateCreditNote = {},
                    syncQueueUiState = SyncQueueUiState(
                        pendingCount = 1L,
                        isSyncing = false,
                    ),
                    onSyncBatch = { syncClicked = true },
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Vérification de la présence de la carte de régularisation par lot
        composeRule.onNodeWithTag(DegradedModeTags.SYNC_BATCH_CARD)
            .assertIsDisplayed()

        // 2. Vérification du compteur de factures en attente
        composeRule.onNodeWithTag(DegradedModeTags.SYNC_BATCH_COUNT)
            .assertIsDisplayed()

        // 3. Action de déclenchement du lot de télétransmission
        composeRule.onNodeWithTag(DegradedModeTags.SYNC_BATCH_BUTTON)
            .assertIsDisplayed()
            .performClick()

        assertTrue(syncClicked, "Le bouton de régularisation par lot doit déclencher la synchronisation")
        composeRule.waitForIdle()

        // 4. Capture d'écran de qualification N3b
        val bitmap = composeRule.onNodeWithTag(InvoiceListTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)!!,
            "us29_degraded_mode_n3b.png",
        )
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-29 absente: ${output.absolutePath}")
        assertTrue(output.length() > 0L, "Capture US-29 vide: ${output.absolutePath}")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "Dimensions de capture invalides")

        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/us-29",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues)
            ?: error("Publication MediaStore US-29 impossible")
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .openOutputStream(mediaUri)
                ?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                ?: error("Ouverture MediaStore US-29 impossible")
            mediaValues.clear()
            mediaValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .update(mediaUri, mediaValues, null, null)
        } catch (error: Throwable) {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .delete(mediaUri, null, null)
            throw error
        }
    }
}
