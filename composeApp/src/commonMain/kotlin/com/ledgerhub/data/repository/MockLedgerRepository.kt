package com.ledgerhub.data.repository

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.repository.LedgerRepository
import kotlinx.coroutines.delay

/**
 * Implémentation en mémoire de [LedgerRepository] — valeur par défaut des ViewModels de l'US-02
 * et jeu de données pour les `@Preview`, tant que le backend local de dev n'est pas lancé.
 * Miroir de [com.ledgerhub.data.invoice.MockInvoiceRepository] côté API distante.
 *
 * @param simulatedDelayMillis latence réseau simulée (pour observer les indicateurs de chargement).
 * @param simulateFailure force un [Result.failure] au lieu du succès — test manuel du chemin d'erreur.
 */
class MockLedgerRepository(
    private val simulatedDelayMillis: Long = 800L,
    private val simulateFailure: Boolean = false,
) : LedgerRepository {

    private val invoices: List<Invoice> = listOf(
        sampleInvoice("F-2026-001", InvoiceStatus.PAID, "2026-02-14", 120_000, VatRate.TAUX_NORMAL),
        sampleInvoice("F-2026-002", InvoiceStatus.PAID, "2026-03-03", 84_000, VatRate.TAUX_INTERMEDIAIRE),
        sampleInvoice("F-2026-003", InvoiceStatus.SENT, "2026-05-21", 45_000, VatRate.TAUX_NORMAL),
        sampleInvoice("F-2026-004", InvoiceStatus.VALIDATED, "2026-06-30", 210_000, VatRate.TAUX_NORMAL),
        sampleInvoice("F-2026-005", InvoiceStatus.DRAFT, "2026-07-11", 30_000, VatRate.TAUX_REDUIT),
        sampleInvoice("F-2026-006", InvoiceStatus.CANCELLED, "2026-07-25", 67_000, VatRate.TAUX_NORMAL),
    )

    override suspend fun fetchInvoices(): Result<List<Invoice>> {
        delay(simulatedDelayMillis)
        if (simulateFailure) return failure()
        return Result.success(invoices)
    }

    override suspend fun getInvoiceDetail(number: String): Result<Invoice?> {
        delay(simulatedDelayMillis)
        if (simulateFailure) return failure()
        return Result.success(invoices.firstOrNull { it.number == number })
    }

    override suspend fun healthCheck(): Result<Unit> {
        delay(simulatedDelayMillis)
        return if (simulateFailure) failure() else Result.success(Unit)
    }

    private fun <T> failure(): Result<T> =
        Result.failure(IllegalStateException("Serveur Ledger local injoignable (mock)"))

    private companion object {
        fun sampleInvoice(
            number: String,
            status: InvoiceStatus,
            issueDate: String,
            unitPriceHtCents: Long,
            vatRate: VatRate,
        ) = Invoice(
            number = number,
            issueDate = issueDate,
            issuer = Party("Ledger Studio SARL", "123456789", "12345678900012"),
            recipient = Party("Client Démo SAS", "987654321", "98765432100045"),
            lines = listOf(
                InvoiceLine("Prestation de conseil", quantity = 1, unitPriceHt = Money(unitPriceHtCents), vatRate = vatRate),
                InvoiceLine("Frais de dossier", quantity = 2, unitPriceHt = Money(4_500), vatRate = VatRate.TAUX_NORMAL),
            ),
            status = status,
        )
    }
}
