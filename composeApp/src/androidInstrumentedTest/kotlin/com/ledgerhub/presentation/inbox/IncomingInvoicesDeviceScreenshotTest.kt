package com.ledgerhub.presentation.inbox

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.inbox.InboxRepository
import com.ledgerhub.domain.inbox.ProcessReceivedInvoiceUseCase
import com.ledgerhub.domain.inbox.ReceivedInvoice
import com.ledgerhub.domain.inbox.ReceivedInvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau N3b US-33 : Qualification sur terminal physique (Samsung S23+) de l'Inbox & Détection des Doublons.
 * Capture de preuve visuelle enregistrée dans Pictures/us-33/01_n3b_incoming_invoices.png.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class IncomingInvoicesDeviceScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakeInboxRepository : InboxRepository {
        val invoices = mutableListOf<ReceivedInvoice>()

        override suspend fun saveReceivedInvoice(invoice: ReceivedInvoice): Result<Unit> {
            invoices.removeAll { it.id == invoice.id }
            invoices.add(invoice)
            return Result.success(Unit)
        }

        override suspend fun fetchReceivedInvoices(): Result<List<ReceivedInvoice>> = Result.success(invoices.toList())

        override suspend fun getReceivedInvoiceById(id: String): Result<ReceivedInvoice?> {
            return Result.success(invoices.find { it.id == id })
        }

        override suspend fun findByFileHash(fileHash: String): Result<ReceivedInvoice?> {
            return Result.success(invoices.find { it.fileHash == fileHash })
        }

        override suspend fun findDuplicateTriptych(
            supplierSiren: String,
            invoiceNumber: String,
            totalTtcCents: Long,
            excludeId: String,
        ): Result<ReceivedInvoice?> {
            return Result.success(
                invoices.find {
                    it.supplierSiren == supplierSiren &&
                        it.invoiceNumber == invoiceNumber &&
                        it.totalTtc.cents == totalTtcCents &&
                        it.id != excludeId
                }
            )
        }

        override suspend fun updateStatus(
            id: String,
            status: ReceivedInvoiceStatus,
            duplicateReason: String?,
        ): Result<Unit> {
            val idx = invoices.indexOfFirst { it.id == id }
            if (idx != -1) {
                val current = invoices[idx]
                invoices[idx] = current.copy(status = status, duplicateReason = duplicateReason)
            }
            return Result.success(Unit)
        }
    }

    @Test
    fun captureIncomingInvoicesScreenshotOnDevice() {
        val repo = FakeInboxRepository()
        val invoice1 = ReceivedInvoice(
            id = "REC-001",
            supplierName = "Cloud Provider SAS",
            supplierSiren = "555666777",
            supplierSiret = "55566677700021",
            invoiceNumber = "INV-2026-881",
            issueDate = "2026-09-14",
            dueDate = "2026-10-14",
            totalHt = Money(150_000),
            totalVat = Money(30_000),
            totalTtc = Money(180_000),
            fileHash = "hash-legit-01",
            status = ReceivedInvoiceStatus.APPROVED,
            receivedAt = "2026-09-14T09:00:00",
        )
        val invoice2 = ReceivedInvoice(
            id = "REC-002",
            supplierName = "Bureau Expert Comptable",
            supplierSiren = "888999000",
            supplierSiret = "88899900000019",
            invoiceNumber = "HON-2026-042",
            issueDate = "2026-09-14",
            dueDate = "2026-09-30",
            totalHt = Money(85_000),
            totalVat = Money(17_000),
            totalTtc = Money(102_000),
            fileHash = "hash-legit-02",
            status = ReceivedInvoiceStatus.RECEIVED,
            receivedAt = "2026-09-14T10:15:00",
        )
        val invoiceDuplicate = ReceivedInvoice(
            id = "REC-003",
            supplierName = "Bureau Expert Comptable",
            supplierSiren = "888999000",
            supplierSiret = "88899900000019",
            invoiceNumber = "HON-2026-042",
            issueDate = "2026-09-14",
            dueDate = "2026-09-30",
            totalHt = Money(85_000),
            totalVat = Money(17_000),
            totalTtc = Money(102_000),
            fileHash = "hash-dup-03",
            status = ReceivedInvoiceStatus.DUPLICATE_ALERT,
            duplicateReason = "Doublon détecté : Fournisseur (888999000), Facture (HON-2026-042), Total (1020.0 €)",
            receivedAt = "2026-09-14T11:00:00",
        )

        repo.invoices.add(invoice1)
        repo.invoices.add(invoice2)
        repo.invoices.add(invoiceDuplicate)

        val useCase = ProcessReceivedInvoiceUseCase(repo)
        val viewModel = IncomingInvoicesViewModel(repo, useCase)

        composeRule.setContent {
            LedgerHubTheme {
                IncomingInvoicesScreen(viewModel = viewModel)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(IncomingInvoicesTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(IncomingInvoicesTags.DUPLICATE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(IncomingInvoicesTags.KPI_RECEIVED).assertIsDisplayed()
        composeRule.onNodeWithTag(IncomingInvoicesTags.KPI_ALERTS).assertIsDisplayed()
        composeRule.onNodeWithTag(IncomingInvoicesTags.KPI_APPROVED).assertIsDisplayed()

        // Capture d'écran haute fidélité pour qualification N3b
        val bitmap = composeRule.onNodeWithTag(IncomingInvoicesTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)!!,
            "01_n3b_incoming_invoices.png",
        )
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-33 absente: ${output.absolutePath}")
        assertTrue(output.length() > 0L, "Capture US-33 vide: ${output.absolutePath}")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "Dimensions de capture invalides")

        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/us-33",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues)
            ?: error("Publication MediaStore US-33 impossible")
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .openOutputStream(mediaUri)
                ?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                ?: error("Ouverture MediaStore US-33 impossible")
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
