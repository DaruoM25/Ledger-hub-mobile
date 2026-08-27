package com.ledgerhub.presentation.invoicedetail

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.Invoice
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
 * ViewModel de l'écran de détail d'une facture. Vérifie, à la construction, qu'aucun avoir
 * n'existe déjà pour [invoice] — condition nécessaire (avec [Invoice.isCancellableByCreditNote])
 * à l'affichage du bouton "Annuler par un avoir" (voir [InvoiceDetailUiState.canCreateCreditNote]).
 */
class InvoiceDetailViewModel(
    invoice: Invoice,
    private val creditNoteRepository: CreditNoteRepository = MockCreditNoteRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(InvoiceDetailUiState(invoice = invoice))
    val uiState: StateFlow<InvoiceDetailUiState> = _uiState.asStateFlow()

    init {
        refreshCreditNoteStatus()
    }

    private fun refreshCreditNoteStatus() {
        val invoiceNumber = _uiState.value.invoice.number
        scope.launch {
            val hasCreditNote = creditNoteRepository.fetchCreditNotes()
                .getOrDefault(emptyList())
                .any { it.invoiceId == invoiceNumber }
            _uiState.update { it.copy(hasCreditNote = hasCreditNote) }
        }
    }

    /** À appeler depuis le cycle de vie de la plateforme (voir CreditNoteFormViewModel.onCleared). */
    fun onCleared() = scope.cancel()
}
