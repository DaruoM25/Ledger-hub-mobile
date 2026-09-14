package com.ledgerhub.presentation.vault

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
import com.ledgerhub.domain.audit.sha256Hex
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.vault.DigitalArchive
import com.ledgerhub.domain.vault.GenerateInvoiceSealUseCase
import com.ledgerhub.domain.vault.PisteAuditEntry
import com.ledgerhub.domain.vault.VaultRepository
import com.ledgerhub.domain.vault.VerifyVaultIntegrityUseCase
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau N3b US-32 : Qualification sur terminal physique (Samsung S23+) du Coffre-Fort Numérique & Piste d'Audit Fiable.
 * Capture de preuve visuelle enregistrée dans Pictures/us-32/01_n3b_vault_archive.png.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class VaultArchiveDeviceScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeIssuer = Party(name = "Cabinet Conseil 2026", siren = "123456782", siret = "12345678200015")
    private val fakeClient = Party(name = "Société Alpha", siren = "987654321", siret = "98765432100010")

    private class FakeVaultRepository : VaultRepository {
        private val archives = mutableListOf<DigitalArchive>()
        private val auditTrail = mutableListOf<PisteAuditEntry>()

        override suspend fun saveArchive(archive: DigitalArchive): Result<Unit> {
            archives.removeAll { it.id == archive.id }
            archives.add(archive)
            return Result.success(Unit)
        }

        override suspend fun getArchiveByInvoiceNumber(invoiceNumber: String): Result<DigitalArchive?> {
            return Result.success(archives.find { it.invoiceNumber == invoiceNumber })
        }

        override suspend fun fetchArchives(): Result<List<DigitalArchive>> = Result.success(archives.toList())

        override suspend fun countSealedDocuments(): Result<Long> = Result.success(archives.size.toLong())

        override suspend fun appendAuditEntry(
            id: String,
            invoiceNumber: String?,
            action: String,
            details: String,
            timestamp: String,
        ): Result<PisteAuditEntry> {
            val prevChecksum = auditTrail.lastOrNull()?.checksum
            val checksum = sha256Hex("$id|$invoiceNumber|$action|$timestamp|$prevChecksum")
            val entry = PisteAuditEntry(
                id = id,
                invoiceNumber = invoiceNumber,
                action = action,
                details = details,
                timestamp = timestamp,
                previousChecksum = prevChecksum,
                checksum = checksum,
            )
            auditTrail.add(entry)
            return Result.success(entry)
        }

        override suspend fun fetchAuditTrail(): Result<List<PisteAuditEntry>> = Result.success(auditTrail.toList())

        override suspend fun getLatestAuditEntry(): Result<PisteAuditEntry?> = Result.success(auditTrail.lastOrNull())
    }

    private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @Test
    fun captureVaultArchiveScreenshotOnDevice() {
        val invoices = listOf(
            Invoice(
                number = "FAC-2026-0001",
                issueDate = "2026-09-14",
                issuer = fakeIssuer,
                recipient = fakeClient,
                status = InvoiceStatus.DEPOSITED,
                natureOperation = NatureOperation.LIVRAISON_BIENS,
                lines = listOf(
                    InvoiceLine(label = "Licence ERP Cloud", quantity = 1, unitPriceHt = Money(250_000), vatRate = VatRate.TAUX_NORMAL),
                ),
            ),
            Invoice(
                number = "FAC-2026-0002",
                issueDate = "2026-09-14",
                issuer = fakeIssuer,
                recipient = fakeClient,
                status = InvoiceStatus.PAID,
                natureOperation = NatureOperation.PRESTATION_SERVICES,
                lines = listOf(
                    InvoiceLine(label = "Audit Sécurité RGPD & Factur-X", quantity = 1, unitPriceHt = Money(180_000), vatRate = VatRate.TAUX_NORMAL),
                ),
            ),
        )

        val vaultRepo = FakeVaultRepository()
        val invoiceRepo = FakeInvoiceRepository(invoices)
        val generateSeal = GenerateInvoiceSealUseCase(vaultRepo, invoiceRepo)
        val verifyIntegrity = VerifyVaultIntegrityUseCase(vaultRepo, invoiceRepo)

        runBlocking {
            generateSeal("FAC-2026-0001")
            generateSeal("FAC-2026-0002")
        }

        val viewModel = VaultArchiveViewModel(
            vaultRepository = vaultRepo,
            generateInvoiceSealUseCase = generateSeal,
            verifyVaultIntegrityUseCase = verifyIntegrity,
            invoiceRepository = invoiceRepo,
        )

        composeRule.setContent {
            LedgerHubTheme {
                VaultArchiveScreen(viewModel = viewModel)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VaultArchiveTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(VaultArchiveTags.KPI_SEALED).assertIsDisplayed()
        composeRule.onNodeWithTag(VaultArchiveTags.KPI_INTEGRITY).assertIsDisplayed()
        composeRule.onNodeWithTag(VaultArchiveTags.KPI_STORAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(VaultArchiveTags.ARCHIVE_LIST).assertExists()

        // Capture d'écran haute fidélité pour qualification N3b
        val bitmap = composeRule.onNodeWithTag(VaultArchiveTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)!!,
            "01_n3b_vault_archive.png",
        )
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-32 absente: ${output.absolutePath}")
        assertTrue(output.length() > 0L, "Capture US-32 vide: ${output.absolutePath}")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "Dimensions de capture invalides")

        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/us-32",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues)
            ?: error("Publication MediaStore US-32 impossible")
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .openOutputStream(mediaUri)
                ?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                ?: error("Ouverture MediaStore US-32 impossible")
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
