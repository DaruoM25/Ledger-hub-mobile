package com.ledgerhub.presentation.reconciliation

import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.reconciliation.BankTransactionRepository
import com.ledgerhub.domain.reconciliation.ReconcilePaymentUseCase
import com.ledgerhub.domain.reconciliation.ReconciliationRepository
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

/**
 * ViewModel de l'écran de Rapprochement Bancaire (US-18) — convention maison (cf.
 * `DirectoryViewModel`) : classe simple, [CoroutineScope] + [MutableStateFlow], aucun
 * `androidx.lifecycle` en commonMain.
 *
 * La sélection est **bascule** des deux côtés : re-toucher la carte déjà choisie la désélectionne.
 * Sur une liste où l'on compare des montants à l'œil, changer d'avis est le geste courant ; imposer
 * un bouton « annuler » pour cela serait une friction inutile.
 *
 * @param dispatcher injecté pour des tests sans dépendance au thread réel.
 */
class ReconciliationViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val bankTransactionRepository: BankTransactionRepository,
    private val reconciliationRepository: ReconciliationRepository,
    private val reconcilePayment: ReconcilePaymentUseCase =
        ReconcilePaymentUseCase(reconciliationRepository),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ReconciliationUiState())
    val uiState: StateFlow<ReconciliationUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun processIntent(intent: ReconciliationIntent) {
        when (intent) {
            is ReconciliationIntent.SelectTransaction -> selectTransaction(intent.transactionId)
            is ReconciliationIntent.SelectInvoice -> selectInvoice(intent.invoiceNumber)
            ReconciliationIntent.PerformMatch -> performMatch()
            ReconciliationIntent.ClearSelection -> _uiState.update {
                it.copy(selectedTransactionId = null, selectedInvoiceNumber = null)
            }

            ReconciliationIntent.MessageShown -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    fun showTab(tab: ReconciliationTab) = _uiState.update { it.copy(compactTab = tab) }

    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        scope.launch {
            val invoices = invoiceRepository.fetchInvoices().getOrElse { emptyList() }
            val transactions = bankTransactionRepository.fetchTransactions().getOrElse { emptyList() }
            val matches = reconciliationRepository.fetchMatches().getOrElse { emptyList() }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    transactions = transactions,
                    invoices = invoices.filter { it.status in ReconciliationUiState.PAYABLE_STATUSES },
                    matches = matches,
                )
            }
        }
    }

    /** Bascule : re-toucher la carte sélectionnée la relâche. */
    private fun selectTransaction(transactionId: String) = _uiState.update { state ->
        state.copy(
            selectedTransactionId = transactionId.takeIf { it != state.selectedTransactionId },
            errorMessage = null,
        )
    }

    private fun selectInvoice(invoiceNumber: String) = _uiState.update { state ->
        state.copy(
            selectedInvoiceNumber = invoiceNumber.takeIf { it != state.selectedInvoiceNumber },
            errorMessage = null,
        )
    }

    /**
     * Lettrage du couple sélectionné.
     *
     * En cas de succès, l'état est relu depuis les dépôts plutôt que reconstruit de mémoire : le
     * lettrage a fait passer la facture à `PAID`, elle sort donc des « en attente de paiement ».
     * Recopier ce raisonnement ici dupliquerait la règle de filtrage — et la laisserait diverger
     * le jour où la machine d'états évoluera.
     */
    private fun performMatch() {
        val state = _uiState.value
        val transaction = state.selectedTransaction ?: return
        val invoice = state.selectedInvoice ?: return
        if (state.isReconciling) return

        _uiState.update { it.copy(isReconciling = true, errorMessage = null) }
        scope.launch {
            reconcilePayment(transaction, invoice).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isReconciling = false,
                            selectedTransactionId = null,
                            selectedInvoiceNumber = null,
                            showEreportingBanner = true,
                        )
                    }
                    load()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isReconciling = false, errorMessage = error.message ?: "Lettrage impossible")
                    }
                },
            )
        }
    }

    /** À appeler quand l'écran quitte définitivement la composition. */
    fun dispose() = scope.cancel()
}
