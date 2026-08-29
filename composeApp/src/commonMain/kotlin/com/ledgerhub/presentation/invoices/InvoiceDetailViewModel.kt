package com.ledgerhub.presentation.invoices

import com.ledgerhub.data.repository.MockLedgerRepository
import com.ledgerhub.domain.audit.AuditRepository
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.ChangeInvoiceStatusUseCase
import com.ledgerhub.domain.invoice.InvoiceStatus
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
    /** Facultatif : sans lui, la Piste d'Audit Fiable n'est simplement pas affichée. */
    private val auditRepository: AuditRepository? = null,
    /** Facultatif : sans lui, aucune action de transition n'est proposée. */
    private val changeInvoiceStatusUseCase: ChangeInvoiceStatusUseCase? = null,
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
            val auditTrail = auditRepository?.entriesFor(invoiceNumber)?.getOrNull().orEmpty()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { invoice ->
                        current.copy(
                            isLoading = false,
                            invoice = invoice,
                            notFound = invoice == null,
                            creditNoteNumber = creditNoteNumber,
                            auditTrail = auditTrail,
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

    // ── Cycle de vie réglementaire (US-07) ───────────────────────────────────────────────────

    /** Ouvre la saisie du motif pour la transition [target]. Aucune écriture à ce stade. */
    fun startTransition(target: InvoiceStatus) {
        _uiState.update { it.copy(pendingTransition = target, transitionReason = "", transitionError = null) }
    }

    fun updateTransitionReason(reason: String) {
        _uiState.update { it.copy(transitionReason = reason) }
    }

    fun cancelTransition() {
        _uiState.update { it.copy(pendingTransition = null, transitionReason = "", transitionError = null) }
    }

    /**
     * Confirme la transition en cours. La validation appartient au use case : ce ViewModel ne
     * réimplémente pas la machine d'états, il en consomme le verdict.
     */
    fun confirmTransition() {
        val state = _uiState.value
        val invoice = state.invoice ?: return
        val target = state.pendingTransition ?: return
        val useCase = changeInvoiceStatusUseCase ?: return
        if (state.isTransitioning) return

        _uiState.update { it.copy(isTransitioning = true, transitionError = null) }
        scope.launch {
            useCase(invoice, target, state.transitionReason.takeIf { it.isNotBlank() }).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isTransitioning = false, pendingTransition = null, transitionReason = "")
                    }
                    // Rechargement : le statut ET la piste d'audit ont changé en base.
                    load()
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isTransitioning = false,
                            transitionError = throwable.message ?: "Transition impossible",
                        )
                    }
                },
            )
        }
    }

    /** À appeler depuis le cycle de vie de la plateforme (voir QuotesViewModel.onCleared). */
    fun onCleared() = scope.cancel()
}
