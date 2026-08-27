package com.ledgerhub.data.quote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.QuoteRepository
import com.ledgerhub.domain.quote.QuoteStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implémentation mock — simule un backend le temps de valider le flux de bout en bout
 * (UI -> ViewModel -> UseCase) sans complexifier l'infrastructure locale (pas de Ktor
 * ni de serveur réel à ce stade). À remplacer par un repository Ktor réel ultérieurement.
 *
 * Conserve les devis en mémoire (liste pré-remplie avec un devis par statut, pour donner
 * à [com.ledgerhub.presentation.quotes.QuotesView] un jeu de données représentatif à l'écran).
 *
 * @param simulatedDelayMillis délai réseau simulé, pour observer les indicateurs de chargement.
 * @param simulateFailure flag de débogage : force un [Result.failure] au lieu du succès par défaut.
 */
class MockQuoteRepository(
    private val simulatedDelayMillis: Long = 1_500L,
    private val simulateFailure: Boolean = false,
) : QuoteRepository {

    private val mutex = Mutex()
    private val quotes = mutableListOf(
        sampleQuote("DEV-2026-001", QuoteStatus.DRAFT),
        sampleQuote("DEV-2026-002", QuoteStatus.SENT),
        sampleQuote("DEV-2026-003", QuoteStatus.ACCEPTED),
        sampleQuote("DEV-2026-004", QuoteStatus.REJECTED),
    )

    override suspend fun submitQuote(quote: Quote): Result<Unit> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        mutex.withLock { quotes.add(quote) }
        return Result.success(Unit)
    }

    override suspend fun fetchQuotes(): Result<List<Quote>> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        return Result.success(mutex.withLock { quotes.toList() })
    }

    private companion object {
        fun sampleQuote(number: String, status: QuoteStatus) = Quote(
            number = number,
            issueDate = "2026-08-01",
            validityDate = "2026-09-01",
            issuer = Party("Vendeur SARL", "123456789", "12345678900012"),
            recipient = Party("Client SAS", "987654321", "98765432100045"),
            lines = listOf(
                QuoteLine("Prestation de conseil", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL)
            ),
            status = status,
        )
    }
}
