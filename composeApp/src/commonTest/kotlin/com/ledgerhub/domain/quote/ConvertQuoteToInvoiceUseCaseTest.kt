package com.ledgerhub.domain.quote

import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConvertQuoteToInvoiceUseCaseTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")
    private val useCase = ConvertQuoteToInvoiceUseCase()

    private fun quote(status: QuoteStatus, lines: List<QuoteLine>) = Quote(
        number = "DEV-2026-042",
        issueDate = "2026-08-01",
        validityDate = "2026-09-01",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    // ── Règle métier : seul un devis Accepté est convertible ────────────────

    @Test
    fun convert_draftQuote_fails() {
        val q = quote(QuoteStatus.DRAFT, listOf(QuoteLine("Prestation", 1, Money(1000), VatRate.TAUX_NORMAL)))

        val result = useCase(q, invoiceNumber = "F-2026-001", issueDate = "2026-08-06")

        assertTrue(result.isFailure)
        assertFailsWith<IllegalStateException> { result.getOrThrow() }
    }

    @Test
    fun convert_sentOrRejectedQuote_fails() {
        val sent = quote(QuoteStatus.SENT, listOf(QuoteLine("Prestation", 1, Money(1000), VatRate.TAUX_NORMAL)))
        val rejected = quote(QuoteStatus.REJECTED, listOf(QuoteLine("Prestation", 1, Money(1000), VatRate.TAUX_NORMAL)))

        assertTrue(useCase(sent, "F-2026-002", "2026-08-06").isFailure)
        assertTrue(useCase(rejected, "F-2026-003", "2026-08-06").isFailure)
    }

    // ── Le "bouton magique" : copie intégrale des lignes + piste d'audit ────

    @Test
    fun convert_acceptedQuote_producesDraftInvoice_withCopiedLines_andSourceQuoteId() {
        val lines = listOf(
            QuoteLine("Conseil", quantity = 2, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL),
            QuoteLine("Livre", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),
        )
        val q = quote(QuoteStatus.ACCEPTED, lines)

        val result = useCase(q, invoiceNumber = "F-2026-042", issueDate = "2026-08-06")

        assertTrue(result.isSuccess)
        val invoice = result.getOrThrow()

        assertEquals("F-2026-042", invoice.number)
        assertEquals("2026-08-06", invoice.issueDate)
        assertEquals(InvoiceStatus.DRAFT, invoice.status)
        assertEquals(q.number, invoice.sourceQuoteId) // piste d'audit fiscale devis -> facture
        assertEquals(q.issuer, invoice.issuer)
        assertEquals(q.recipient, invoice.recipient)

        assertEquals(2, invoice.lines.size)
        assertEquals("Conseil", invoice.lines[0].label)
        assertEquals(2, invoice.lines[0].quantity)
        assertEquals(10000L, invoice.lines[0].unitPriceHt.cents)
        assertEquals(VatRate.TAUX_NORMAL, invoice.lines[0].vatRate)
        assertEquals("Livre", invoice.lines[1].label)
        assertEquals(VatRate.TAUX_REDUIT, invoice.lines[1].vatRate)

        // Les totaux se recalculent à l'identique côté facture (mêmes lignes, même arithmétique Money).
        assertEquals(q.totalHt, invoice.totalHt)
        assertEquals(q.totalVat, invoice.totalVat)
        assertEquals(q.totalTtc, invoice.totalTtc)
    }

    @Test
    fun invoiceNotProducedFromConversion_hasNullSourceQuoteId() {
        // Contrôle négatif : une facture "normale" (pas issue d'une conversion) ne porte pas de sourceQuoteId.
        assertNull(
            com.ledgerhub.domain.invoice.Invoice(
                number = "F-2026-099",
                issueDate = "2026-08-06",
                issuer = issuer,
                recipient = recipient,
                lines = listOf(com.ledgerhub.domain.invoice.InvoiceLine("Prestation", 1, Money(1000), VatRate.TAUX_NORMAL)),
            ).sourceQuoteId
        )
    }
}
