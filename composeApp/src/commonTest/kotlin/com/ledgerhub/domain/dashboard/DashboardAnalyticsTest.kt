package com.ledgerhub.domain.dashboard

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.QuoteStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests QA (Skill 2) de [computeDashboardAnalytics] — fonction pure, aucune base de données
 * nécessaire (voir SqlDelightInvoiceRepositoryTest pour la couche persistance).
 */
class DashboardAnalyticsTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun invoice(
        number: String,
        status: InvoiceStatus,
        issueDate: String = "2026-08-01",
        unitPriceHtCents: Long = 10000,
    ) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(unitPriceHtCents), vatRate = VatRate.TAUX_NORMAL)),
        status = status,
    )

    @Test
    fun noInvoices_returnsAllZeroesAndNoRecentDocuments() {
        val analytics = computeDashboardAnalytics(invoices = emptyList(), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.collectedRevenue)
        assertEquals(Money.ZERO, analytics.pendingRevenue)
        assertEquals(Money.ZERO, analytics.overdueRevenue)
        assertEquals(0, analytics.issuedCount)
        assertEquals(emptyList(), analytics.monthlyRevenue)
        assertEquals(emptyList(), analytics.recentDocuments)
    }

    @Test
    fun issuedCount_countsEveryInvoice_regardlessOfStatus() {
        val invoices = listOf(
            invoice("F-100", InvoiceStatus.DRAFT),
            invoice("F-101", InvoiceStatus.SENT),
            invoice("F-102", InvoiceStatus.PAID),
            invoice("F-103", InvoiceStatus.CANCELLED),
        )

        val analytics = computeDashboardAnalytics(invoices = invoices, creditNotes = emptyList())

        assertEquals(4, analytics.issuedCount)
    }

    @Test
    fun paidInvoice_contributesToCollectedRevenue_atFullTtc() {
        val paid = invoice("F-001", InvoiceStatus.PAID, unitPriceHtCents = 10000) // 100.00 HT * 20% = 120.00 TTC

        val analytics = computeDashboardAnalytics(invoices = listOf(paid), creditNotes = emptyList())

        assertEquals(Money(12000), analytics.collectedRevenue)
    }

    @Test
    fun sentInvoice_contributesToPendingRevenue_notCollected() {
        val sent = invoice("F-002", InvoiceStatus.SENT, unitPriceHtCents = 5000) // 50.00 HT * 20% = 60.00 TTC

        val analytics = computeDashboardAnalytics(invoices = listOf(sent), creditNotes = emptyList())

        assertEquals(Money(6000), analytics.pendingRevenue)
        assertEquals(Money.ZERO, analytics.collectedRevenue)
    }

    @Test
    fun draftAndValidatedInvoices_contributeToNeitherCollectedNorPending() {
        val draft = invoice("F-003", InvoiceStatus.DRAFT)
        val validated = invoice("F-004", InvoiceStatus.VALIDATED)

        val analytics = computeDashboardAnalytics(invoices = listOf(draft, validated), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.collectedRevenue)
        assertEquals(Money.ZERO, analytics.pendingRevenue)
    }

    @Test
    fun creditNoteOnCancelledInvoice_decreasesCollectedRevenue_backToZero() {
        // Une facture payée puis intégralement annulée par avoir : l'argent est entré en caisse
        // puis a été remboursé — l'avoir doit ramener la contribution nette de cette facture à 0.
        val cancelled = invoice("F-005", InvoiceStatus.CANCELLED, unitPriceHtCents = 10000) // 120.00 TTC à l'origine
        val creditNote = CreditNote(
            number = "AV-005",
            issueDate = "2026-08-10",
            invoiceId = "F-005",
            originalInvoiceDate = "2026-08-01",
            reason = "Erreur tarifaire",
            lines = emptyList(),
            issuer = issuer,
            recipient = recipient,
            totalHt = Money(-10000),
            totalVat = Money(-2000),
            totalTtc = Money(-12000),
        )

        val analytics = computeDashboardAnalytics(invoices = listOf(cancelled), creditNotes = listOf(creditNote))

        assertEquals(Money.ZERO, analytics.collectedRevenue)
    }

    @Test
    fun creditNote_decreasesCollectedRevenue_whenMixedWithOtherPaidInvoices() {
        // Preuve directe que l'avoir DIMINUE bien le total, plutôt que de simplement l'exclure :
        // deux factures payées, une seule des deux se voit annulée par avoir.
        val stillPaid = invoice("F-006", InvoiceStatus.PAID, unitPriceHtCents = 10000) // 120.00 TTC
        val refunded = invoice("F-007", InvoiceStatus.CANCELLED, unitPriceHtCents = 5000) // 60.00 TTC à l'origine
        val creditNote = CreditNote(
            number = "AV-007",
            issueDate = "2026-08-11",
            invoiceId = "F-007",
            originalInvoiceDate = "2026-08-02",
            reason = "Marchandise retournée",
            lines = emptyList(),
            issuer = issuer,
            recipient = recipient,
            totalHt = Money(-5000),
            totalVat = Money(-1000),
            totalTtc = Money(-6000),
        )

        val withoutCreditNote = computeDashboardAnalytics(invoices = listOf(stillPaid, refunded), creditNotes = emptyList())
        val withCreditNote = computeDashboardAnalytics(invoices = listOf(stillPaid, refunded), creditNotes = listOf(creditNote))

        // Sans l'avoir, F-007 est Annulée mais sans avoir associé -> déjà exclue (120.00 seulement).
        assertEquals(Money(12000), withoutCreditNote.collectedRevenue)
        // Avec l'avoir, F-007 rentre dans le calcul (annulée + avoir) mais nette à 0 -> total inchangé
        // ici puisque le montant remboursé est exactement celui de la facture qu'il annule.
        assertEquals(Money(12000), withCreditNote.collectedRevenue)
        // Preuve que l'avoir réduit bien la contribution de SA facture (60.00 -> 0), et non un no-op :
        // en simulant un avoir partiel (défensif — l'app n'en génère pas, mais la fonction doit rester
        // correcte si le montant de l'avoir diffère du total de la facture).
        val partialCreditNote = creditNote.copy(totalHt = Money(-2000), totalVat = Money(-400), totalTtc = Money(-2400))
        val withPartialCreditNote = computeDashboardAnalytics(invoices = listOf(stillPaid, refunded), creditNotes = listOf(partialCreditNote))
        assertEquals(Money(12000 + 6000 - 2400), withPartialCreditNote.collectedRevenue)
    }

    @Test
    fun cancelledInvoiceWithoutCreditNote_isExcludedFromCollectedRevenue() {
        val cancelledWithoutRefund = invoice("F-008", InvoiceStatus.CANCELLED, unitPriceHtCents = 10000)

        val analytics = computeDashboardAnalytics(invoices = listOf(cancelledWithoutRefund), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.collectedRevenue)
    }

    @Test
    fun monthlyRevenue_groupsByIssueMonth_sortedAscending_cappedToLastSixMonths() {
        val invoices = listOf(
            invoice("F-010", InvoiceStatus.PAID, issueDate = "2026-01-15", unitPriceHtCents = 10000),
            invoice("F-011", InvoiceStatus.PAID, issueDate = "2026-01-20", unitPriceHtCents = 10000),
            invoice("F-012", InvoiceStatus.PAID, issueDate = "2026-02-05", unitPriceHtCents = 5000),
            invoice("F-013", InvoiceStatus.PAID, issueDate = "2026-03-01", unitPriceHtCents = 5000),
            invoice("F-014", InvoiceStatus.PAID, issueDate = "2026-04-01", unitPriceHtCents = 5000),
            invoice("F-015", InvoiceStatus.PAID, issueDate = "2026-05-01", unitPriceHtCents = 5000),
            invoice("F-016", InvoiceStatus.PAID, issueDate = "2026-06-01", unitPriceHtCents = 5000),
            invoice("F-017", InvoiceStatus.PAID, issueDate = "2026-07-01", unitPriceHtCents = 5000),
        )

        val analytics = computeDashboardAnalytics(invoices = invoices, creditNotes = emptyList())

        assertEquals(6, analytics.monthlyRevenue.size)
        assertEquals(listOf("2026-02", "2026-03", "2026-04", "2026-05", "2026-06", "2026-07"), analytics.monthlyRevenue.map { it.month })
        assertEquals(Money(6000), analytics.monthlyRevenue.first { it.month == "2026-02" }.amount) // 50.00 HT + 20% TVA = 60.00 TTC
    }

    @Test
    fun overdueRevenue_isAlwaysZero_noDueDateInDomainYet() {
        val paid = invoice("F-020", InvoiceStatus.PAID)
        val sent = invoice("F-021", InvoiceStatus.SENT)

        val analytics = computeDashboardAnalytics(invoices = listOf(paid, sent), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.overdueRevenue)
    }

    @Test
    fun recentDocuments_mergesInvoicesAndQuotes_sortedByIssueDateDescending_cappedToThree() {
        val quote = Quote(
            number = "DEV-001",
            issueDate = "2026-08-05",
            validityDate = "2026-09-05",
            issuer = issuer,
            recipient = recipient,
            lines = listOf(QuoteLine("Etude", quantity = 1, unitPriceHt = Money(1000), vatRate = VatRate.TAUX_NORMAL)),
            status = QuoteStatus.SENT,
        )
        val invoices = listOf(
            invoice("F-030", InvoiceStatus.DRAFT, issueDate = "2026-08-01"),
            invoice("F-031", InvoiceStatus.PAID, issueDate = "2026-08-08"),
            invoice("F-032", InvoiceStatus.SENT, issueDate = "2026-08-03"),
        )

        val analytics = computeDashboardAnalytics(invoices = invoices, creditNotes = emptyList(), quotes = listOf(quote))

        assertEquals(3, analytics.recentDocuments.size)
        assertEquals(listOf("F-031", "DEV-001", "F-032"), analytics.recentDocuments.map { it.number })
    }
}
