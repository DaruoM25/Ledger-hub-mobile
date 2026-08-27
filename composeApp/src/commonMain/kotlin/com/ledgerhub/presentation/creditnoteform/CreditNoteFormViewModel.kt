package com.ledgerhub.presentation.creditnoteform

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.creditnote.SubmitCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
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

private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

/**
 * ViewModel du formulaire d'avoir — PATTERN UDF/MVVM, symétrique à QuoteFormViewModel.
 * [sourceInvoice] doit être finalisée ([Invoice.isCancellableByCreditNote]) : les montants
 * inversés sont calculés une seule fois, à la construction, et ne sont jamais recalculés
 * depuis une saisie utilisateur (ils ne sont pas éditables — seul le motif l'est).
 *
 * @param createCreditNoteUseCase injecté pour permettre les tests (facture non finalisable, etc.).
 * @param submitCreditNoteUseCase injecté pour permettre les tests avec un mock configurable.
 * @param dispatcher injecté pour permettre les tests sans dépendance au thread réel.
 */
class CreditNoteFormViewModel(
    private val sourceInvoice: Invoice,
    private val createCreditNoteUseCase: CreateCreditNoteUseCase = CreateCreditNoteUseCase(),
    private val submitCreditNoteUseCase: SubmitCreditNoteUseCase = SubmitCreditNoteUseCase(MockCreditNoteRepository()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val initialState = CreditNoteFormUiState(
        invoiceId = sourceInvoice.number,
        issuerName = sourceInvoice.issuer.name,
        recipientName = sourceInvoice.recipient.name,
        totalHt = Money(-sourceInvoice.totalHt.cents),
        totalVat = Money(-sourceInvoice.totalVat.cents),
        totalTtc = Money(-sourceInvoice.totalTtc.cents),
    )

    // L'état initial doit lui aussi refléter les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(revalidate(initialState))
    val uiState: StateFlow<CreditNoteFormUiState> = _uiState.asStateFlow()

    fun processIntent(intent: CreditNoteFormIntent) {
        if (intent is CreditNoteFormIntent.Submit) {
            submit()
            return
        }
        _uiState.update { current -> revalidate(applyChange(current, intent)) }
    }

    private fun applyChange(current: CreditNoteFormUiState, intent: CreditNoteFormIntent): CreditNoteFormUiState =
        when (intent) {
            is CreditNoteFormIntent.CreditNoteNumberChanged -> current.copy(creditNoteNumber = intent.value)
            is CreditNoteFormIntent.IssueDateChanged -> current.copy(issueDate = intent.value)
            is CreditNoteFormIntent.ReasonChanged -> current.copy(reason = intent.value)
            CreditNoteFormIntent.Submit -> current
        }

    private fun revalidate(state: CreditNoteFormUiState): CreditNoteFormUiState {
        val errors = mutableMapOf<CreditNoteFormField, String>()

        if (state.creditNoteNumber.isBlank()) {
            errors[CreditNoteFormField.CREDIT_NOTE_NUMBER] = "Le numéro d'avoir est requis"
        }
        if (!ISO_DATE_REGEX.matches(state.issueDate)) {
            errors[CreditNoteFormField.ISSUE_DATE] = "Date attendue au format AAAA-MM-JJ"
        }
        if (state.reason.isBlank()) {
            errors[CreditNoteFormField.REASON] = "Le motif d'annulation est obligatoire"
        }

        return state.copy(errors = errors)
    }

    private fun submit() {
        val revalidated = revalidate(_uiState.value)
        if (revalidated.errors.isNotEmpty()) {
            _uiState.value = revalidated
            return
        }

        val creation = createCreditNoteUseCase(
            invoice = sourceInvoice,
            number = revalidated.creditNoteNumber,
            issueDate = revalidated.issueDate,
            reason = revalidated.reason,
        )
        val creditNote = creation.getOrElse { throwable ->
            _uiState.value = revalidated.copy(
                submissionStatus = SubmissionStatus.Error(throwable.message ?: "Erreur inconnue lors de la création de l'avoir")
            )
            return
        }

        _uiState.value = revalidated.copy(submissionStatus = SubmissionStatus.Loading)

        scope.launch {
            val result = submitCreditNoteUseCase(creditNote)
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(submissionStatus = SubmissionStatus.Success, submittedCreditNote = creditNote)
                    },
                    onFailure = { throwable ->
                        current.copy(
                            submissionStatus = SubmissionStatus.Error(
                                throwable.message ?: "Erreur inconnue lors de la soumission"
                            )
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
