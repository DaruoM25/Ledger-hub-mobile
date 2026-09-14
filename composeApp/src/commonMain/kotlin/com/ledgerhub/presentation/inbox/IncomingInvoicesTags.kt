package com.ledgerhub.presentation.inbox

/**
 * Tags sémantiques Compose pour l'écran Factures Reçues (Inbox US-33).
 */
object IncomingInvoicesTags {
    const val SCREEN = "incoming_invoices_screen"
    const val BACK_BUTTON = "incoming_invoices_back_button"
    const val KPI_RECEIVED = "incoming_invoices_kpi_received"
    const val KPI_ALERTS = "incoming_invoices_kpi_alerts"
    const val KPI_APPROVED = "incoming_invoices_kpi_approved"
    const val TAB_ALL = "incoming_invoices_tab_all"
    const val TAB_ALERTS = "incoming_invoices_tab_alerts"
    const val TAB_APPROVED = "incoming_invoices_tab_approved"
    const val IMPORT_BUTTON = "incoming_invoices_import_button"
    const val INVOICE_LIST = "incoming_invoices_list"
    const val EMPTY_STATE = "incoming_invoices_empty_state"
    const val LOADING = "incoming_invoices_loading"
    const val DETAIL_DIALOG = "incoming_invoices_detail_dialog"
    const val APPROVE_BUTTON = "incoming_invoices_approve_button"
    const val REJECT_BUTTON = "incoming_invoices_reject_button"
    const val CLOSE_DIALOG_BUTTON = "incoming_invoices_close_dialog_button"
    const val DUPLICATE_BANNER = "incoming_invoices_duplicate_banner"
    const val SNACKBAR = "incoming_invoices_snackbar"

    fun invoiceCard(id: String) = "incoming_invoice_card_$id"
}
