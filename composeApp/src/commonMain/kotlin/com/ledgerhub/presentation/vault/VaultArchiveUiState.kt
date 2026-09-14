package com.ledgerhub.presentation.vault

import com.ledgerhub.domain.vault.DigitalArchive
import com.ledgerhub.domain.vault.VaultIntegrityReport

/**
 * UI State for the Digital Vault & Audit Trail Screen.
 */
data class VaultArchiveUiState(
    val isLoading: Boolean = false,
    val isVerifying: Boolean = false,
    val archives: List<DigitalArchive> = emptyList(),
    val totalSizeInBytes: Long = 0L,
    val isIntegrityValid: Boolean = true,
    val verificationReport: VaultIntegrityReport? = null,
    val showReportDialog: Boolean = false,
    val exportSuccessMessage: String? = null,
    val errorMessage: String? = null
) {
    val sealedDocumentsCount: Int get() = archives.size
}

/**
 * Intents for the Digital Vault & Audit Trail Screen.
 */
sealed interface VaultArchiveIntent {
    data object LoadArchives : VaultArchiveIntent
    data object VerifyIntegrity : VaultArchiveIntent
    data object SealPendingInvoices : VaultArchiveIntent
    data object ExportAuditLog : VaultArchiveIntent
    data object DismissDialog : VaultArchiveIntent
    data object DismissSnackbar : VaultArchiveIntent
}
