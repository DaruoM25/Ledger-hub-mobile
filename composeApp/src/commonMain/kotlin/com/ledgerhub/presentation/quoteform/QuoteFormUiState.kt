package com.ledgerhub.presentation.quoteform

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

/** État immuable du formulaire — pattern UDF. Les champs sont stockés en texte brut (saisie utilisateur). */
data class QuoteFormUiState(
    val quoteNumber: String = "",
    val issueDate: String = "",
    val validityDate: String = "",
    val issuerName: String = "",
    val issuerSiren: String = "",
    val issuerSiret: String = "",
    val recipientName: String = "",
    val recipientSiren: String = "",
    val recipientSiret: String = "",
    val lines: List<QuoteLineFormState> = listOf(QuoteLineFormState()),
    val errors: Map<QuoteFormField, String> = emptyMap(),
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    val submittedQuote: Quote? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
) {
    /** Verrouille tous les champs pendant la soumission — évite toute saisie concurrente. */
    val isFormEnabled: Boolean get() = submissionStatus != SubmissionStatus.Loading

    /** Un devis doit garder au moins une ligne — la suppression de la dernière est bloquée. */
    val canRemoveLines: Boolean get() = lines.size > 1 && isFormEnabled

    val isSubmitEnabled: Boolean
        get() = errors.isEmpty() && lines.all { it.errors.isEmpty() } && isFormEnabled
}
