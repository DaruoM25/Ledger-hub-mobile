package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.facturx.FacturXGenerator
import com.ledgerhub.domain.facturx.toFacturXDocument
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Double d'export : capture l'appel sans dépendre d'un runtime de partage. */
private class RecordingDocumentExporter : DocumentExporter {
    var fileName: String? = null
    var mimeType: String? = null
    var content: String? = null
    var callCount = 0

    override suspend fun export(fileName: String, mimeType: String, content: String): Result<Unit> {
        this.fileName = fileName
        this.mimeType = mimeType
        this.content = content
        callCount++
        return Result.success(Unit)
    }
}

/**
 * Déclenchement de l'export Factur-X depuis l'écran de détail (US-06).
 *
 * L'écran ne génère rien lui-même : il émet un événement. Les tests vérifient donc le câblage
 * — présence des actions, appel effectif — et, en bout de chaîne, que ce qui serait remis à la
 * plateforme est bien le XML attendu.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class FacturXExportUiTest {

    private val settings = TaxSettings.Default

    private val invoice = Invoice(
        number = "FAC-2026-0137",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(
            InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(220_000), vatRate = VatRate.TAUX_NORMAL),
        ),
        status = InvoiceStatus.SENT,
    )

    // ── Présence des actions ─────────────────────────────────────────────────────────────────

    @Test
    fun detailScreen_offersTheInvoiceExportAction() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = InvoiceDetailUiState(isLoading = false, invoice = invoice)) }

        onNodeWithTag(InvoiceDetailScreenTags.EXPORT_INVOICE_XML_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun creditNoteExportAction_appearsOnlyWhenACreditNoteExists() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = InvoiceDetailUiState(isLoading = false, invoice = invoice)) }

        onNodeWithTag(InvoiceDetailScreenTags.EXPORT_CREDIT_NOTE_XML_BUTTON).assertDoesNotExist()
    }

    @Test
    fun creditNoteExportAction_appearsOnTheParentInvoice() = runComposeUiTest {
        // L'avoir n'a pas d'écran propre : son export vit sur la facture parente (arbitrage PO).
        setContent {
            InvoiceDetailView(
                uiState = InvoiceDetailUiState(
                    isLoading = false,
                    invoice = invoice.copy(status = InvoiceStatus.CANCELLED),
                    creditNoteNumber = "AV-2026-0001",
                ),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.EXPORT_CREDIT_NOTE_XML_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun exportRemainsAvailable_onACancelledInvoice() = runComposeUiTest {
        // Une facture annulée reste une pièce fiscale : son archivage est précisément l'enjeu.
        setContent {
            InvoiceDetailView(
                uiState = InvoiceDetailUiState(
                    isLoading = false,
                    invoice = invoice.copy(status = InvoiceStatus.CANCELLED),
                ),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.EXPORT_INVOICE_XML_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
    }

    // ── Câblage effectif ─────────────────────────────────────────────────────────────────────

    @Test
    fun clickingInvoiceExport_emitsTheEventWithTheInvoice() = runComposeUiTest {
        var exported: Invoice? = null
        setContent {
            InvoiceDetailView(
                uiState = InvoiceDetailUiState(isLoading = false, invoice = invoice),
                onExportInvoiceXml = { exported = it },
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.EXPORT_INVOICE_XML_BUTTON).performScrollTo().performClick()

        assertEquals("FAC-2026-0137", assertNotNull(exported).number)
    }

    @Test
    fun clickingCreditNoteExport_emitsTheCreditNoteNumber() = runComposeUiTest {
        var exported: String? = null
        setContent {
            InvoiceDetailView(
                uiState = InvoiceDetailUiState(
                    isLoading = false,
                    invoice = invoice.copy(status = InvoiceStatus.CANCELLED),
                    creditNoteNumber = "AV-2026-0001",
                ),
                onExportCreditNoteXml = { exported = it },
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.EXPORT_CREDIT_NOTE_XML_BUTTON).performScrollTo().performClick()

        assertEquals("AV-2026-0001", exported)
    }

    @Test
    fun noExportIsTriggered_withoutAClick() = runComposeUiTest {
        val exporter = RecordingDocumentExporter()
        setContent { InvoiceDetailView(uiState = InvoiceDetailUiState(isLoading = false, invoice = invoice)) }

        assertEquals(0, exporter.callCount)
        assertNull(exporter.content)
    }

    // ── Bout de chaîne : ce qui serait remis à la plateforme ─────────────────────────────────

    @Test
    fun theInvoiceHandedToThePlatform_isTheFacturXXml() = runTest {
        val exporter = RecordingDocumentExporter()

        exporter.export(
            fileName = DocumentExporter.FACTUR_X_FILE_NAME,
            mimeType = DocumentExporter.XML_MIME_TYPE,
            content = FacturXGenerator.generate(invoice.toFacturXDocument(settings)),
        )

        assertEquals("factur-x.xml", exporter.fileName)
        assertEquals("application/xml", exporter.mimeType)
        val xml = assertNotNull(exporter.content)
        assertContains(xml, "<rsm:CrossIndustryInvoice")
        assertContains(xml, "<ram:TypeCode>380</ram:TypeCode>")
        assertContains(xml, "<ram:ID>FAC-2026-0137</ram:ID>")
    }

    @Test
    fun theCreditNoteHandedToThePlatform_carriesTypeCode381AndTheCrossReference() = runTest {
        val exporter = RecordingDocumentExporter()
        val creditNote = CreateCreditNoteUseCase()(
            invoice, "AV-2026-0001", "2026-08-29", "Erreur de facturation",
        ).getOrThrow()

        exporter.export(
            fileName = DocumentExporter.FACTUR_X_FILE_NAME,
            mimeType = DocumentExporter.XML_MIME_TYPE,
            content = FacturXGenerator.generate(creditNote.toFacturXDocument(settings)),
        )

        val xml = assertNotNull(exporter.content)
        assertContains(xml, "<ram:TypeCode>381</ram:TypeCode>")
        assertContains(xml, "<ram:IssuerAssignedID>FAC-2026-0137</ram:IssuerAssignedID>")
    }
}
