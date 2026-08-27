package com.ledgerhub.domain.quote

/** Cas d'usage unique : soumettre un devis via le [QuoteRepository] injecté. */
class SubmitQuoteUseCase(private val repository: QuoteRepository) {
    suspend operator fun invoke(quote: Quote): Result<Unit> = repository.submitQuote(quote)
}
