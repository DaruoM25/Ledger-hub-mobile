package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceOverdue
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Filtre proposé au-dessus de la liste des factures. [TOUTES] = aucun filtre.
 *
 * [OVERDUE] est le seul qui ne porte pas sur le statut : il croise l'échéance et la date du jour
 * (voir [InvoiceOverdue]). C'est la cible de l'action rapide « Relancer les factures en retard »
 * de la palette de commandes (US-19).
 *
 * [label] n'est qu'un repère de lecture du code : l'écran résout le libellé affiché par
 * `InvoiceStatusFilter.labelKey()`, donc en FR/EN.
 */
enum class InvoiceStatusFilter(val label: String) {
    TOUTES("Toutes"),
    DRAFT("Brouillons"),
    DEPOSITED("Déposées"),
    APPROVED("Approuvées"),
    PAID("Encaissées"),
    OVERDUE("En retard"),
    REJECTED("Rejetées"),
    REFUSED("Refusées"),
    CANCELLED("Annulées");

    /**
     * @param today date du jour en ISO `AAAA-MM-JJ`, nécessaire au seul filtre [OVERDUE]. Vide,
     *   ce filtre ne retient rien — un retard ne s'invente pas sans horloge.
     */
    fun matches(invoice: Invoice, today: String = ""): Boolean = when (this) {
        TOUTES -> true
        OVERDUE -> InvoiceOverdue.isOverdue(invoice, today)
        else -> matchesStatus(invoice.status)
    }

    /**
     * Correspondance par statut seul. [OVERDUE] n'en est pas un : il croise une échéance et une
     * date, et ne retient donc aucun statut à lui seul — d'où `false`, et non une exception.
     */
    fun matchesStatus(status: InvoiceStatus): Boolean = when (this) {
        TOUTES -> true
        DRAFT -> status == InvoiceStatus.DRAFT
        DEPOSITED -> status == InvoiceStatus.DEPOSITED
        APPROVED -> status == InvoiceStatus.APPROVED
        PAID -> status == InvoiceStatus.PAID
        REJECTED -> status == InvoiceStatus.REJECTED
        REFUSED -> status == InvoiceStatus.REFUSED
        CANCELLED -> status == InvoiceStatus.CANCELLED
        OVERDUE -> false
    }
}

/**
 * État immuable de l'écran liste des factures (Sprint 1 — US-02). Pattern UDF, même style que
 * [com.ledgerhub.presentation.quotes.QuotesUiState] : une data class à champs bruts + des
 * projections calculées.
 *
 * [content] traduit ces champs bruts en l'un des 4 états d'affichage attendus par l'UI
 * (Loading / Error / Empty / Success), pour que l'écran n'ait pas à ré-implémenter cette logique.
 */
data class InvoiceListUiState(
    val isLoading: Boolean = false,
    val invoices: List<Invoice> = emptyList(),
    val errorMessage: String? = null,
    val statusFilter: InvoiceStatusFilter = InvoiceStatusFilter.TOUTES,
    /** Numéro de l'avoir par facture annulée — alimente la mention croisée US-05. */
    val creditNotesByInvoice: Map<String, String> = emptyMap(),
    /**
     * Date du jour en ISO `AAAA-MM-JJ`, fournie par le ViewModel depuis
     * [Clock][com.ledgerhub.domain.time.Clock]. Sert au seul filtre
     * [InvoiceStatusFilter.OVERDUE] ; vide, celui-ci ne retient rien.
     */
    val today: String = "",
) {
    /**
     * Factures du filtre courant, triées par date d'émission décroissante (plus récentes
     * d'abord). Le tri lexicographique est valide car [Invoice.issueDate] est au format ISO
     * `YYYY-MM-DD` (pas de dépendance kotlinx-datetime en v1).
     */
    val visibleInvoices: List<Invoice>
        get() = invoices
            .filter { statusFilter.matches(it, today) }
            .sortedByDescending { it.issueDate }

    /** Nombre de factures par filtre — alimente les compteurs des chips de filtre. */
    val counts: Map<InvoiceStatusFilter, Int>
        get() = InvoiceStatusFilter.entries.associateWith { filter ->
            invoices.count { filter.matches(it, today) }
        }

    /** État d'affichage dérivé — l'UI fait un simple `when` dessus. */
    val content: InvoiceListContent
        get() = when {
            isLoading && invoices.isEmpty() -> InvoiceListContent.Loading
            errorMessage != null && invoices.isEmpty() -> InvoiceListContent.Error(errorMessage)
            invoices.isEmpty() -> InvoiceListContent.Empty
            visibleInvoices.isEmpty() -> InvoiceListContent.Empty
            else -> InvoiceListContent.Success(visibleInvoices)
        }
}

/** Les 4 états d'affichage possibles de la liste des factures. */
sealed interface InvoiceListContent {
    data object Loading : InvoiceListContent
    data class Error(val message: String) : InvoiceListContent
    data object Empty : InvoiceListContent
    data class Success(val invoices: List<Invoice>) : InvoiceListContent
}
