package com.ledgerhub.presentation.invoices

import com.ledgerhub.data.repository.MockLedgerRepository
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.repository.LedgerRepository
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
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

/** Intentions utilisateur de l'écran liste des factures (UDF). */
sealed interface InvoiceListIntent {
    data object Load : InvoiceListIntent
    data object Retry : InvoiceListIntent
    data class FilterSelected(val filter: InvoiceStatusFilter) : InvoiceListIntent
}

/** Message présenté à l'utilisateur pour toute erreur de chargement (réseau, 5xx, timeout…). */
/** Longueur de la partie calendaire d'un instant ISO 8601 : `AAAA-MM-JJ`. */
private const val ISO_DATE_LENGTH = 10

private const val NETWORK_ERROR_MESSAGE =
    "Impossible de joindre le serveur Ledger local. Vérifiez que le backend de développement " +
        "est démarré, puis réessayez."

/**
 * ViewModel de l'écran liste des factures (Sprint 1 — US-02). Charge les factures depuis
 * [LedgerRepository] (API distante créée en US-01), délègue tri et filtrage à
 * [InvoiceListUiState] et convertit toute erreur en message lisible.
 *
 * Convention maison (cf. [com.ledgerhub.presentation.quotes.QuotesViewModel]) : classe simple,
 * [CoroutineScope] + [MutableStateFlow], aucun `androidx.lifecycle` dans commonMain.
 */
class InvoiceListViewModel(
    private val ledgerRepository: LedgerRepository = MockLedgerRepository(),
    /** Facultatif : sans lui, aucune mention croisée n'est affichée sur les cartes. */
    private val creditNoteRepository: CreditNoteRepository? = null,
    /** Date du jour du filtre « En retard » (US-19) — injectée pour rester déterministe en test. */
    private val clock: Clock = SystemClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(InvoiceListUiState())
    val uiState: StateFlow<InvoiceListUiState> = _uiState.asStateFlow()

    init {
        processIntent(InvoiceListIntent.Load)
    }

    fun processIntent(intent: InvoiceListIntent) {
        when (intent) {
            InvoiceListIntent.Load, InvoiceListIntent.Retry -> load()
            is InvoiceListIntent.FilterSelected ->
                _uiState.update { it.copy(statusFilter = intent.filter) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val result = ledgerRepository.fetchInvoices()
            // Index avoir par facture — alimente la mention croisée sur les cartes (US-05).
            val creditNotes = creditNoteRepository?.fetchCreditNotes()?.getOrNull()
                ?.associate { it.invoiceId to it.number }
                ?: emptyMap()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { invoices ->
                        current.copy(
                            isLoading = false,
                            invoices = invoices,
                            errorMessage = null,
                            creditNotesByInvoice = creditNotes,
                            // Relue a chaque chargement : la liste peut rester ouverte a cheval
                            // sur minuit, et une facture echue entre-temps doit alors basculer.
                            today = clock.nowIso().take(ISO_DATE_LENGTH),
                        )
                    },
                    onFailure = {
                        current.copy(isLoading = false, errorMessage = NETWORK_ERROR_MESSAGE)
                    },
                )
            }
        }
    }

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : `onDestroy()` / `onCleared()` du holder. iOS : `deinit` du ViewController.
     */
    fun onCleared() = scope.cancel()
}
