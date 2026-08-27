package com.ledgerhub.presentation.quotes

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.quote.Quote

/** État immuable de l'écran liste des devis — pattern UDF, symétrique à QuoteFormUiState. */
data class QuotesUiState(
    val quotes: List<Quote> = emptyList(),
    val isLoading: Boolean = false,
    val loadErrorMessage: String? = null,
    /** Numéro du devis dont la conversion en facture est en cours — pilote le spinner du bouton. */
    val convertingQuoteNumber: String? = null,
    /** Factures déjà générées par conversion, indexées par numéro du devis d'origine ([Invoice.sourceQuoteId]). */
    val convertedInvoicesByQuoteNumber: Map<String, Invoice> = emptyMap(),
    val conversionErrorMessage: String? = null,
) {
    fun invoiceFor(quote: Quote): Invoice? = convertedInvoicesByQuoteNumber[quote.number]
}
