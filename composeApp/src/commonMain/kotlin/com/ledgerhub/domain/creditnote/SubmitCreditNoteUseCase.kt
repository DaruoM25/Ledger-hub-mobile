package com.ledgerhub.domain.creditnote

/** Cas d'usage unique : soumettre un avoir via le [CreditNoteRepository] injecté. */
class SubmitCreditNoteUseCase(private val repository: CreditNoteRepository) {
    suspend operator fun invoke(creditNote: CreditNote): Result<Unit> = repository.submitCreditNote(creditNote)
}
