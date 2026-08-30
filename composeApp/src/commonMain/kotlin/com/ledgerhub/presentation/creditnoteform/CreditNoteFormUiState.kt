package com.ledgerhub.presentation.creditnoteform

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteReason
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatBreakdown
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

/**
 * État immuable du formulaire d'avoir — pattern UDF, symétrique à `QuoteFormUiState`.
 *
 * Tout est pré-rempli en lecture seule depuis la facture source : référence croisée
 * ([invoiceId] + [originalInvoiceDate]), lignes recopiées, assiettes de TVA et totaux inversés.
 * Le [creditNoteNumber] est **attribué par la séquence** et non saisi (obligation de continuité).
 * Seuls [issueDate] et [reason] relèvent de l'utilisateur.
 */
data class CreditNoteFormUiState(
    val invoiceId: String = "",
    /** Date d'émission de la facture annulée — seconde moitié de la référence croisée. */
    val originalInvoiceDate: String = "",
    val issuerName: String = "",
    val recipientName: String = "",
    /** Lignes recopiées de la facture d'origine — prix positifs, sens comptable porté par les totaux. */
    val lines: List<InvoiceLine> = emptyList(),
    /** Assiettes de TVA par taux, inversées. */
    val vatBreakdown: List<VatBreakdown> = emptyList(),
    /** Toujours négatif ou nul — inversion des montants de la facture source (règle fiscale). */
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    val creditNoteNumber: String = "",
    val issueDate: String = "",
    /** Raison légale **effective** transmise au domaine — label d'un motif type, ou texte libre. */
    val reason: String = "",
    /** US-10 : motif type sélectionné dans le formulaire (`null` tant qu'aucun choix). */
    val reasonKind: CreditNoteReason? = null,
    /** US-10 : texte saisi quand [CreditNoteReason.OTHER] est sélectionné. */
    val reasonFreeText: String = "",
    val errors: Map<CreditNoteFormField, String> = emptyMap(),
    val touchedFields: Set<CreditNoteFormField> = emptySet(),
    val submitAttempted: Boolean = false,
    val submittedCreditNote: CreditNote? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
    /**
     * Numéro de l'avoir déjà émis pour cette facture, le cas échéant : l'émission est alors
     * refusée d'emblée, avant toute saisie (voir [InvoiceAlreadyCreditedException]
     * [com.ledgerhub.domain.creditnote.InvoiceAlreadyCreditedException]).
     */
    val blockedByExistingCreditNote: String? = null,
) {
    val isFormEnabled: Boolean
        get() = submissionStatus != SubmissionStatus.Loading && blockedByExistingCreditNote == null

    /**
     * `false` une fois l'avoir créé — un bouton resté actif après succès est trompeur, et une
     * resoumission n'a aucun sens sur une pièce fiscale déjà émise (QA manuelle, campagne 1).
     */
    val isSubmitEnabled: Boolean
        get() = errors.isEmpty() && isFormEnabled && submissionStatus != SubmissionStatus.Success

    /** Erreurs présentées : un champ jamais saisi reste neutre jusqu'à la première tentative (D-02). */
    val visibleErrors: Map<CreditNoteFormField, String>
        get() = if (submitAttempted) errors else errors.filterKeys { it in touchedFields }
}
