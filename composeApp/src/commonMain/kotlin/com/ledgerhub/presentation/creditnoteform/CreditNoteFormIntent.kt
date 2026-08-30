package com.ledgerhub.presentation.creditnoteform

import com.ledgerhub.domain.creditnote.CreditNoteReason

sealed interface CreditNoteFormIntent {
    data class CreditNoteNumberChanged(val value: String) : CreditNoteFormIntent
    data class IssueDateChanged(val value: String) : CreditNoteFormIntent

    /**
     * Motif légal libre — conservé pour la compatibilité : équivaut à choisir
     * [CreditNoteReason.OTHER] et saisir [value].
     */
    data class ReasonChanged(val value: String) : CreditNoteFormIntent

    /** US-10 : choix d'un motif type (ou « Autre motif » qui déverrouille le champ libre). */
    data class ReasonKindChanged(val kind: CreditNoteReason) : CreditNoteFormIntent

    /** US-10 : texte du motif libre, pris en compte quand [CreditNoteReason.OTHER] est sélectionné. */
    data class ReasonFreeTextChanged(val value: String) : CreditNoteFormIntent

    data object Submit : CreditNoteFormIntent
}
