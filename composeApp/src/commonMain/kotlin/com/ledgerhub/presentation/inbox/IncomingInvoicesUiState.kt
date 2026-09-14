package com.ledgerhub.presentation.inbox

import com.ledgerhub.domain.inbox.ReceivedInvoice

/**
 * Filtres d'onglets de la boîte de réception des factures fournisseurs.
 */
enum class IncomingInvoicesFilterTab {
    ALL,
    ALERTS_ONLY,
    APPROVED,
}

/**
 * État de l'interface utilisateur pour la boîte de réception.
 */
data class IncomingInvoicesUiState(
    val isLoading: Boolean = false,
    val invoices: List<ReceivedInvoice> = emptyList(),
    val filteredInvoices: List<ReceivedInvoice> = emptyList(),
    val selectedTab: IncomingInvoicesFilterTab = IncomingInvoicesFilterTab.ALL,
    val selectedInvoiceForDetail: ReceivedInvoice? = null,
    val totalReceivedCount: Int = 0,
    val totalAlertsCount: Int = 0,
    val totalApprovedCount: Int = 0,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

/**
 * Intents d'action utilisateur pour la boîte de réception.
 */
sealed interface IncomingInvoicesIntent {
    data object Refresh : IncomingInvoicesIntent
    data class SelectTab(val tab: IncomingInvoicesFilterTab) : IncomingInvoicesIntent
    data class OpenInvoiceDetail(val invoice: ReceivedInvoice) : IncomingInvoicesIntent
    data object CloseInvoiceDetail : IncomingInvoicesIntent
    data class ApproveInvoice(val id: String) : IncomingInvoicesIntent
    data class RejectInvoice(val id: String, val reason: String) : IncomingInvoicesIntent
    data class ImportSimulatedInvoice(
        val supplierName: String,
        val supplierSiren: String,
        val supplierSiret: String,
        val invoiceNumber: String,
        val issueDate: String,
        val dueDate: String,
        val totalHtCents: Long,
        val totalVatCents: Long,
        val totalTtcCents: Long,
        val rawPayload: String = "",
    ) : IncomingInvoicesIntent
    data object DismissMessage : IncomingInvoicesIntent
}
