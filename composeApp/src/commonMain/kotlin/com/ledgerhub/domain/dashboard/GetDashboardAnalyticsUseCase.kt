package com.ledgerhub.domain.dashboard

import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.quote.QuoteRepository
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock

/**
 * Cas d'usage : assemble [DashboardAnalytics] à partir des trois repositories existants.
 * Toutes les factures de l'utilisateur sont récupérées (pas seulement Payée/Envoyée) car la
 * section "Activité récente" doit aussi montrer les brouillons/devis en cours — le filtrage par
 * statut pour le calcul du CA reste interne à [computeDashboardAnalytics].
 */
class GetDashboardAnalyticsUseCase(
    private val invoiceRepository: InvoiceRepository,
    private val creditNoteRepository: CreditNoteRepository,
    private val quoteRepository: QuoteRepository,
    /**
     * Source du « aujourd'hui » qui date les relances de devis (US-12). Injectée plutôt que
     * statique : un délai avant échéance ne se teste pas contre l'heure réelle.
     */
    private val clock: Clock = SystemClock,
) {
    suspend operator fun invoke(): Result<DashboardAnalytics> = runCatching {
        val invoices = invoiceRepository.fetchInvoices().getOrThrow()
        val creditNotes = creditNoteRepository.fetchCreditNotes().getOrThrow()
        val quotes = quoteRepository.fetchQuotes().getOrThrow()

        computeDashboardAnalytics(
            invoices = invoices,
            creditNotes = creditNotes,
            quotes = quotes,
            // nowIso() est un instant ISO 8601 complet ; seule la partie date nous intéresse.
            today = clock.nowIso().take(ISO_DATE_LENGTH),
        )
    }

    private companion object {
        const val ISO_DATE_LENGTH = 10 // "AAAA-MM-JJ"
    }
}
