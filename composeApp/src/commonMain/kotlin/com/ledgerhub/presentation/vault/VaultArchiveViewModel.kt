package com.ledgerhub.presentation.vault

import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.vault.GenerateInvoiceSealUseCase
import com.ledgerhub.domain.vault.VaultRepository
import com.ledgerhub.domain.vault.VerifyVaultIntegrityUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VaultArchiveViewModel(
    private val vaultRepository: VaultRepository,
    private val generateInvoiceSealUseCase: GenerateInvoiceSealUseCase,
    private val verifyVaultIntegrityUseCase: VerifyVaultIntegrityUseCase,
    private val invoiceRepository: InvoiceRepository? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _uiState = MutableStateFlow(VaultArchiveUiState(isLoading = true))
    val uiState: StateFlow<VaultArchiveUiState> = _uiState.asStateFlow()

    init {
        loadArchives()
    }

    fun processIntent(intent: VaultArchiveIntent) {
        when (intent) {
            is VaultArchiveIntent.LoadArchives -> loadArchives()
            is VaultArchiveIntent.VerifyIntegrity -> verifyIntegrity()
            is VaultArchiveIntent.SealPendingInvoices -> sealPendingInvoices()
            is VaultArchiveIntent.ExportAuditLog -> exportAuditLog()
            is VaultArchiveIntent.DismissDialog -> _uiState.update { it.copy(showReportDialog = false) }
            is VaultArchiveIntent.DismissSnackbar -> _uiState.update { it.copy(exportSuccessMessage = null, errorMessage = null) }
        }
    }

    private fun loadArchives() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val archivesResult = vaultRepository.fetchArchives()

            archivesResult.fold(
                onSuccess = { archives ->
                    val totalSize = archives.sumOf { it.archiveSize }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            archives = archives,
                            totalSizeInBytes = totalSize
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Erreur de chargement du coffre-fort"
                        )
                    }
                }
            )
        }
    }

    private fun verifyIntegrity() {
        scope.launch {
            _uiState.update { it.copy(isVerifying = true) }
            val reportResult = verifyVaultIntegrityUseCase()

            reportResult.fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            isIntegrityValid = report.isCompletelyValid,
                            verificationReport = report,
                            showReportDialog = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = error.message ?: "Erreur lors de la vérification"
                        )
                    }
                }
            )
        }
    }

    private fun sealPendingInvoices() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            if (invoiceRepository != null) {
                val invoicesResult = invoiceRepository.fetchInvoices()
                val invoices = invoicesResult.getOrDefault(emptyList())
                val existingArchives = vaultRepository.fetchArchives().getOrDefault(emptyList())
                val sealedInvoiceNumbers = existingArchives.map { it.invoiceNumber }.toSet()

                for (invoice in invoices) {
                    if (invoice.number !in sealedInvoiceNumbers) {
                        generateInvoiceSealUseCase(invoice.number)
                    }
                }
            }
            loadArchives()
        }
    }

    private fun exportAuditLog() {
        scope.launch {
            val logsResult = vaultRepository.fetchAuditTrail()
            logsResult.fold(
                onSuccess = { logs ->
                    _uiState.update {
                        it.copy(
                            exportSuccessMessage = "Journal d'audit exporté (${logs.size} entrées certifiées)."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = "Échec de l'export du journal d'audit: ${error.message}")
                    }
                }
            )
        }
    }

    fun onCleared() {
        scope.cancel()
    }
}
