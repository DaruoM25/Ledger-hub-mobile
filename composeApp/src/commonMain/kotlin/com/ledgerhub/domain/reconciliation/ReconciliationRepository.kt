package com.ledgerhub.domain.reconciliation

/**
 * Lecture des écritures bancaires et des lettrages déjà prononcés (US-18).
 *
 * Aucune écriture de transaction : le relevé bancaire est une donnée reçue, pas produite.
 */
interface BankTransactionRepository {
    suspend fun fetchTransactions(): Result<List<BankTransaction>>
}

/**
 * Écriture du lettrage.
 *
 * Séparée de [BankTransactionRepository] pour que [ReconcilePaymentUseCase] ne dépende que de ce
 * qu'il utilise, et que les doubles de test restent minces — même découpage que
 * [InvoiceStatusRepository][com.ledgerhub.domain.invoice.InvoiceStatusRepository].
 */
interface ReconciliationRepository {
    suspend fun fetchMatches(): Result<List<ReconciliationMatch>>

    /**
     * Enregistre le lettrage **et** passe la facture à `PAID` avec sa trace d'audit, dans une
     * seule transaction. Un lettrage sans changement de statut laisserait une facture soldée
     * affichée comme impayée ; un statut sans lettrage rendrait le rapprochement introuvable.
     *
     * La validité de la transition de statut n'est pas vérifiée ici : elle l'est en amont par
     * [ReconcilePaymentUseCase], avant tout accès à la base.
     */
    suspend fun reconcile(match: ReconciliationMatch, fromStatus: String): Result<Unit>
}
