package com.ledgerhub.domain.quote

/** Abstraction de la persistance/backend — la couche présentation ne connaît que ce contrat. */
interface QuoteRepository {
    suspend fun submitQuote(quote: Quote): Result<Unit>

    /** Liste des devis existants, pour l'écran [com.ledgerhub.presentation.quotes.QuotesView]. */
    suspend fun fetchQuotes(): Result<List<Quote>>
}
