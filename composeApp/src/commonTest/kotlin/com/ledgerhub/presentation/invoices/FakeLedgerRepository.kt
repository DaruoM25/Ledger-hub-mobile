package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.repository.LedgerRepository
import kotlinx.coroutines.delay

/**
 * Double de test de [LedgerRepository] — aucun accès réseau, contrôle direct du succès/échec.
 * [delayMillis] simule une latence pour observer l'état `Loading` intermédiaire sous
 * `StandardTestDispatcher` (même principe que `FakeAuthRepository` dans LoginViewModelTest).
 *
 * [invoicesResult] / [detailResult] sont mutables : un test peut basculer d'un échec à un
 * succès entre deux appels pour vérifier le chemin "Réessayer".
 */
class FakeLedgerRepository(
    var invoicesResult: Result<List<Invoice>> = Result.success(emptyList()),
    var detailResult: Result<Invoice?> = Result.success(null),
    private val delayMillis: Long = 0L,
) : LedgerRepository {

    var fetchInvoicesCallCount = 0
        private set
    var lastRequestedNumber: String? = null
        private set

    override suspend fun fetchInvoices(): Result<List<Invoice>> {
        delay(delayMillis)
        fetchInvoicesCallCount++
        return invoicesResult
    }

    override suspend fun getInvoiceDetail(number: String): Result<Invoice?> {
        delay(delayMillis)
        lastRequestedNumber = number
        return detailResult
    }

    override suspend fun healthCheck(): Result<Unit> {
        delay(delayMillis)
        return Result.success(Unit)
    }
}

/** Fabrique de factures de test — une seule ligne, paramètres utiles au test seulement. */
fun testInvoice(
    number: String,
    status: InvoiceStatus = InvoiceStatus.DRAFT,
    issueDate: String = "2026-01-01",
    unitPriceHtCents: Long = 10_000,
    quantity: Int = 1,
    vatRate: VatRate = VatRate.TAUX_NORMAL,
): Invoice = Invoice(
    number = number,
    issueDate = issueDate,
    issuer = Party("Émetteur SARL", "123456789", "12345678900012"),
    recipient = Party("Client SAS", "987654321", "98765432100045"),
    lines = listOf(InvoiceLine("Prestation", quantity, Money(unitPriceHtCents), vatRate)),
    status = status,
)
