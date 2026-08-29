package com.ledgerhub.domain.invoice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvoiceTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")
    private val line = InvoiceLine("Prestation", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL)

    private fun invoice(status: InvoiceStatus, lines: List<InvoiceLine> = listOf(line)) = Invoice(
        number = "F-2026-001",
        issueDate = "2026-08-03",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    // ── Invariant : au moins une ligne ──────────────────────────────────────

    @Test
    fun invoice_withNoLines_throws() {
        assertFailsWith<IllegalArgumentException> { invoice(InvoiceStatus.DRAFT, lines = emptyList()) }
    }

    // ── Immutabilité : seul le statut Brouillon autorise modification/suppression ──

    @Test
    fun draftInvoice_isEditable_andCanBeDeleted() {
        val draft = invoice(InvoiceStatus.DRAFT)
        assertTrue(draft.isEditable)
        assertTrue(canDelete(draft))
    }

    @Test
    fun validatedInvoice_isNotEditable_andCannotBeDeleted() {
        val validated = invoice(InvoiceStatus.DEPOSITED)
        assertFalse(validated.isEditable)
        assertFalse(canDelete(validated))
    }

    @Test
    fun paidInvoice_cannotBeDeleted() {
        assertFalse(canDelete(invoice(InvoiceStatus.PAID)))
    }

    // ── Totaux agrégés sur une ligne unique (cas simple) ────────────────────

    @Test
    fun invoiceTotals_singleLine_matchLineTotal() {
        val inv = invoice(InvoiceStatus.DRAFT)
        // 2 * 50.00 = 100.00 HT, TVA 20% = 20.00, TTC = 120.00
        assertEquals(10000L, inv.totalHt.cents)
        assertEquals(2000L, inv.totalVat.cents)
        assertEquals(12000L, inv.totalTtc.cents)
    }

    // ── Totaux agrégés sur plusieurs lignes, même taux de TVA ───────────────

    @Test
    fun invoiceTotals_multipleLines_sameRate_aggregateCorrectly() {
        val lines = listOf(
            InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL), // 100.00
            InvoiceLine("Formation", quantity = 2, unitPriceHt = Money(2500), vatRate = VatRate.TAUX_NORMAL), // 50.00
        )
        val inv = invoice(InvoiceStatus.DRAFT, lines)

        // HT = 150.00, TVA 20% = 30.00, TTC = 180.00
        assertEquals(15000L, inv.totalHt.cents)
        assertEquals(3000L, inv.totalVat.cents)
        assertEquals(18000L, inv.totalTtc.cents)
        assertEquals(1, inv.vatBreakdown.size)
    }

    // ── Totaux agrégés sur plusieurs lignes, taux de TVA différents (ventilation) ──

    @Test
    fun invoiceTotals_multipleLines_differentRates_produceSeparateBreakdown() {
        val lines = listOf(
            InvoiceLine("Prestation 20%", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL),
            InvoiceLine("Livre 5.5%", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),
        )
        val inv = invoice(InvoiceStatus.DRAFT, lines)

        // HT = 120.00, TVA = 20.00 (sur 100) + 1.10 (sur 20 à 5.5%) = 21.10, TTC = 141.10
        assertEquals(12000L, inv.totalHt.cents)
        assertEquals(2110L, inv.totalVat.cents)
        assertEquals(14110L, inv.totalTtc.cents)

        assertEquals(2, inv.vatBreakdown.size)
        val normalBreakdown = inv.vatBreakdown.first { it.rate == VatRate.TAUX_NORMAL }
        assertEquals(10000L, normalBreakdown.baseHt.cents)
        assertEquals(2000L, normalBreakdown.vatAmount.cents)
        val reduitBreakdown = inv.vatBreakdown.first { it.rate == VatRate.TAUX_REDUIT }
        assertEquals(2000L, reduitBreakdown.baseHt.cents)
        assertEquals(110L, reduitBreakdown.vatAmount.cents)
    }

    // ── L'agrégation groupée évite la dérive d'arrondi (vs somme de TVA par ligne) ──

    @Test
    fun invoiceTotals_roundingIsComputedOnAggregatedBase_notSummedPerLine() {
        // Deux lignes identiques à 1.25€ HT, taux 2.1% : arrondies séparément, chaque ligne
        // donne 0.03€ de TVA (1.25 * 2.1% = 0.02625 -> round half up -> 0.03), soit 0.06€ au total.
        // Agrégées (base combinée 2.50€), la TVA correcte est 2.50 * 2.1% = 0.0525 -> 0.05€.
        // Ce test garantit que l'implémentation utilise bien la base agrégée (0.05), pas la somme
        // des arrondis par ligne (0.06) — c'est la méthode attendue par l'administration fiscale.
        val lines = listOf(
            InvoiceLine("Ligne A", quantity = 1, unitPriceHt = Money(125), vatRate = VatRate.TAUX_PARTICULIER),
            InvoiceLine("Ligne B", quantity = 1, unitPriceHt = Money(125), vatRate = VatRate.TAUX_PARTICULIER),
        )
        val inv = invoice(InvoiceStatus.DRAFT, lines)

        val perLineSummed = lines.sumOf { it.totalVat.cents }
        assertEquals(6L, perLineSummed) // 0.03 + 0.03 (ce que donnerait, à tort, une somme par ligne)
        assertEquals(5L, inv.totalVat.cents) // la valeur agrégée correcte, réellement utilisée par Invoice
    }
}
