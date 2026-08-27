package com.ledgerhub.domain.creditnote

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateCreditNoteUseCaseTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")
    private val useCase = CreateCreditNoteUseCase()

    private fun invoice(status: InvoiceStatus, lines: List<InvoiceLine> = listOf(
        InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL),
    )) = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    // ── Règle métier : seule une facture finalisée est annulable par avoir ──

    @Test
    fun createCreditNote_forDraftInvoice_fails() {
        val result = useCase(invoice(InvoiceStatus.DRAFT), number = "AV-1", issueDate = "2026-08-06", reason = "Erreur")
        assertTrue(result.isFailure)
        assertFailsWith<IllegalStateException> { result.getOrThrow() }
    }

    @Test
    fun createCreditNote_forAlreadyCancelledInvoice_fails() {
        val result = useCase(invoice(InvoiceStatus.CANCELLED), number = "AV-1", issueDate = "2026-08-06", reason = "Erreur")
        assertTrue(result.isFailure)
    }

    @Test
    fun createCreditNote_forValidatedSentOrPaidInvoice_succeeds() {
        listOf(InvoiceStatus.VALIDATED, InvoiceStatus.SENT, InvoiceStatus.PAID).forEach { status ->
            val result = useCase(invoice(status), number = "AV-1", issueDate = "2026-08-06", reason = "Erreur")
            assertTrue(result.isSuccess, "Le statut $status devrait être annulable par avoir")
        }
    }

    // ── Motif obligatoire ─────────────────────────────────────────────────────

    @Test
    fun createCreditNote_withBlankReason_fails() {
        val result = useCase(invoice(InvoiceStatus.VALIDATED), number = "AV-1", issueDate = "2026-08-06", reason = "")
        assertTrue(result.isFailure)
        assertFailsWith<IllegalArgumentException> { result.getOrThrow() }
    }

    // ── Le "bouton magique" inversé : inversion intégrale des montants ──────

    @Test
    fun createCreditNote_invertsAllAmounts_andKeepsAuditTrail() {
        val lines = listOf(
            InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Livre", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),
        )
        val original = invoice(InvoiceStatus.VALIDATED, lines)

        val result = useCase(original, number = "AV-2026-042", issueDate = "2026-08-06", reason = "Erreur tarifaire")

        assertTrue(result.isSuccess)
        val creditNote = result.getOrThrow()

        assertEquals("AV-2026-042", creditNote.number)
        assertEquals("2026-08-06", creditNote.issueDate)
        assertEquals(original.number, creditNote.invoiceId) // piste d'audit fiscale facture -> avoir
        assertEquals("Erreur tarifaire", creditNote.reason)
        assertEquals(original.issuer, creditNote.issuer)
        assertEquals(original.recipient, creditNote.recipient)

        // HT = 120.00, TVA = 21.10, TTC = 141.10 côté facture -> inversés côté avoir.
        assertEquals(-original.totalHt.cents, creditNote.totalHt.cents)
        assertEquals(-original.totalVat.cents, creditNote.totalVat.cents)
        assertEquals(-original.totalTtc.cents, creditNote.totalTtc.cents)
        assertEquals(-12000L, creditNote.totalHt.cents)
        assertEquals(-2110L, creditNote.totalVat.cents)
        assertEquals(-14110L, creditNote.totalTtc.cents)
    }
}
