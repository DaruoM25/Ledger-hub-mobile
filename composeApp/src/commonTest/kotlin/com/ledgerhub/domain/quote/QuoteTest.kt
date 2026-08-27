package com.ledgerhub.domain.quote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuoteTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")
    private val line = QuoteLine("Prestation", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL)

    private fun quote(status: QuoteStatus, lines: List<QuoteLine> = listOf(line)) = Quote(
        number = "DEV-2026-001",
        issueDate = "2026-08-03",
        validityDate = "2026-09-03",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    // ── Invariant : au moins une ligne ──────────────────────────────────────

    @Test
    fun quote_withNoLines_throws() {
        assertFailsWith<IllegalArgumentException> { quote(QuoteStatus.DRAFT, lines = emptyList()) }
    }

    // ── Cycle de vie réglementaire : Brouillon -> Envoyé -> Accepté / Refusé ──

    @Test
    fun draftQuote_isEditable_andCanBeDeleted_butNotConvertible() {
        val draft = quote(QuoteStatus.DRAFT)
        assertTrue(draft.isEditable)
        assertTrue(canDelete(draft))
        assertFalse(draft.isConvertibleToInvoice)
    }

    @Test
    fun sentQuote_isNotEditable_andCannotBeDeleted_norConvertible() {
        val sent = quote(QuoteStatus.SENT)
        assertFalse(sent.isEditable)
        assertFalse(canDelete(sent))
        assertFalse(sent.isConvertibleToInvoice)
    }

    @Test
    fun acceptedQuote_isConvertibleToInvoice_butNotEditable() {
        val accepted = quote(QuoteStatus.ACCEPTED)
        assertFalse(accepted.isEditable)
        assertFalse(canDelete(accepted))
        assertTrue(accepted.isConvertibleToInvoice)
    }

    @Test
    fun rejectedQuote_isNeitherEditable_norConvertible() {
        val rejected = quote(QuoteStatus.REJECTED)
        assertFalse(rejected.isEditable)
        assertFalse(rejected.isConvertibleToInvoice)
    }

    // ── Totaux agrégés (mêmes règles que Invoice — voir InvoiceTest) ────────

    @Test
    fun quoteTotals_singleLine_matchLineTotal() {
        val q = quote(QuoteStatus.DRAFT)
        // 2 * 50.00 = 100.00 HT, TVA 20% = 20.00, TTC = 120.00
        assertEquals(10000L, q.totalHt.cents)
        assertEquals(2000L, q.totalVat.cents)
        assertEquals(12000L, q.totalTtc.cents)
    }

    @Test
    fun quoteTotals_multipleLines_differentRates_produceSeparateBreakdown() {
        val lines = listOf(
            QuoteLine("Prestation 20%", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL),
            QuoteLine("Livre 5.5%", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),
        )
        val q = quote(QuoteStatus.DRAFT, lines)

        // HT = 120.00, TVA = 20.00 (sur 100) + 1.10 (sur 20 à 5.5%) = 21.10, TTC = 141.10
        assertEquals(12000L, q.totalHt.cents)
        assertEquals(2110L, q.totalVat.cents)
        assertEquals(14110L, q.totalTtc.cents)
        assertEquals(2, q.vatBreakdown.size)
    }
}
