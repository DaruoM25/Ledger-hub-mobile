package com.ledgerhub.presentation.quotes

sealed interface QuotesIntent {
    data object LoadQuotes : QuotesIntent

    /** Déclenche "le bouton magique" — conversion du devis [quoteNumber] (déjà Accepté) en brouillon de facture. */
    data class ConvertToInvoice(val quoteNumber: String) : QuotesIntent
}
