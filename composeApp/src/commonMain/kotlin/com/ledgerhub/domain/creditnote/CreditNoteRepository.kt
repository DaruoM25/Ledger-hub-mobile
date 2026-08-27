package com.ledgerhub.domain.creditnote

/** Abstraction de la persistance/backend — la couche présentation ne connaît que ce contrat. */
interface CreditNoteRepository {
    suspend fun submitCreditNote(creditNote: CreditNote): Result<Unit>

    /** Liste des avoirs existants. */
    suspend fun fetchCreditNotes(): Result<List<CreditNote>>
}
