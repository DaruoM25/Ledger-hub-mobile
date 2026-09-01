package com.ledgerhub.domain.reconciliation

/**
 * Double de test du dépôt de lettrage (US-18).
 *
 * Conserve les lettrages en mémoire pour que la règle « une écriture, une facture » soit
 * éprouvée de bout en bout : le second appel voit bien le premier. [failWith] permet de simuler
 * une panne de persistance sans complexifier les tests nominaux.
 */
class FakeReconciliationRepository(
    initialMatches: List<ReconciliationMatch> = emptyList(),
    private val failWith: Throwable? = null,
) : ReconciliationRepository {

    private val stored = initialMatches.toMutableList()

    /** Statuts de départ reçus par [reconcile] — le lettrage doit transmettre celui de la facture. */
    val recordedFromStatuses = mutableListOf<String>()

    val matches: List<ReconciliationMatch> get() = stored.toList()

    override suspend fun fetchMatches(): Result<List<ReconciliationMatch>> =
        failWith?.let { Result.failure(it) } ?: Result.success(stored.toList())

    override suspend fun reconcile(match: ReconciliationMatch, fromStatus: String): Result<Unit> {
        failWith?.let { return Result.failure(it) }
        stored += match
        recordedFromStatuses += fromStatus
        return Result.success(Unit)
    }
}

/** Double de test de la source bancaire. */
class FakeBankTransactionRepository(
    private val transactions: List<BankTransaction> = emptyList(),
) : BankTransactionRepository {
    override suspend fun fetchTransactions(): Result<List<BankTransaction>> =
        Result.success(transactions)
}
