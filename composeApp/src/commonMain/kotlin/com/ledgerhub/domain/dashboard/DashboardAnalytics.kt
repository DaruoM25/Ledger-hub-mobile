package com.ledgerhub.domain.dashboard

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private const val YEAR_MONTH_LENGTH = 7 // "AAAA-MM"
private const val ISO_DATE_LENGTH = 10 // "AAAA-MM-JJ"
private const val MONTHLY_CHART_SIZE = 6
private const val RECENT_DOCUMENTS_SIZE = 3

/**
 * Fenêtre de relance : un devis envoyé entre dans « Devis à relancer » dès que sa validité expire
 * dans 14 jours ou moins. Les devis **déjà expirés** y figurent aussi — ce sont les premiers à
 * rappeler, pas ceux qu'il faut cesser de voir.
 */
const val FOLLOW_UP_WINDOW_DAYS = 14

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

/**
 * Un devis envoyé dont la validité arrive à échéance — une ligne de « Devis à relancer » (US-12).
 *
 * @param daysRemaining jours restants avant [validityDate]. **Négatif si le devis est déjà
 *   expiré** : la liste n'écarte pas ces devis, elle les met en tête.
 */
data class QuoteFollowUpItem(
    val number: String,
    val clientName: String,
    val totalTtc: Money,
    val validityDate: String,
    val daysRemaining: Int,
) {
    val isExpired: Boolean get() = daysRemaining < 0
}

data class DashboardAnalytics(
    val collectedRevenue: Money,
    val pendingRevenue: Money,
    val overdueRevenue: Money,
    /** Nombre total de factures émises par l'utilisateur, tous statuts confondus — 3ᵉ KPI du tableau de bord. */
    val issuedCount: Int,
    val monthlyRevenue: List<MonthlyRevenue>,
    val recentDocuments: List<RecentDocument>,
    /**
     * Montant **HT** cumulé des devis [QuoteStatus.SENT] — 4ᵉ KPI « Devis en attente » (US-12).
     * Hors taxes, comme tout indicateur de chiffre d'affaires potentiel.
     */
    val pendingQuotesTotal: Money = Money.ZERO,
    val pendingQuotesCount: Int = 0,
    /** Devis envoyés dont l'échéance approche, du plus urgent au moins urgent. */
    val quotesToFollowUp: List<QuoteFollowUpItem> = emptyList(),
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
 * - "En attente" = factures déposées ([InvoiceStatus.DEPOSITED]) ou approuvées par
 *   l'administration ([InvoiceStatus.APPROVED]), pas encore payées. Une facture Validée mais
 *   pas encore envoyée n'est pas encore "en attente de paiement" côté client ; à l'inverse,
 *   l'approbation PPF ne change rien à l'attente d'encaissement, elle la confirme.
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
    /**
     * Date du jour en ISO `AAAA-MM-JJ`, fournie par l'appelant (voir
     * [com.ledgerhub.domain.time.Clock]). Vide = pas d'horloge : le KPI des devis reste calculé,
     * mais aucune relance n'est proposée. Mieux vaut une section vide qu'un délai inventé.
     */
    today: String = "",
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
        .filter { it.status == InvoiceStatus.DEPOSITED || it.status == InvoiceStatus.APPROVED }
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

    // ── Activité commerciale des devis (US-12) ────────────────────────────────
    val sentQuotes = quotes.filter { it.status == QuoteStatus.SENT }
    // Cumul **HT** : un devis en attente mesure un chiffre d'affaires potentiel, et le CA se
    // compte hors taxes. La TVA n'est pas un produit de l'entreprise, elle est collectée pour
    // le Trésor — l'inclure gonflerait l'indicateur commercial d'un montant qui ne lui revient pas.
    // Les lignes de « Devis à relancer » restent en TTC : c'est le montant que le client paiera.
    val pendingQuotesTotal = sentQuotes.fold(Money.ZERO) { acc, quote -> acc + quote.totalHt }

    val quotesToFollowUp = sentQuotes
        .mapNotNull { quote ->
            val daysRemaining = daysBetween(today, quote.validityDate) ?: return@mapNotNull null
            if (daysRemaining > FOLLOW_UP_WINDOW_DAYS) return@mapNotNull null
            QuoteFollowUpItem(
                number = quote.number,
                clientName = quote.recipient.name,
                totalTtc = quote.totalTtc,
                validityDate = quote.validityDate,
                daysRemaining = daysRemaining,
            )
        }
        // Échéance la plus imminente d'abord ; le numéro départage pour un ordre stable entre
        // deux devis expirant le même jour.
        .sortedWith(compareBy({ it.validityDate }, { it.number }))

    return DashboardAnalytics(
        collectedRevenue = collectedRevenue,
        pendingRevenue = pendingRevenue,
        overdueRevenue = overdueRevenue,
        issuedCount = invoices.size,
        monthlyRevenue = monthlyRevenue,
        recentDocuments = recentDocuments,
        pendingQuotesTotal = pendingQuotesTotal,
        pendingQuotesCount = sentQuotes.size,
        quotesToFollowUp = quotesToFollowUp,
    )
}

/**
 * Jours séparant [from] de [to], deux dates ISO `AAAA-MM-JJ`.
 *
 * @return `null` si l'une des deux est vide ou malformée — une date de validité illisible écarte
 *   le devis de la liste plutôt que de faire échouer tout le tableau de bord.
 */
private fun daysBetween(from: String, to: String): Int? {
    val start = parseIsoDateOrNull(from) ?: return null
    val end = parseIsoDateOrNull(to) ?: return null
    return start.daysUntil(end)
}

private fun parseIsoDateOrNull(iso: String): LocalDate? {
    if (iso.length < ISO_DATE_LENGTH) return null
    return runCatching { LocalDate.parse(iso.take(ISO_DATE_LENGTH)) }.getOrNull()
}
