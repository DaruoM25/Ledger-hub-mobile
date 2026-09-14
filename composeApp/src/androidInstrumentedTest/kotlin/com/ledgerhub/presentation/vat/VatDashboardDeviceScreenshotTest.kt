package com.ledgerhub.presentation.vat

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
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.vat.CalculateVatMetricsUseCase
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau N3b US-31 : Qualification sur terminal physique (Samsung S23+) du module Analytique TVA & Déclaration 3310-CA3.
 * Capture de preuve visuelle enregistrée dans Pictures/us-31/01_n3b_vat_dashboard.png.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class VatDashboardDeviceScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeIssuer = Party(name = "Cabinet Conseil 2026", siren = "123456782", siret = "12345678200015")
    private val fakeClient = Party(name = "Société Alpha", siren = "987654321", siret = "98765432100010")

    private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @Test
    fun captureVatDashboardScreenshotOnDevice() {
        val invoices = listOf(
            Invoice(
                number = "FAC-2026-0001",
                issueDate = "2026-09-14",
                issuer = fakeIssuer,
                recipient = fakeClient,
                status = InvoiceStatus.PAID,
                natureOperation = NatureOperation.LIVRAISON_BIENS,
                lines = listOf(
                    InvoiceLine(label = "Matériel Informatique", quantity = 1, unitPriceHt = Money(150_000), vatRate = VatRate.TAUX_NORMAL),
                    InvoiceLine(label = "Maintenance", quantity = 1, unitPriceHt = Money(50_000), vatRate = VatRate.TAUX_INTERMEDIAIRE),
                ),
            ),
            Invoice(
                number = "FAC-2026-0002",
                issueDate = "2026-09-14",
                issuer = fakeIssuer,
                recipient = fakeClient,
                status = InvoiceStatus.DEPOSITED,
                natureOperation = NatureOperation.PRESTATION_SERVICES,
                optionTvaDebit = false,
                lines = listOf(
                    InvoiceLine(label = "Prestation d'audit", quantity = 1, unitPriceHt = Money(80_000), vatRate = VatRate.TAUX_NORMAL),
                ),
            ),
        )

        val useCase = CalculateVatMetricsUseCase(FakeInvoiceRepository(invoices))
        val viewModel = VatDashboardViewModel(useCase)

        composeRule.setContent {
            LedgerHubTheme {
                VatDashboardScreen(viewModel = viewModel)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VatDashboardTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(VatDashboardTags.KPI_COLLECTED_EXIGIBLE).assertIsDisplayed()
        composeRule.onNodeWithTag(VatDashboardTags.KPI_PENDING_COLLECTION).assertIsDisplayed()
        composeRule.onNodeWithTag(VatDashboardTags.CA3_SECTION).assertExists()

        // Capture d'écran haute fidélité pour qualification N3b
        val bitmap = composeRule.onNodeWithTag(VatDashboardTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null)!!,
            "01_n3b_vat_dashboard.png",
        )
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-31 absente: ${output.absolutePath}")
        assertTrue(output.length() > 0L, "Capture US-31 vide: ${output.absolutePath}")
        assertTrue(bitmap.width > 0 && bitmap.height > 0, "Dimensions de capture invalides")

        val mediaValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, output.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/us-31",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val mediaUri = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaValues)
            ?: error("Publication MediaStore US-31 impossible")
        try {
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
                .openOutputStream(mediaUri)
                ?.use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                ?: error("Ouverture MediaStore US-31 impossible")
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
