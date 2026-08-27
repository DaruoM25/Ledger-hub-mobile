package com.ledgerhub.presentation.invoiceform

/** État de la soumission de la facture — piloté par [SubmitInvoiceUseCase][com.ledgerhub.domain.invoice.SubmitInvoiceUseCase]. */
sealed interface SubmissionStatus {
    data object Idle : SubmissionStatus
    data object Loading : SubmissionStatus
    data object Success : SubmissionStatus
    data class Error(val message: String) : SubmissionStatus
}
