package com.ledgerhub.presentation.quotes

import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteStatus

/** Filtres de statut de l'écran liste des devis. */
enum class QuoteStatusFilter(val label: String) {
    TOUS("Tous"),
    DRAFT("Brouillons"),
    SENT("Envoyés"),
    ACCEPTED("Acceptés"),
    REJECTED("Refusés"),
}

fun QuoteStatusFilter.labelKey(): StringKey = when (this) {
    QuoteStatusFilter.TOUS -> StringKey.FILTER_ALL
    QuoteStatusFilter.DRAFT -> StringKey.FILTER_DRAFT
    QuoteStatusFilter.SENT -> StringKey.FILTER_SENT
    QuoteStatusFilter.ACCEPTED -> StringKey.FILTER_ACCEPTED
    QuoteStatusFilter.REJECTED -> StringKey.FILTER_REJECTED
}

fun QuoteStatusFilter.matches(status: QuoteStatus): Boolean = when (this) {
    QuoteStatusFilter.TOUS -> true
    QuoteStatusFilter.DRAFT -> status == QuoteStatus.DRAFT
    QuoteStatusFilter.SENT -> status == QuoteStatus.SENT
    QuoteStatusFilter.ACCEPTED -> status == QuoteStatus.ACCEPTED
    QuoteStatusFilter.REJECTED -> status == QuoteStatus.REJECTED
}

/** État immuable de l'écran liste des devis — pattern UDF, symétrique à QuoteFormUiState. */
data class QuotesUiState(
    val quotes: List<Quote> = emptyList(),
    val statusFilter: QuoteStatusFilter = QuoteStatusFilter.TOUS,
    val isLoading: Boolean = false,
    val loadErrorMessage: String? = null,
    /** Numéro du devis dont la conversion en facture est en cours — pilote le spinner du bouton. */
    val convertingQuoteNumber: String? = null,
    /** Factures déjà générées par conversion, indexées par numéro du devis d'origine ([Invoice.sourceQuoteId]). */
    val convertedInvoicesByQuoteNumber: Map<String, Invoice> = emptyMap(),
    val conversionErrorMessage: String? = null,
) {
    val filteredQuotes: List<Quote>
        get() = quotes.filter { statusFilter.matches(it.status) }

    val counts: Map<QuoteStatusFilter, Int>
        get() = QuoteStatusFilter.entries.associateWith { filter ->
            quotes.count { filter.matches(it.status) }
        }

    fun invoiceFor(quote: Quote): Invoice? = convertedInvoicesByQuoteNumber[quote.number]
}
