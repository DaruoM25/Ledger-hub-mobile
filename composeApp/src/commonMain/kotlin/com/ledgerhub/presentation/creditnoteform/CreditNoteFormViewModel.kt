package com.ledgerhub.presentation.creditnoteform

import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.domain.creditnote.CreateCreditNoteUseCase
import com.ledgerhub.domain.creditnote.CreditNoteNumbering
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.creditnote.InvoiceAlreadyCreditedException
import com.ledgerhub.domain.creditnote.SubmitCreditNoteUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatBreakdown
import com.ledgerhub.domain.invoice.computeVatBreakdown
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
 * ViewModel du formulaire d'avoir — PATTERN UDF/MVVM, symétrique à `QuoteFormViewModel`.
 *
 * [sourceInvoice] doit être finalisée ([Invoice.isCancellableByCreditNote]). Tout ce qui découle
 * de la facture — lignes, assiettes, montants inversés, référence croisée — est calculé une seule
 * fois à la construction et n'est jamais recalculé depuis une saisie : seuls la date d'émission
 * et le motif sont éditables.
 *
 * Au démarrage, deux interrogations du dépôt : le numéro séquentiel de l'exercice, et l'éventuel
 * avoir déjà émis pour cette facture — qui bloque alors le formulaire d'emblée.
 *
 * @param creditNoteRepository source de la numérotation et du contrôle d'unicité.
 * @param dispatcher injecté pour les tests sans dépendance au thread réel.
 */
class CreditNoteFormViewModel(
    private val sourceInvoice: Invoice,
    private val creditNoteRepository: CreditNoteRepository = MockCreditNoteRepository(),
    private val createCreditNoteUseCase: CreateCreditNoteUseCase = CreateCreditNoteUseCase(),
    private val submitCreditNoteUseCase: SubmitCreditNoteUseCase = SubmitCreditNoteUseCase(creditNoteRepository),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val initialState = CreditNoteFormUiState(
        invoiceId = sourceInvoice.number,
        originalInvoiceDate = sourceInvoice.issueDate,
        issuerName = sourceInvoice.issuer.name,
        recipientName = sourceInvoice.recipient.name,
        lines = sourceInvoice.lines,
        vatBreakdown = negatedBreakdown(sourceInvoice),
        totalHt = Money(-sourceInvoice.totalHt.cents),
        totalVat = Money(-sourceInvoice.totalVat.cents),
        totalTtc = Money(-sourceInvoice.totalTtc.cents),
    )

    // L'état initial reflète aussi les erreurs de validation (formulaire vide = invalide) : sans
    // ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(revalidate(initialState))
    val uiState: StateFlow<CreditNoteFormUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            // Refus d'emblée si la facture porte déjà un avoir : inutile de laisser saisir un
            // motif pour échouer à la soumission.
            creditNoteRepository.findByInvoiceNumber(sourceInvoice.number).getOrNull()?.let { existing ->
                _uiState.update { it.copy(blockedByExistingCreditNote = existing.number) }
                return@launch
            }
            val number = creditNoteRepository.nextNumberForYear(exerciseYear()).getOrNull()
                ?: CreditNoteNumbering.format(exerciseYear(), 1)
            _uiState.update { revalidate(it.copy(creditNoteNumber = number)) }
        }
    }

    fun processIntent(intent: CreditNoteFormIntent) {
        if (intent is CreditNoteFormIntent.Submit) {
            submit()
            return
        }
        _uiState.update { current -> revalidate(applyChange(current, intent)) }
    }

    private fun applyChange(current: CreditNoteFormUiState, intent: CreditNoteFormIntent): CreditNoteFormUiState =
        when (intent) {
            // Le numéro est attribué par la séquence : l'intention subsiste pour la compatibilité
            // du contrat UDF, mais elle est sans effet — la continuité fiscale ne se saisit pas.
            is CreditNoteFormIntent.CreditNoteNumberChanged -> current

            is CreditNoteFormIntent.IssueDateChanged ->
                current.copy(
                    issueDate = intent.value,
                    touchedFields = current.touchedFields + CreditNoteFormField.ISSUE_DATE,
                )

            is CreditNoteFormIntent.ReasonChanged ->
                current.copy(
                    reason = intent.value,
                    touchedFields = current.touchedFields + CreditNoteFormField.REASON,
                )

            CreditNoteFormIntent.Submit -> current
        }

    private fun revalidate(state: CreditNoteFormUiState): CreditNoteFormUiState {
        val errors = mutableMapOf<CreditNoteFormField, String>()

        if (!CreditNoteNumbering.isValid(state.creditNoteNumber)) {
            errors[CreditNoteFormField.CREDIT_NOTE_NUMBER] = "Numéro d'avoir non attribué"
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
        val revalidated = revalidate(_uiState.value).copy(submitAttempted = true)
        if (revalidated.errors.isNotEmpty() || !revalidated.isFormEnabled) {
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
                submissionStatus = SubmissionStatus.Error(
                    throwable.message ?: "Erreur inconnue lors de la création de l'avoir",
                ),
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
                        // Une course sur l'unicité se rejoue ici : le dépôt reste l'autorité.
                        val alreadyCredited = throwable as? InvoiceAlreadyCreditedException
                        current.copy(
                            submissionStatus = SubmissionStatus.Error(
                                throwable.message ?: "Erreur inconnue lors de la soumission de l'avoir",
                            ),
                            blockedByExistingCreditNote = alreadyCredited?.creditNoteNumber,
                        )
                    },
                )
            }
        }
    }

    /**
     * Exercice de rattachement de l'avoir.
     *
     * commonMain n'a pas d'horloge : le projet a écarté `kotlinx-datetime` (décision v1) et les
     * dates transitent en chaînes ISO saisies par l'utilisateur. On retient donc l'exercice de la
     * facture annulée, ce qui reste juste dans le cas courant — un avoir émis dans l'année de la
     * facture. À revoir le jour où une horloge est introduite.
     */
    private fun exerciseYear(): Int =
        sourceInvoice.issueDate.take(4).toIntOrNull() ?: FALLBACK_YEAR

    fun onCleared() = scope.cancel()

    private companion object {
        const val FALLBACK_YEAR = 2026

        fun negatedBreakdown(invoice: Invoice): List<VatBreakdown> =
            computeVatBreakdown(invoice.lines).map {
                VatBreakdown(
                    rate = it.rate,
                    baseHt = Money(-it.baseHt.cents),
                    vatAmount = Money(-it.vatAmount.cents),
                )
            }
    }
}
