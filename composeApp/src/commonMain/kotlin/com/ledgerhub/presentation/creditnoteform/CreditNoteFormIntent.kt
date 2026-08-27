package com.ledgerhub.presentation.creditnoteform

sealed interface CreditNoteFormIntent {
    data class CreditNoteNumberChanged(val value: String) : CreditNoteFormIntent
    data class IssueDateChanged(val value: String) : CreditNoteFormIntent

    /** Motif légal de l'annulation — obligatoire, voir [com.ledgerhub.domain.creditnote.CreditNote]. */
    data class ReasonChanged(val value: String) : CreditNoteFormIntent

    data object Submit : CreditNoteFormIntent
}
