package com.ledgerhub.domain.dashboard

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.quote.Quote

private const val YEAR_MONTH_LENGTH = 7 // "AAAA-MM"
private const val MONTHLY_CHART_SIZE = 6
private const val RECENT_DOCUMENTS_SIZE = 3

/** CA net (factures + avoirs) d'un mois "AAAA-MM" — un point du graphique du tableau de bord. */
data class MonthlyRevenue(val month: String, val amount: Money)

/** Facture ou devis, réunis pour la section "Activité récente" du tableau de bord. */
sealed interface RecentDocument {
    val number: String
    val issueDate: String

    data class InvoiceDocument(val invoice: Invoice) : RecentDocument {
        override val number: String get() = invoice.number
        override val issueDate: String get() = invoice.issueDate
    }

    data class QuoteDocument(val quote: Quote) : RecentDocument {
        override val number: String get() = quote.number
        override val issueDate: String get() = quote.issueDate
    }
}

data class DashboardAnalytics(
    val collectedRevenue: Money,
    val pendingRevenue: Money,
    val overdueRevenue: Money,
    val monthlyRevenue: List<MonthlyRevenue>,
    val recentDocuments: List<RecentDocument>,
)

/**
 * Calcul pur (aucun repository/coroutine) des indicateurs du tableau de bord — extrait de
 * [GetDashboardAnalyticsUseCase] pour rester testable directement, sans base de données.
 *
 * Hypothèses métier (v1) :
 * - "Encaissé" = factures Payées, + factures Payées-puis-Annulées par avoir (l'argent est entré
 *   en caisse avant d'être remboursé) — nettes du montant de l'avoir. Un avoir d'annulation
 *   intégrale (seul type supporté, voir [CreditNote.init] : montants toujours négatifs) ramène
 *   donc la contribution nette de sa facture à 0, ce qui reflète bien un encaissement remboursé
 *   en totalité plutôt qu'un encaissement qui aurait simplement disparu du rapport.
 * - "En attente" = factures Envoyées, pas encore payées ([InvoiceStatus.SENT]). Une facture
 *   Validée mais pas encore envoyée n'est pas encore "en attente de paiement" côté client.
 * - "En retard" est TOUJOURS 0 : le domaine [Invoice] n'a pas de date d'échéance en v1 (seule
 *   [Invoice.issueDate] existe) — impossible de déterminer un retard réel sans ce champ, et
 *   mieux vaut l'absence explicite d'un chiffre que d'en simuler un non mesurable.
 * - Le CA mensuel du graphique reprend la même définition d'"encaissé", ventilée par mois
 *   d'émission de la facture d'origine (pas la date de l'avoir).
 */
fun computeDashboardAnalytics(
    invoices: List<Invoice>,
    creditNotes: List<CreditNote>,
    quotes: List<Quote> = emptyList(),
): DashboardAnalytics {
    val creditNotesByInvoiceNumber = creditNotes.groupBy { it.invoiceId }

    fun netTtcOf(invoice: Invoice): Money {
        val creditNoteAdjustment = creditNotesByInvoiceNumber[invoice.number]
            .orEmpty()
            .fold(Money.ZERO) { sum, creditNote -> sum + creditNote.totalTtc }
        return invoice.totalTtc + creditNoteAdjustment
    }

    val collectedEntries = invoices.filter { invoice ->
        invoice.status == InvoiceStatus.PAID ||
            (invoice.status == InvoiceStatus.CANCELLED && creditNotesByInvoiceNumber.containsKey(invoice.number))
    }
    val collectedRevenue = collectedEntries.fold(Money.ZERO) { acc, invoice -> acc + netTtcOf(invoice) }

    val pendingRevenue = invoices
        .filter { it.status == InvoiceStatus.SENT }
        .fold(Money.ZERO) { acc, invoice -> acc + invoice.totalTtc }

    val overdueRevenue = Money.ZERO

    val monthlyRevenue = collectedEntries
        .groupBy { it.issueDate.take(YEAR_MONTH_LENGTH) }
        .map { (month, monthInvoices) ->
            MonthlyRevenue(month = month, amount = monthInvoices.fold(Money.ZERO) { acc, invoice -> acc + netTtcOf(invoice) })
        }
        .sortedBy { it.month }
        .takeLast(MONTHLY_CHART_SIZE)

    val recentDocuments = (invoices.map(RecentDocument::InvoiceDocument) + quotes.map(RecentDocument::QuoteDocument))
        .sortedByDescending { it.issueDate }
        .take(RECENT_DOCUMENTS_SIZE)

    return DashboardAnalytics(
        collectedRevenue = collectedRevenue,
        pendingRevenue = pendingRevenue,
        overdueRevenue = overdueRevenue,
        monthlyRevenue = monthlyRevenue,
        recentDocuments = recentDocuments,
    )
}
