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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            invoice("F-101", InvoiceStatus.DEPOSITED),
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
        val sent = invoice("F-002", InvoiceStatus.DEPOSITED, unitPriceHtCents = 5000) // 50.00 HT * 20% = 60.00 TTC

        val analytics = computeDashboardAnalytics(invoices = listOf(sent), creditNotes = emptyList())

        assertEquals(Money(6000), analytics.pendingRevenue)
        assertEquals(Money.ZERO, analytics.collectedRevenue)
    }

    @Test
    fun aDraftContributesToNeitherCollectedNorPending() {
        // Un brouillon n'est pas une créance : il n'a pas quitté le cabinet.
        val draft = invoice("F-003", InvoiceStatus.DRAFT)

        val analytics = computeDashboardAnalytics(invoices = listOf(draft), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.collectedRevenue)
        assertEquals(Money.ZERO, analytics.pendingRevenue)
    }

    @Test
    fun aDepositedInvoiceIsPending_notCollected() {
        // Référentiel DGFIP (US-07) : une facture déposée est une créance en attente
        // d'encaissement. Avant l'US-07, VALIDATED ne comptait nulle part et seul SENT était
        // en attente — la fusion des deux sous DEPOSITED déplace cette frontière.
        val deposited = invoice("F-004", InvoiceStatus.DEPOSITED)

        val analytics = computeDashboardAnalytics(invoices = listOf(deposited), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.collectedRevenue)
        assertEquals(deposited.totalTtc, analytics.pendingRevenue)
    }

    @Test
    fun rejectedAndRefusedInvoices_areNotPending() {
        // Une facture rejetée ou refusée n'est plus une créance recouvrable en l'état.
        val rejected = invoice("F-008", InvoiceStatus.REJECTED)
        val refused = invoice("F-009", InvoiceStatus.REFUSED)

        val analytics = computeDashboardAnalytics(invoices = listOf(rejected, refused), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.pendingRevenue)
        assertEquals(Money.ZERO, analytics.collectedRevenue)
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
        val sent = invoice("F-021", InvoiceStatus.DEPOSITED)

        val analytics = computeDashboardAnalytics(invoices = listOf(paid, sent), creditNotes = emptyList())

        assertEquals(Money.ZERO, analytics.overdueRevenue)
    }

    @Test
    fun recentDocuments_onlyContainsInvoices_sortedByIssueDateDescending_cappedToThree() {
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
            invoice("F-032", InvoiceStatus.DEPOSITED, issueDate = "2026-08-03"),
            invoice("F-033", InvoiceStatus.PAID, issueDate = "2026-08-06"),
        )

        val analytics = computeDashboardAnalytics(invoices = invoices, creditNotes = emptyList(), quotes = listOf(quote))

        assertEquals(3, analytics.recentDocuments.size)
        // Les devis (DEV-001) ne doivent JAMAIS figurer dans les factures récentes
        assertEquals(listOf("F-031", "F-033", "F-032"), analytics.recentDocuments.map { it.number })
        assertTrue(analytics.recentDocuments.all { it is RecentDocument.InvoiceDocument })
    }

    @Test
    fun recentDocuments_exposesExpectedInvoiceDetails_forPopulatedTable() {
        val invoice = invoice("FAC-2026-001", InvoiceStatus.DEPOSITED, issueDate = "2026-08-15", unitPriceHtCents = 10000)
        val analytics = computeDashboardAnalytics(invoices = listOf(invoice), creditNotes = emptyList())

        assertEquals(1, analytics.recentDocuments.size)
        val doc = analytics.recentDocuments.first()
        assertTrue(doc is RecentDocument.InvoiceDocument)
        assertEquals("FAC-2026-001", doc.number)
        assertEquals("2026-08-15", doc.issueDate)
        assertEquals("Client SAS", (doc as RecentDocument.InvoiceDocument).invoice.recipient.name)
        assertEquals(12000L, doc.invoice.totalTtc.cents)
        assertEquals(InvoiceStatus.DEPOSITED, doc.invoice.status)
    }

    // ── Activité commerciale des devis (US-12) ──────────────────────────────

    /** Date de référence de tous les tests de relance — aucune dépendance à l'heure réelle. */
    private val today = "2026-08-30"

    private fun quote(
        number: String,
        status: QuoteStatus,
        validityDate: String,
        unitPriceHtCents: Long = 10000,
        clientName: String = "Client SAS",
    ) = Quote(
        number = number,
        issueDate = "2026-08-01",
        validityDate = validityDate,
        issuer = issuer,
        recipient = recipient.copy(name = clientName),
        lines = listOf(QuoteLine("Etude", quantity = 1, unitPriceHt = Money(unitPriceHtCents), vatRate = VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun analyticsOf(vararg quotes: Quote) = computeDashboardAnalytics(
        invoices = emptyList(),
        creditNotes = emptyList(),
        quotes = quotes.toList(),
        today = today,
    )

    @Test
    fun pendingQuotesTotal_sumsOnlySentQuotes_excludingTax() {
        val analytics = analyticsOf(
            quote("DEV-001", QuoteStatus.SENT, "2026-09-05"),      // 100,00 HT (120,00 TTC)
            quote("DEV-002", QuoteStatus.SENT, "2026-09-06"),      // 100,00 HT
            quote("DEV-003", QuoteStatus.DRAFT, "2026-09-07"),     // exclu
            quote("DEV-004", QuoteStatus.ACCEPTED, "2026-09-08"),  // exclu
            quote("DEV-005", QuoteStatus.REJECTED, "2026-09-09"),  // exclu
        )

        // Cumul HT : la TVA n'entre pas dans un indicateur de chiffre d'affaires potentiel.
        assertEquals(20_000L, analytics.pendingQuotesTotal.cents)
        assertEquals(2, analytics.pendingQuotesCount)
    }

    @Test
    fun pendingQuotesTotal_isZero_whenNoQuoteIsSent() {
        val analytics = analyticsOf(
            quote("DEV-001", QuoteStatus.DRAFT, "2026-09-05"),
            quote("DEV-002", QuoteStatus.ACCEPTED, "2026-09-06"),
        )

        assertEquals(Money.ZERO, analytics.pendingQuotesTotal)
        assertEquals(0, analytics.pendingQuotesCount)
        assertTrue(analytics.quotesToFollowUp.isEmpty())
    }

    @Test
    fun quotesToFollowUp_areSortedByMostImminentDeadlineFirst() {
        val analytics = analyticsOf(
            quote("DEV-C", QuoteStatus.SENT, "2026-09-10"), // J+11
            quote("DEV-A", QuoteStatus.SENT, "2026-08-25"), // expiré depuis 5 j
            quote("DEV-B", QuoteStatus.SENT, "2026-09-01"), // J+2
        )

        assertEquals(listOf("DEV-A", "DEV-B", "DEV-C"), analytics.quotesToFollowUp.map { it.number })
        assertEquals(listOf(-5, 2, 11), analytics.quotesToFollowUp.map { it.daysRemaining })
    }

    @Test
    fun quotesToFollowUp_areLimitedToTheFourteenDayWindow() {
        val analytics = analyticsOf(
            quote("DEV-IN", QuoteStatus.SENT, "2026-09-13"),   // J+14 — dernier jour retenu
            quote("DEV-OUT", QuoteStatus.SENT, "2026-09-14"),  // J+15 — hors fenêtre
        )

        assertEquals(FOLLOW_UP_WINDOW_DAYS, 14)
        assertEquals(listOf("DEV-IN"), analytics.quotesToFollowUp.map { it.number })
    }

    @Test
    fun expiredQuotes_stayInTheList_andAreFlaggedAsSuch() {
        val analytics = analyticsOf(quote("DEV-OLD", QuoteStatus.SENT, "2026-07-01"))

        val item = analytics.quotesToFollowUp.single()
        assertTrue(item.isExpired)
        assertTrue(item.daysRemaining < 0)
        // Le KPI compte tout de même le devis : il reste une somme en attente de réponse.
        assertEquals(10_000L, analytics.pendingQuotesTotal.cents)
    }

    @Test
    fun aQuoteExpiringToday_isNotConsideredExpired() {
        val analytics = analyticsOf(quote("DEV-TODAY", QuoteStatus.SENT, today))

        val item = analytics.quotesToFollowUp.single()
        assertEquals(0, item.daysRemaining)
        assertFalse(item.isExpired)
    }

    @Test
    fun followUpItems_carryNumberClientAmountAndValidityDate() {
        val analytics = analyticsOf(
            quote("DEV-042", QuoteStatus.SENT, "2026-09-02", unitPriceHtCents = 50_000, clientName = "Boulangerie Moreau SARL"),
        )

        val item = analytics.quotesToFollowUp.single()
        assertEquals("DEV-042", item.number)
        assertEquals("Boulangerie Moreau SARL", item.clientName)
        // La ligne de relance reste en TTC : c'est le montant que le client paiera.
        assertEquals(60_000L, item.totalTtc.cents) // 500,00 HT + 20 % TVA
        // …tandis que le KPI cumule le HT.
        assertEquals(50_000L, analytics.pendingQuotesTotal.cents)
        assertEquals("2026-09-02", item.validityDate)
        assertEquals(3, item.daysRemaining)
    }

    @Test
    fun withoutAClock_theKpiIsStillComputed_butNoFollowUpIsProposed() {
        val analytics = computeDashboardAnalytics(
            invoices = emptyList(),
            creditNotes = emptyList(),
            quotes = listOf(quote("DEV-001", QuoteStatus.SENT, "2026-09-01")),
            // today omis : aucune horloge disponible.
        )

        assertEquals(10_000L, analytics.pendingQuotesTotal.cents)
        assertTrue(analytics.quotesToFollowUp.isEmpty(), "aucun délai inventé sans date de référence")
    }

    @Test
    fun aMalformedValidityDate_dropsTheQuoteFromTheListWithoutFailing() {
        val analytics = analyticsOf(
            quote("DEV-BAD", QuoteStatus.SENT, "pas-une-date"),
            quote("DEV-OK", QuoteStatus.SENT, "2026-09-01"),
        )

        assertEquals(listOf("DEV-OK"), analytics.quotesToFollowUp.map { it.number })
        // Le devis illisible compte malgré tout dans le montant HT en attente.
        assertEquals(20_000L, analytics.pendingQuotesTotal.cents)
    }
}
