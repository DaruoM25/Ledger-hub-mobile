package com.ledgerhub.domain.dashboard

import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.quote.QuoteRepository

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
) {
    suspend operator fun invoke(): Result<DashboardAnalytics> = runCatching {
        val invoices = invoiceRepository.fetchInvoices().getOrThrow()
        val creditNotes = creditNoteRepository.fetchCreditNotes().getOrThrow()
        val quotes = quoteRepository.fetchQuotes().getOrThrow()

        computeDashboardAnalytics(invoices = invoices, creditNotes = creditNotes, quotes = quotes)
    }
}
