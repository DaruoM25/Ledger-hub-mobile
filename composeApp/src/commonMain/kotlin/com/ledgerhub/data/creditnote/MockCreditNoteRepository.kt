package com.ledgerhub.data.creditnote

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteNumbering
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.creditnote.InvoiceAlreadyCreditedException
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
        return mutex.withLock {
            // Même règle que l'implémentation SQLDelight : une facture ne porte qu'un seul avoir.
            val existing = creditNotes.firstOrNull { it.invoiceId == creditNote.invoiceId }
            if (existing != null && existing.number != creditNote.number) {
                Result.failure(InvoiceAlreadyCreditedException(creditNote.invoiceId, existing.number))
            } else {
                creditNotes.removeAll { it.number == creditNote.number }
                creditNotes.add(creditNote)
                Result.success(Unit)
            }
        }
    }

    override suspend fun fetchCreditNotes(): Result<List<CreditNote>> {
        delay(simulatedDelayMillis)
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        return Result.success(mutex.withLock { creditNotes.toList() })
    }

    override suspend fun findByInvoiceNumber(invoiceNumber: String): Result<CreditNote?> {
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        return Result.success(mutex.withLock { creditNotes.firstOrNull { it.invoiceId == invoiceNumber } })
    }

    override suspend fun nextNumberForYear(year: Int): Result<String> {
        if (simulateFailure) {
            return Result.failure(IllegalStateException("Erreur réseau simulée (mock) : le serveur n'a pas répondu"))
        }
        val last = mutex.withLock {
            creditNotes.map { it.number }
                .filter { it.startsWith("${CreditNoteNumbering.PREFIX}-$year-") }
                .maxOrNull()
        }
        return Result.success(CreditNoteNumbering.next(year, last))
    }
}
