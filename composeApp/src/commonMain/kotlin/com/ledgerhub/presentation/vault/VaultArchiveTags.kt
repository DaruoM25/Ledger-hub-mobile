package com.ledgerhub.presentation.vault

/**
 * Semantic tags for testing the Digital Vault & Audit Trail UI (Robolectric & Device).
 */
object VaultArchiveTags {
    const val SCREEN = "vault_archive_screen"
    const val LOADING = "vault_archive_loading"
    const val BACK_BUTTON = "vault_back_button"

    const val KPI_SEALED = "vault_kpi_sealed_docs"
    const val KPI_INTEGRITY = "vault_kpi_integrity"
    const val KPI_STORAGE = "vault_kpi_storage"

    const val VERIFY_BUTTON = "vault_verify_button"
    const val SEAL_BUTTON = "vault_seal_button"
    const val EXPORT_BUTTON = "vault_export_button"

    const val ARCHIVE_LIST = "vault_archive_list"
    const val EMPTY_STATE = "vault_empty_state"
    fun archiveRow(invoiceNumber: String) = "vault_archive_row_$invoiceNumber"

    const val DIALOG = "vault_report_dialog"
    const val DIALOG_CLOSE = "vault_dialog_close"
    const val SNACKBAR = "vault_snackbar"
}
