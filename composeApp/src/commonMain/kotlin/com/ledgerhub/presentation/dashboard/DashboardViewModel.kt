package com.ledgerhub.presentation.dashboard

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.dashboard.GetDashboardAnalyticsUseCase
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.quote.QuoteRepository
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

/** ViewModel de l'écran tableau de bord — charge les indicateurs financiers au démarrage. */
class DashboardViewModel(
    private val getDashboardAnalyticsUseCase: GetDashboardAnalyticsUseCase = GetDashboardAnalyticsUseCase(
        invoiceRepository = MockInvoiceRepository(),
        creditNoteRepository = MockCreditNoteRepository(),
        quoteRepository = MockQuoteRepository(),
    ),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    constructor(
        invoiceRepository: InvoiceRepository,
        creditNoteRepository: CreditNoteRepository,
        quoteRepository: QuoteRepository,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        getDashboardAnalyticsUseCase = GetDashboardAnalyticsUseCase(invoiceRepository, creditNoteRepository, quoteRepository),
        dispatcher = dispatcher,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        processIntent(DashboardIntent.LoadDashboard)
    }

    fun processIntent(intent: DashboardIntent) {
        when (intent) {
            DashboardIntent.LoadDashboard -> load()
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
        scope.launch {
            val result = getDashboardAnalyticsUseCase()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { analytics -> current.copy(isLoading = false, analytics = analytics) },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            loadErrorMessage = throwable.message ?: "Erreur inconnue lors du chargement du tableau de bord",
                        )
                    },
                )
            }
        }
    }

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : depuis onDestroy() ou rememberViewModel().
     * iOS     : depuis le deinit de la UIViewController.
     */
    fun onCleared() = scope.cancel()
}
