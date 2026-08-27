package com.ledgerhub.data.creditnote

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implémentation mock — voir [com.ledgerhub.data.invoice.MockInvoiceRepository] pour le
 * rationnel. Ne cascade pas le passage au statut Annulée de la facture d'origine (ce mock
 * n'a pas accès aux factures) : réservé aux tests unitaires du ViewModel (inversion des
 * montants) qui n'exercent pas cette cascade — voir SqlDelightCreditNoteRepositoryTest pour
 * la vérification de bout en bout.
 */
class MockCreditNoteRepository(
    private val simulatedDelayMillis: Long = 1_500L,
    private val simulateFailure: Boolean = false,
) : CreditNoteRepository {

    private val mutex = Mutex()
    private val creditNotes = mutableListOf<CreditNote>()

    override suspend fun submitCreditNote(creditNote: CreditNote): Result<Unit> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        mutex.withLock { creditNotes.add(creditNote) }
        return Result.success(Unit)
    }

    override suspend fun fetchCreditNotes(): Result<List<CreditNote>> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        return Result.success(mutex.withLock { creditNotes.toList() })
    }
}
