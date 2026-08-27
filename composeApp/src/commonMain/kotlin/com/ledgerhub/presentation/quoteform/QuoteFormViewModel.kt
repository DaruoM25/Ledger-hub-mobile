package com.ledgerhub.presentation.quoteform

import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.SubmitQuoteUseCase
import com.ledgerhub.domain.quote.totalHtOf
import com.ledgerhub.domain.quote.totalTtcOf
import com.ledgerhub.domain.quote.totalVatOf
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
 * ViewModel du formulaire de devis — PATTERN UDF/MVVM, symétrique à InvoiceFormViewModel.
 * La validation reste synchrone (locale) ; seule la soumission via [SubmitQuoteUseCase]
 * est asynchrone (délai réseau simulé par [MockQuoteRepository] à ce stade).
 *
 * @param submitQuoteUseCase injecté pour permettre les tests avec un mock configurable
 *   (succès/échec) — le défaut construit un [MockQuoteRepository] tant qu'aucun backend
 *   réel (Ktor) n'est branché.
 * @param dispatcher injecté pour permettre les tests sans dépendance au thread réel.
 */
class QuoteFormViewModel(
    private val submitQuoteUseCase: SubmitQuoteUseCase = SubmitQuoteUseCase(MockQuoteRepository()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial doit lui aussi refléter les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(revalidate(QuoteFormUiState()))
    val uiState: StateFlow<QuoteFormUiState> = _uiState.asStateFlow()

    fun processIntent(intent: QuoteFormIntent) {
        if (intent is QuoteFormIntent.Submit) {
            submit()
            return
        }
        _uiState.update { current -> revalidate(applyChange(current, intent)) }
    }

    private fun applyChange(current: QuoteFormUiState, intent: QuoteFormIntent): QuoteFormUiState =
        when (intent) {
            is QuoteFormIntent.QuoteNumberChanged -> current.copy(quoteNumber = intent.value)
            is QuoteFormIntent.IssueDateChanged -> current.copy(issueDate = intent.value)
            is QuoteFormIntent.ValidityDateChanged -> current.copy(validityDate = intent.value)
            is QuoteFormIntent.IssuerNameChanged -> current.copy(issuerName = intent.value)
            is QuoteFormIntent.IssuerSirenChanged -> current.copy(issuerSiren = intent.value)
            is QuoteFormIntent.IssuerSiretChanged -> current.copy(issuerSiret = intent.value)
            is QuoteFormIntent.RecipientNameChanged -> current.copy(recipientName = intent.value)
            is QuoteFormIntent.RecipientSirenChanged -> current.copy(recipientSiren = intent.value)
            is QuoteFormIntent.RecipientSiretChanged -> current.copy(recipientSiret = intent.value)

            QuoteFormIntent.AddLine ->
                current.copy(lines = current.lines + QuoteLineFormState())

            is QuoteFormIntent.RemoveLine ->
                if (current.canRemoveLines && intent.index in current.lines.indices) {
                    current.copy(lines = current.lines.filterIndexed { i, _ -> i != intent.index })
                } else {
                    current
                }

            is QuoteFormIntent.UpdateLine ->
                if (!current.isFormEnabled || intent.index !in current.lines.indices) {
                    current
                } else {
                    current.copy(
                        lines = current.lines.mapIndexed { i, line ->
                            if (i == intent.index) {
                                line.copy(
                                    label = intent.label,
                                    quantity = intent.quantity,
                                    unitPriceHt = intent.unitPriceHt,
                                    vatRate = intent.vatRate,
                                )
                            } else {
                                line
                            }
                        },
                    )
                }

            QuoteFormIntent.Submit -> current
        }

    private fun revalidate(state: QuoteFormUiState): QuoteFormUiState {
        val errors = mutableMapOf<QuoteFormField, String>()

        if (state.quoteNumber.isBlank()) {
            errors[QuoteFormField.QUOTE_NUMBER] = "Le numéro de devis est requis"
        }
        if (!ISO_DATE_REGEX.matches(state.issueDate)) {
            errors[QuoteFormField.ISSUE_DATE] = "Date attendue au format AAAA-MM-JJ"
        }
        if (!ISO_DATE_REGEX.matches(state.validityDate)) {
            errors[QuoteFormField.VALIDITY_DATE] = "Date attendue au format AAAA-MM-JJ"
        }
        if (state.issuerName.isBlank()) {
            errors[QuoteFormField.ISSUER_NAME] = "Le nom de l'émetteur est requis"
        }
        (FiscalValidation.validateSiren(state.issuerSiren) as? ValidationResult.Invalid)?.let {
            errors[QuoteFormField.ISSUER_SIREN] = it.reason
        }
        (FiscalValidation.validateSiret(state.issuerSiret) as? ValidationResult.Invalid)?.let {
            errors[QuoteFormField.ISSUER_SIRET] = it.reason
        }
        if (state.recipientName.isBlank()) {
            errors[QuoteFormField.RECIPIENT_NAME] = "Le nom du destinataire est requis"
        }
        (FiscalValidation.validateSiren(state.recipientSiren) as? ValidationResult.Invalid)?.let {
            errors[QuoteFormField.RECIPIENT_SIREN] = it.reason
        }
        (FiscalValidation.validateSiret(state.recipientSiret) as? ValidationResult.Invalid)?.let {
            errors[QuoteFormField.RECIPIENT_SIRET] = it.reason
        }

        val validatedLines = state.lines.map(::validateLine)

        // Seules les lignes sans erreur contribuent aux totaux affichés — une ligne invalide
        // ne doit ni fausser le total ni empêcher l'affichage de celui des lignes correctes,
        // mais bloque tout de même la soumission globale (voir isSubmitEnabled).
        val validDomainLines = validatedLines.mapNotNull { line -> if (line.errors.isEmpty()) line.toDomainOrNull() else null }

        return state.copy(
            errors = errors,
            lines = validatedLines,
            totalHt = totalHtOf(validDomainLines),
            totalVat = totalVatOf(validDomainLines),
            totalTtc = totalTtcOf(validDomainLines),
        )
    }

    private fun validateLine(line: QuoteLineFormState): QuoteLineFormState {
        val lineErrors = mutableMapOf<QuoteLineField, String>()

        if (line.label.isBlank()) {
            lineErrors[QuoteLineField.LABEL] = "Le libellé est requis"
        }
        val quantity = line.quantity.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            lineErrors[QuoteLineField.QUANTITY] = "La quantité doit être un entier positif"
        }
        val unitPriceCents = parseAmountToCents(line.unitPriceHt)
        if (unitPriceCents == null || unitPriceCents <= 0) {
            lineErrors[QuoteLineField.UNIT_PRICE] = "Le prix unitaire HT doit être un montant positif"
        }

        return line.copy(errors = lineErrors)
    }

    /** Convertit une ligne de formulaire déjà validée (sans erreur) en ligne de domaine. Null sinon. */
    private fun QuoteLineFormState.toDomainOrNull(): QuoteLine? {
        if (errors.isNotEmpty()) return null
        val quantity = quantity.toIntOrNull() ?: return null
        val unitPriceCents = parseAmountToCents(unitPriceHt) ?: return null
        return QuoteLine(label = label, quantity = quantity, unitPriceHt = Money(unitPriceCents), vatRate = vatRate)
    }

    private fun buildQuote(state: QuoteFormUiState): Quote = Quote(
        number = state.quoteNumber,
        issueDate = state.issueDate,
        validityDate = state.validityDate,
        issuer = Party(state.issuerName, state.issuerSiren, state.issuerSiret),
        recipient = Party(state.recipientName, state.recipientSiren, state.recipientSiret),
        lines = state.lines.map { line ->
            QuoteLine(
                label = line.label,
                quantity = line.quantity.toInt(),
                unitPriceHt = Money(parseAmountToCents(line.unitPriceHt)!!),
                vatRate = line.vatRate,
            )
        },
    )

    private fun submit() {
        val revalidated = revalidate(_uiState.value)
        val hasLineErrors = revalidated.lines.any { it.errors.isNotEmpty() }
        if (revalidated.errors.isNotEmpty() || hasLineErrors) {
            _uiState.value = revalidated
            return
        }

        val quote = buildQuote(revalidated)
        _uiState.value = revalidated.copy(submissionStatus = SubmissionStatus.Loading)

        scope.launch {
            val result = submitQuoteUseCase(quote)
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(submissionStatus = SubmissionStatus.Success, submittedQuote = quote)
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
