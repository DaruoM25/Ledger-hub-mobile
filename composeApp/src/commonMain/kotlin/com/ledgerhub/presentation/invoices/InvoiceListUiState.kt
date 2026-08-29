package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Filtre par statut fiscal proposé au-dessus de la liste des factures. [TOUTES] = aucun filtre.
 */
enum class InvoiceStatusFilter(val label: String) {
    TOUTES("Toutes"),
    DRAFT("Brouillons"),
    VALIDATED("Validées"),
    SENT("Envoyées"),
    PAID("Payées"),
    CANCELLED("Annulées");

    fun matches(status: InvoiceStatus): Boolean = when (this) {
        TOUTES -> true
        DRAFT -> status == InvoiceStatus.DRAFT
        VALIDATED -> status == InvoiceStatus.VALIDATED
        SENT -> status == InvoiceStatus.SENT
        PAID -> status == InvoiceStatus.PAID
        CANCELLED -> status == InvoiceStatus.CANCELLED
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
) {
    /**
     * Factures du filtre courant, triées par date d'émission décroissante (plus récentes
     * d'abord). Le tri lexicographique est valide car [Invoice.issueDate] est au format ISO
     * `YYYY-MM-DD` (pas de dépendance kotlinx-datetime en v1).
     */
    val visibleInvoices: List<Invoice>
        get() = invoices
            .filter { statusFilter.matches(it.status) }
            .sortedByDescending { it.issueDate }

    /** Nombre de factures par filtre — alimente les compteurs des chips de filtre. */
    val counts: Map<InvoiceStatusFilter, Int>
        get() = InvoiceStatusFilter.entries.associateWith { filter ->
            invoices.count { filter.matches(it.status) }
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
