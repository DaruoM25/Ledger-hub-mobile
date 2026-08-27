package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money

/** État immuable du formulaire — pattern UDF. Les champs sont stockés en texte brut (saisie utilisateur). */
data class InvoiceFormUiState(
    val invoiceNumber: String = "",
    val issueDate: String = "",
    val issuerName: String = "",
    val issuerSiren: String = "",
    val issuerSiret: String = "",
    val recipientName: String = "",
    val recipientSiren: String = "",
    val recipientSiret: String = "",
    val lines: List<InvoiceLineFormState> = listOf(InvoiceLineFormState()),
    val errors: Map<InvoiceFormField, String> = emptyMap(),
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    val submittedInvoice: Invoice? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
) {
    /** Verrouille tous les champs pendant la soumission — évite toute saisie concurrente. */
    val isFormEnabled: Boolean get() = submissionStatus != SubmissionStatus.Loading

    /** Une facture doit garder au moins une ligne — la suppression de la dernière est bloquée. */
    val canRemoveLines: Boolean get() = lines.size > 1 && isFormEnabled

    val isSubmitEnabled: Boolean
        get() = errors.isEmpty() && lines.all { it.errors.isEmpty() } && isFormEnabled
}
