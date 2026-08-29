package com.ledgerhub.data.invoice

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implémentation mock — simule un backend Kubernetes le temps de valider le flux de bout en
 * bout (UI -> ViewModel -> UseCase) sans complexifier l'infrastructure locale (pas de Ktor
 * ni de serveur réel à ce stade). À remplacer par un repository Ktor réel ultérieurement.
 *
 * Conserve les factures en mémoire (liste pré-remplie sur plusieurs mois et statuts, pour donner
 * à [com.ledgerhub.presentation.dashboard.DashboardScreen] un jeu de données représentatif à
 * l'écran — voir [com.ledgerhub.data.quote.MockQuoteRepository] pour le même principe côté devis).
 *
 * @param simulatedDelayMillis délai réseau simulé, pour observer les indicateurs de chargement.
 * @param simulateFailure flag de débogage : force un [Result.failure] au lieu du succès par défaut.
 */
class MockInvoiceRepository(
    private val simulatedDelayMillis: Long = 1_500L,
    private val simulateFailure: Boolean = false,
) : InvoiceRepository {

    private val mutex = Mutex()
    private val invoices = mutableListOf(
        sampleInvoice("F-2026-101", InvoiceStatus.PAID, "2026-03-05", unitPriceHtCents = 80000),
        sampleInvoice("F-2026-102", InvoiceStatus.PAID, "2026-04-12", unitPriceHtCents = 120000),
        sampleInvoice("F-2026-103", InvoiceStatus.PAID, "2026-05-20", unitPriceHtCents = 95000),
        sampleInvoice("F-2026-104", InvoiceStatus.PAID, "2026-06-02", unitPriceHtCents = 150000),
        sampleInvoice("F-2026-105", InvoiceStatus.PAID, "2026-07-18", unitPriceHtCents = 60000),
        sampleInvoice("F-2026-106", InvoiceStatus.PAID, "2026-08-01", unitPriceHtCents = 70000),
        sampleInvoice("F-2026-107", InvoiceStatus.SENT, "2026-08-10", unitPriceHtCents = 45000),
        sampleInvoice("F-2026-108", InvoiceStatus.DRAFT, "2026-08-15", unitPriceHtCents = 20000),
    )

    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        mutex.withLock { invoices.add(invoice) }
        return Result.success(Unit)
    }

    override suspend fun fetchInvoices(): Result<List<Invoice>> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        return Result.success(mutex.withLock { invoices.toList() })
    }

    private companion object {
        fun sampleInvoice(number: String, status: InvoiceStatus, issueDate: String, unitPriceHtCents: Long) = Invoice(
            number = number,
            issueDate = issueDate,
            issuer = Party("Vendeur SARL", "123456789", "12345678900012", "contact@vendeur-sarl.fr"),
            recipient = Party("Client SAS", "987654321", "98765432100045", "compta@client-sas.fr"),
            lines = listOf(
                InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(unitPriceHtCents), vatRate = VatRate.TAUX_NORMAL)
            ),
            status = status,
            // Échéance de paiement simplifiée pour le jeu de démo : même quantième, mois suivant.
            dueDate = nextMonth(issueDate),
        )

        /** "2026-03-05" -> "2026-04-05". Suffisant pour un jeu de données de démonstration (pas de calcul calendaire réel). */
        private fun nextMonth(isoDate: String): String {
            val (year, month, day) = isoDate.split("-").map { it.toInt() }
            val (nextYear, nextMonth) = if (month == 12) year + 1 to 1 else year to month + 1
            return "$nextYear-${nextMonth.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }
    }
}
