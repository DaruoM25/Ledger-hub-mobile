package com.ledgerhub.data.reconciliation

import com.ledgerhub.db.BankTransaction as BankTransactionRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.ReconciliationMatch as ReconciliationMatchRow
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.BankTransactionRepository
import com.ledgerhub.domain.reconciliation.ReconciliationMatch
import com.ledgerhub.domain.reconciliation.ReconciliationRepository
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Persistance du rapprochement bancaire (US-18) — voir `BankTransaction.sq` et
 * `ReconciliationMatch.sq`.
 *
 * Assure les deux contrats de lecture et l'unique chemin d'écriture du lettrage.
 */
class SqlDelightReconciliationRepository(
    private val database: LedgerHubDatabase,
    /** Horodatage de la trace d'audit — injecté pour rester déterministe en test. */
    private val clock: Clock = SystemClock,
) : BankTransactionRepository, ReconciliationRepository {

    override suspend fun fetchTransactions(): Result<List<BankTransaction>> = runCatching {
        database.bankTransactionQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun fetchMatches(): Result<List<ReconciliationMatch>> = runCatching {
        database.reconciliationMatchQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    /**
     * Lettrage, passage à [InvoiceStatus.PAID] et trace d'audit — **une seule transaction**.
     *
     * C'est la raison d'être de cette méthode. Écrits séparément, ces trois faits pourraient
     * diverger : une facture soldée en base mais toujours affichée « en attente », ou une
     * transition de statut sans lettrage donc sans justification retrouvable. La trace est
     * inscrite exactement comme le fait
     * [SqlDelightInvoiceRepository.changeStatus][com.ledgerhub.data.invoice.SqlDelightInvoiceRepository.changeStatus] :
     * la Piste d'Audit Fiable ne distingue pas un paiement constaté par rapprochement d'un
     * paiement saisi à la main, et elle n'a pas à le faire.
     *
     * La validation de la transition a eu lieu en amont, dans
     * [ReconcilePaymentUseCase][com.ledgerhub.domain.reconciliation.ReconcilePaymentUseCase].
     */
    @OptIn(ExperimentalUuidApi::class)
    override suspend fun reconcile(match: ReconciliationMatch, fromStatus: String): Result<Unit> =
        runCatching {
            database.transaction {
                database.reconciliationMatchQueries.insert(
                    transactionId = match.transactionId,
                    invoiceNumber = match.invoiceNumber,
                    matchedAt = match.matchedAtIso,
                    deltaCents = match.deltaCents,
                )
                database.invoiceQueries.updateStatus(InvoiceStatus.PAID.name, match.invoiceNumber)
                database.auditLogQueries.insert(
                    id = Uuid.random().toString(),
                    invoiceNumber = match.invoiceNumber,
                    fromStatus = fromStatus,
                    toStatus = InvoiceStatus.PAID.name,
                    // Le motif nomme l'écriture bancaire : en contrôle, c'est ce qui rattache la
                    // transition au relevé qui la justifie.
                    reason = "Rapprochement bancaire — transaction ${match.transactionId}",
                    createdAt = clock.nowIso(),
                )
            }
        }

    /**
     * Enregistre un relevé. Réservé à l'alimentation depuis la source bancaire — `INSERT OR
     * REPLACE` rend le semis idempotent, une même écriture relue ne se dédouble pas.
     */
    suspend fun saveTransactions(transactions: List<BankTransaction>): Result<Unit> = runCatching {
        database.transaction {
            transactions.forEach { transaction ->
                database.bankTransactionQueries.insert(
                    id = transaction.id,
                    label = transaction.label,
                    amountCents = transaction.amount.cents,
                    valueDate = transaction.valueDateIso,
                    counterparty = transaction.counterparty,
                )
            }
        }
    }

    suspend fun countTransactions(): Result<Long> = runCatching {
        database.bankTransactionQueries.countAll().executeAsOne()
    }

    private fun BankTransactionRow.toDomain(): BankTransaction = BankTransaction(
        id = id,
        label = label,
        amount = Money(amountCents),
        valueDateIso = valueDate,
        counterparty = counterparty,
    )

    private fun ReconciliationMatchRow.toDomain(): ReconciliationMatch = ReconciliationMatch(
        transactionId = transactionId,
        invoiceNumber = invoiceNumber,
        matchedAtIso = matchedAt,
        deltaCents = deltaCents,
    )
}
