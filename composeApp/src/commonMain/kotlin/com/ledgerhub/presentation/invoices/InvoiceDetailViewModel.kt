package com.ledgerhub.presentation.invoices

import com.ledgerhub.data.repository.MockLedgerRepository
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.repository.LedgerRepository
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
 * ViewModel de l'écran de détail d'une facture (Sprint 1 — US-02). Charge la facture
 * [invoiceNumber] depuis [LedgerRepository] ; un `null` côté serveur (404) se traduit par
 * [InvoiceDetailUiState.notFound], une erreur réseau par [InvoiceDetailUiState.errorMessage].
 *
 * Convention maison (cf. [com.ledgerhub.presentation.quotes.QuotesViewModel]).
 */
class InvoiceDetailViewModel(
    private val invoiceNumber: String,
    private val ledgerRepository: LedgerRepository = MockLedgerRepository(),
    /** Facultatif : sans lui, la mention croisée vers l'avoir n'est simplement pas affichée. */
    private val creditNoteRepository: CreditNoteRepository? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(InvoiceDetailUiState())
    val uiState: StateFlow<InvoiceDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Recharge la facture après une erreur réseau. */
    fun retry() = load()

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, notFound = false) }
        scope.launch {
            val result = ledgerRepository.getInvoiceDetail(invoiceNumber)
            // Mention croisée US-05 : l'avoir qui annule cette facture, s'il existe.
            val creditNoteNumber = creditNoteRepository
                ?.findByInvoiceNumber(invoiceNumber)?.getOrNull()?.number
            _uiState.update { current ->
                result.fold(
                    onSuccess = { invoice ->
                        current.copy(
                            isLoading = false,
                            invoice = invoice,
                            notFound = invoice == null,
                            creditNoteNumber = creditNoteNumber,
                        )
                    },
                    onFailure = {
                        current.copy(
                            isLoading = false,
                            errorMessage = "Impossible de charger la facture $invoiceNumber. " +
                                "Vérifiez la connexion au serveur Ledger local.",
                        )
                    },
                )
            }
        }
    }

    /** À appeler depuis le cycle de vie de la plateforme (voir QuotesViewModel.onCleared). */
    fun onCleared() = scope.cancel()
}
