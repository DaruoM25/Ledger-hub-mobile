package com.ledgerhub.presentation.quotes

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import com.ledgerhub.domain.quote.Quote
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

/** Date d'émission utilisée pour la facture issue d'une conversion, faute d'horloge injectable (v1, pas de dépendance kotlinx-datetime). */
private const val CONVERSION_ISSUE_DATE_PLACEHOLDER = "2026-08-06"

/**
 * ViewModel de l'écran liste des devis — charge les devis existants et pilote "le bouton
 * magique" de conversion d'un devis Accepté en brouillon de facture (audit fiscal : voir
 * [ConvertQuoteToInvoiceUseCase] et [Invoice.sourceQuoteId]).
 */
class QuotesViewModel(
    private val quoteRepository: QuoteRepository = MockQuoteRepository(),
    private val convertQuoteToInvoiceUseCase: ConvertQuoteToInvoiceUseCase = ConvertQuoteToInvoiceUseCase(),
    private val submitInvoiceUseCase: SubmitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository()),
    private val invoiceNumberFor: (Quote) -> String = { quote -> "F-${quote.number}" },
    private val issueDateProvider: () -> String = { CONVERSION_ISSUE_DATE_PLACEHOLDER },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(QuotesUiState())
    val uiState: StateFlow<QuotesUiState> = _uiState.asStateFlow()

    init {
        processIntent(QuotesIntent.LoadQuotes)
    }

    fun processIntent(intent: QuotesIntent) {
        when (intent) {
            QuotesIntent.LoadQuotes -> loadQuotes()
            is QuotesIntent.ConvertToInvoice -> convert(intent.quoteNumber)
        }
    }

    private fun loadQuotes() {
        _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
        scope.launch {
            val result = quoteRepository.fetchQuotes()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { quotes -> current.copy(isLoading = false, quotes = quotes) },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            loadErrorMessage = throwable.message ?: "Erreur inconnue lors du chargement des devis",
                        )
                    },
                )
            }
        }
    }

    private fun convert(quoteNumber: String) {
        val quote = _uiState.value.quotes.firstOrNull { it.number == quoteNumber } ?: return
        if (!quote.isConvertibleToInvoice) return

        _uiState.update { it.copy(convertingQuoteNumber = quoteNumber, conversionErrorMessage = null) }

        scope.launch {
            val conversion = convertQuoteToInvoiceUseCase(
                quote = quote,
                invoiceNumber = invoiceNumberFor(quote),
                issueDate = issueDateProvider(),
            )

            val result = conversion.fold(
                onSuccess = { invoice -> submitInvoiceUseCase(invoice).map { invoice } },
                onFailure = { Result.failure(it) },
            )

            _uiState.update { current ->
                result.fold(
                    onSuccess = { invoice ->
                        current.copy(
                            convertingQuoteNumber = null,
                            convertedInvoicesByQuoteNumber = current.convertedInvoicesByQuoteNumber + (quote.number to invoice),
                        )
                    },
                    onFailure = { throwable ->
                        current.copy(
                            convertingQuoteNumber = null,
                            conversionErrorMessage = throwable.message ?: "Erreur inconnue lors de la conversion",
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
