package com.ledgerhub.presentation.invoices

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.audit.AuditMilestoneId
import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.components.AuditTrailTags
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau N3b US-28 : parcours de cycle de vie et preuve visuelle de la Piste d'Audit Fiable sur
 * appareil Android réel.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Us28InvoiceLifecycleN3bInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val invoiceNumber = "FAC-2026-0280"
    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    @Test
    fun lifecycleAndAuditTrail_areValidatedAndCapturedOnDevice() {
        val auditTrail = listOf(
            AuditEntry(
                id = "us28-created",
                invoiceNumber = invoiceNumber,
                fromStatus = null,
                toStatus = InvoiceStatus.DRAFT,
                reason = null,
                createdAt = "2026-09-12T18:00:00Z",
            ),
            AuditEntry(
                id = "us28-deposited",
                invoiceNumber = invoiceNumber,
                fromStatus = InvoiceStatus.DRAFT,
                toStatus = InvoiceStatus.DEPOSITED,
                reason = null,
                createdAt = "2026-09-12T18:05:00Z",
            ),
            AuditEntry(
                id = "us28-approved",
                invoiceNumber = invoiceNumber,
                fromStatus = InvoiceStatus.DEPOSITED,
                toStatus = InvoiceStatus.APPROVED,
                reason = null,
                createdAt = "2026-09-12T18:10:00Z",
            ),
        )

        val approvedInvoice = Invoice(
            number = invoiceNumber,
            issueDate = "2026-09-12",
            issuer = issuer,
            recipient = recipient,
            lines = listOf(
                InvoiceLine("Prestation de conseil PPF", 1, Money(220_000), VatRate.TAUX_NORMAL),
            ),
            status = InvoiceStatus.APPROVED,
            dueDate = "2026-10-12",
        )

        composeRule.setContent {
            LedgerHubTheme {
                InvoiceDetailView(
                    uiState = InvoiceDetailUiState(
                        isLoading = false,
                        invoice = approvedInvoice,
                        auditTrail = auditTrail,
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuditTrailTags.PANEL)
            .performScrollTo()
            .assertIsDisplayed()
        AuditMilestoneId.entries.forEach { id ->
            composeRule.onNodeWithTag(AuditTrailTags.stepTag(id))
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        assertTrue(approvedInvoice.fingerprintSha256().isNotBlank())

        // Défilement jusqu'au bas complet de la vue pour s'assurer que l'intégralité de la Card d'audit (et son padding bas) est dans la zone visible
        composeRule.onNodeWithTag(InvoiceDetailScreenTags.BOTTOM_SPACER).performScrollTo()
        composeRule.onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.STATUS)).assertIsDisplayed()
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(InvoiceDetailScreenTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)!!,
            "us28_invoice_lifecycle_n3b.png",
        )
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-28 absente: ${output.absolutePath}")
        assertTrue(output.length() > 0L, "Capture US-28 vide: ${output.absolutePath}")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "Dimensions de capture invalides")

        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/us-28",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues)
            ?: error("Publication MediaStore US-28 impossible")
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .openOutputStream(mediaUri)
                ?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                ?: error("Ouverture MediaStore US-28 impossible")
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
