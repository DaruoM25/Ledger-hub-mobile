package com.ledgerhub.presentation.creditnoteform

import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

/**
 * État immuable du formulaire d'avoir — pattern UDF, symétrique à QuoteFormUiState.
 * [invoiceId], [issuerName], [recipientName] et les totaux sont pré-remplis (lecture seule côté
 * UI) à partir de la facture source dès la construction du ViewModel ; seuls [creditNoteNumber],
 * [issueDate] et [reason] sont saisis par l'utilisateur.
 */
data class CreditNoteFormUiState(
    val invoiceId: String = "",
    val issuerName: String = "",
    val recipientName: String = "",
    /** Toujours négatif ou nul — inversion des montants de la facture source (règle fiscale). */
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    val creditNoteNumber: String = "",
    val issueDate: String = "",
    val reason: String = "",
    val errors: Map<CreditNoteFormField, String> = emptyMap(),
    val submittedCreditNote: CreditNote? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
) {
    val isFormEnabled: Boolean get() = submissionStatus != SubmissionStatus.Loading

    /**
     * `false` une fois l'avoir déjà créé avec succès — évite qu'un second tap sur "Valider"
     * (bouton resté visible et cliquable à l'écran) ne déclenche une resoumission inutile.
     * QA manuelle (campagne 1) : ce n'était auparavant pas un crash (INSERT OR REPLACE côté
     * SQLDelight), mais un bouton "actif" après succès reste trompeur pour l'utilisateur.
     */
    val isSubmitEnabled: Boolean get() = errors.isEmpty() && isFormEnabled && submissionStatus != SubmissionStatus.Success
}
