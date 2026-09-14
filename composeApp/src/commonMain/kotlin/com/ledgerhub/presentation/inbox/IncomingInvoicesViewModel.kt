package com.ledgerhub.presentation.inbox

import com.ledgerhub.domain.inbox.InboxRepository
import com.ledgerhub.domain.inbox.ProcessReceivedInvoiceResult
import com.ledgerhub.domain.inbox.ProcessReceivedInvoiceUseCase
import com.ledgerhub.domain.inbox.ReceivedInvoice
import com.ledgerhub.domain.inbox.ReceivedInvoiceStatus
import com.ledgerhub.domain.invoice.Money
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel gérant la boîte de réception des factures fournisseurs et les alertes de doublons (US-33).
 */
class IncomingInvoicesViewModel(
    private val inboxRepository: InboxRepository,
    private val processReceivedInvoiceUseCase: ProcessReceivedInvoiceUseCase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(dispatcher)
    private val _uiState = MutableStateFlow(IncomingInvoicesUiState(isLoading = true))
    val uiState: StateFlow<IncomingInvoicesUiState> = _uiState.asStateFlow()

    init {
        loadInvoices()
    }

    fun processIntent(intent: IncomingInvoicesIntent) {
        when (intent) {
            is IncomingInvoicesIntent.Refresh -> loadInvoices()
            is IncomingInvoicesIntent.SelectTab -> onSelectTab(intent.tab)
            is IncomingInvoicesIntent.OpenInvoiceDetail -> onOpenInvoiceDetail(intent.invoice)
            is IncomingInvoicesIntent.CloseInvoiceDetail -> onCloseInvoiceDetail()
            is IncomingInvoicesIntent.ApproveInvoice -> onApproveInvoice(intent.id)
            is IncomingInvoicesIntent.RejectInvoice -> onRejectInvoice(intent.id, intent.reason)
            is IncomingInvoicesIntent.ImportSimulatedInvoice -> onImportSimulatedInvoice(intent)
            is IncomingInvoicesIntent.DismissMessage -> onDismissMessage()
        }
    }

    private fun loadInvoices() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = inboxRepository.fetchReceivedInvoices()
            val list = result.getOrDefault(emptyList())

            val receivedCount = list.size
            val alertsCount = list.count { it.status == ReceivedInvoiceStatus.DUPLICATE_ALERT }
            val approvedCount = list.count { it.status == ReceivedInvoiceStatus.APPROVED }

            val filtered = filterList(list, _uiState.value.selectedTab)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    invoices = list,
                    filteredInvoices = filtered,
                    totalReceivedCount = receivedCount,
                    totalAlertsCount = alertsCount,
                    totalApprovedCount = approvedCount,
                )
            }
        }
    }

    private fun onSelectTab(tab: IncomingInvoicesFilterTab) {
        _uiState.update {
            it.copy(
                selectedTab = tab,
                filteredInvoices = filterList(it.invoices, tab),
            )
        }
    }

    private fun filterList(
        invoices: List<ReceivedInvoice>,
        tab: IncomingInvoicesFilterTab,
    ): List<ReceivedInvoice> {
        return when (tab) {
            IncomingInvoicesFilterTab.ALL -> invoices
            IncomingInvoicesFilterTab.ALERTS_ONLY -> invoices.filter { it.status == ReceivedInvoiceStatus.DUPLICATE_ALERT }
            IncomingInvoicesFilterTab.APPROVED -> invoices.filter { it.status == ReceivedInvoiceStatus.APPROVED }
        }
    }

    private fun onOpenInvoiceDetail(invoice: ReceivedInvoice) {
        _uiState.update { it.copy(selectedInvoiceForDetail = invoice) }
    }

    private fun onCloseInvoiceDetail() {
        _uiState.update { it.copy(selectedInvoiceForDetail = null) }
    }

    private fun onApproveInvoice(id: String) {
        scope.launch {
            val result = processReceivedInvoiceUseCase.approveInvoice(id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        infoMessage = "Facture validée avec succès pour le paiement",
                        selectedInvoiceForDetail = null,
                    )
                }
                loadInvoices()
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Impossible d'approuver cette facture",
                    )
                }
            }
        }
    }

    private fun onRejectInvoice(id: String, reason: String) {
        scope.launch {
            val result = processReceivedInvoiceUseCase.rejectInvoice(id, reason)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        infoMessage = "Facture rejetée",
                        selectedInvoiceForDetail = null,
                    )
                }
                loadInvoices()
            } else {
                _uiState.update {
                    it.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Impossible de rejeter la facture",
                    )
                }
            }
        }
    }

    private fun onImportSimulatedInvoice(intent: IncomingInvoicesIntent.ImportSimulatedInvoice) {
        scope.launch {
            val id = "REC-${intent.invoiceNumber}-${intent.supplierSiren.takeLast(4)}"
            val now = "2026-09-14T12:00:00"
            val result = processReceivedInvoiceUseCase(
                id = id,
                supplierName = intent.supplierName,
                supplierSiren = intent.supplierSiren,
                supplierSiret = intent.supplierSiret,
                invoiceNumber = intent.invoiceNumber,
                issueDate = intent.issueDate,
                dueDate = intent.dueDate,
                totalHt = Money(intent.totalHtCents),
                totalVat = Money(intent.totalVatCents),
                totalTtc = Money(intent.totalTtcCents),
                rawPayload = intent.rawPayload,
                receivedAt = now,
            )

            when (result) {
                is ProcessReceivedInvoiceResult.Success -> {
                    _uiState.update { it.copy(infoMessage = "Nouvelle facture fournisseur importée") }
                }
                is ProcessReceivedInvoiceResult.DuplicateDetected -> {
                    _uiState.update { it.copy(errorMessage = "ALERTE : Doublon détecté (${result.reason})") }
                }
                is ProcessReceivedInvoiceResult.Failure -> {
                    _uiState.update { it.copy(errorMessage = "Erreur d'import : ${result.error.message}") }
                }
            }
            loadInvoices()
        }
    }

    private fun onDismissMessage() {
        _uiState.update { it.copy(infoMessage = null, errorMessage = null) }
    }
}
