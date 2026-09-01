package com.ledgerhub.domain.invoice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-19) — définition du retard de paiement.
 *
 * Cette règle alimente à la fois le KPI « En retard » du tableau de bord et le filtre de la liste
 * visé par l'action rapide « Relancer les factures en retard ». Ce qu'elle décide se répercute donc
 * sur un montant affiché **et** sur la liste censée le justifier : les cas limites y sont testés
 * pour eux-mêmes, pas incidemment.
 */
class InvoiceOverdueTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** 240,00 € TTC. */
    private fun invoice(
        number: String = "FAC-2026-0401",
        status: InvoiceStatus = InvoiceStatus.DEPOSITED,
        dueDate: String = "2026-08-31",
    ) = Invoice(
        number = number,
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
        dueDate = dueDate,
    )

    // ── La règle de base ────────────────────────────────────────────────────

    @Test
    fun anUnsettledInvoicePastItsDueDate_isOverdue() {
        assertTrue(InvoiceOverdue.isOverdue(invoice(dueDate = "2026-08-31"), today = "2026-09-01"))
    }

    /** Le débiteur a jusqu'au soir : l'échéance du jour n'est pas encore un retard. */
    @Test
    fun anInvoiceDueToday_isNotYetOverdue() {
        assertFalse(InvoiceOverdue.isOverdue(invoice(dueDate = "2026-09-01"), today = "2026-09-01"))
    }

    @Test
    fun anInvoiceDueTomorrow_isNotOverdue() {
        assertFalse(InvoiceOverdue.isOverdue(invoice(dueDate = "2026-09-02"), today = "2026-09-01"))
    }

    // ── Absence de date ─────────────────────────────────────────────────────

    /**
     * Le parc antérieur à l'US-16 n'a pas d'échéance. Sans date, relancer reviendrait à harceler
     * un client qui ne doit peut-être rien : l'absence de retard est le seul choix défendable.
     */
    @Test
    fun anInvoiceWithoutADueDate_isNeverOverdue() {
        assertFalse(InvoiceOverdue.isOverdue(invoice(dueDate = ""), today = "2026-09-01"))
    }

    /** Sans horloge, aucun retard n'est déclaré — plutôt que de retomber sur une date implicite. */
    @Test
    fun withoutToday_nothingIsOverdue() {
        assertFalse(InvoiceOverdue.isOverdue(invoice(dueDate = "2020-01-01"), today = ""))
        assertTrue(InvoiceOverdue.filter(listOf(invoice()), today = "").isEmpty())
    }

    // ── Statuts ─────────────────────────────────────────────────────────────

    @Test
    fun onlyUnsettledInvoicesCanBeOverdue() {
        val past = "2026-09-01"

        assertTrue(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.DEPOSITED), past))
        assertTrue(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.APPROVED), past))
        // Rejetée ou refusée, la créance reste à recouvrer : l'échéance contractuelle court.
        assertTrue(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.REJECTED), past))
        assertTrue(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.REFUSED), past))

        // Éteintes : plus de créance, donc plus de retard.
        assertFalse(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.PAID), past))
        assertFalse(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.CANCELLED), past))
        // Un brouillon n'a jamais été présenté au client : il ne peut pas être en retard.
        assertFalse(InvoiceOverdue.isOverdue(invoice(status = InvoiceStatus.DRAFT), past))
    }

    // ── Agrégats ────────────────────────────────────────────────────────────

    /** L'ordre de relance : la créance la plus ancienne d'abord. */
    @Test
    fun overdueInvoices_areSortedByOldestDueDateFirst() {
        val invoices = listOf(
            invoice("FAC-B", dueDate = "2026-08-20"),
            invoice("FAC-A", dueDate = "2026-07-15"),
            invoice("FAC-C", dueDate = "2026-08-25"),
        )

        assertEquals(
            listOf("FAC-A", "FAC-B", "FAC-C"),
            InvoiceOverdue.filter(invoices, today = "2026-09-01").map { it.number },
        )
    }

    @Test
    fun theOverdueTotal_sumsOnlyOverdueInvoices() {
        val invoices = listOf(
            invoice("FAC-A", dueDate = "2026-08-20"),
            invoice("FAC-B", dueDate = "2026-08-25"),
            invoice("FAC-PAID", status = InvoiceStatus.PAID, dueDate = "2026-08-01"),
            invoice("FAC-FUTURE", dueDate = "2026-12-31"),
        )

        assertEquals(Money(48_000), InvoiceOverdue.totalTtc(invoices, today = "2026-09-01"))
    }

    @Test
    fun anEmptyLedger_hasNoOverdueTotal() {
        assertEquals(Money.ZERO, InvoiceOverdue.totalTtc(emptyList(), today = "2026-09-01"))
    }
}
