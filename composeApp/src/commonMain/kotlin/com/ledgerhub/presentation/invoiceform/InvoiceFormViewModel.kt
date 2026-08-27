package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.domain.invoice.totalHtOf
import com.ledgerhub.domain.invoice.totalTtcOf
import com.ledgerhub.domain.invoice.totalVatOf
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
 * ViewModel du formulaire de facture — PATTERN UDF/MVVM.
 * La validation reste synchrone (locale) ; seule la soumission via [SubmitInvoiceUseCase]
 * est asynchrone (délai réseau simulé par [MockInvoiceRepository] à ce stade).
 *
 * @param submitInvoiceUseCase injecté pour permettre les tests avec un mock configurable
 *   (succès/échec) — le défaut construit un [MockInvoiceRepository] tant qu'aucun backend
 *   réel (Ktor) n'est branché.
 * @param dispatcher injecté pour permettre les tests sans dépendance au thread réel.
 */
class InvoiceFormViewModel(
    private val submitInvoiceUseCase: SubmitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial doit lui aussi refléter les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(revalidate(InvoiceFormUiState()))
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    fun processIntent(intent: InvoiceFormIntent) {
        if (intent is InvoiceFormIntent.Submit) {
            submit()
            return
        }
        _uiState.update { current -> revalidate(applyChange(current, intent)) }
    }

    private fun applyChange(current: InvoiceFormUiState, intent: InvoiceFormIntent): InvoiceFormUiState =
        when (intent) {
            is InvoiceFormIntent.InvoiceNumberChanged -> current.copy(invoiceNumber = intent.value)
            is InvoiceFormIntent.IssueDateChanged -> current.copy(issueDate = intent.value)
            is InvoiceFormIntent.IssuerNameChanged -> current.copy(issuerName = intent.value)
            is InvoiceFormIntent.IssuerSirenChanged -> current.copy(issuerSiren = intent.value)
            is InvoiceFormIntent.IssuerSiretChanged -> current.copy(issuerSiret = intent.value)
            is InvoiceFormIntent.RecipientNameChanged -> current.copy(recipientName = intent.value)
            is InvoiceFormIntent.RecipientSirenChanged -> current.copy(recipientSiren = intent.value)
            is InvoiceFormIntent.RecipientSiretChanged -> current.copy(recipientSiret = intent.value)

            InvoiceFormIntent.AddLine ->
                current.copy(lines = current.lines + InvoiceLineFormState())

            is InvoiceFormIntent.RemoveLine ->
                if (current.canRemoveLines && intent.index in current.lines.indices) {
                    current.copy(lines = current.lines.filterIndexed { i, _ -> i != intent.index })
                } else {
                    current
                }

            is InvoiceFormIntent.UpdateLine ->
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

            InvoiceFormIntent.Submit -> current
        }

    private fun revalidate(state: InvoiceFormUiState): InvoiceFormUiState {
        val errors = mutableMapOf<InvoiceFormField, String>()

        if (state.invoiceNumber.isBlank()) {
            errors[InvoiceFormField.INVOICE_NUMBER] = "Le numéro de facture est requis"
        }
        if (!ISO_DATE_REGEX.matches(state.issueDate)) {
            errors[InvoiceFormField.ISSUE_DATE] = "Date attendue au format AAAA-MM-JJ"
        }
        if (state.issuerName.isBlank()) {
            errors[InvoiceFormField.ISSUER_NAME] = "Le nom de l'émetteur est requis"
        }
        (FiscalValidation.validateSiren(state.issuerSiren) as? ValidationResult.Invalid)?.let {
            errors[InvoiceFormField.ISSUER_SIREN] = it.reason
        }
        (FiscalValidation.validateSiret(state.issuerSiret) as? ValidationResult.Invalid)?.let {
            errors[InvoiceFormField.ISSUER_SIRET] = it.reason
        }
        if (state.recipientName.isBlank()) {
            errors[InvoiceFormField.RECIPIENT_NAME] = "Le nom du destinataire est requis"
        }
        (FiscalValidation.validateSiren(state.recipientSiren) as? ValidationResult.Invalid)?.let {
            errors[InvoiceFormField.RECIPIENT_SIREN] = it.reason
        }
        (FiscalValidation.validateSiret(state.recipientSiret) as? ValidationResult.Invalid)?.let {
            errors[InvoiceFormField.RECIPIENT_SIRET] = it.reason
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

    private fun validateLine(line: InvoiceLineFormState): InvoiceLineFormState {
        val lineErrors = mutableMapOf<InvoiceLineField, String>()

        if (line.label.isBlank()) {
            lineErrors[InvoiceLineField.LABEL] = "Le libellé est requis"
        }
        val quantity = line.quantity.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            lineErrors[InvoiceLineField.QUANTITY] = "La quantité doit être un entier positif"
        }
        val unitPriceCents = parseAmountToCents(line.unitPriceHt)
        if (unitPriceCents == null || unitPriceCents <= 0) {
            lineErrors[InvoiceLineField.UNIT_PRICE] = "Le prix unitaire HT doit être un montant positif"
        }

        return line.copy(errors = lineErrors)
    }

    /** Convertit une ligne de formulaire déjà validée (sans erreur) en ligne de domaine. Null sinon. */
    private fun InvoiceLineFormState.toDomainOrNull(): InvoiceLine? {
        if (errors.isNotEmpty()) return null
        val quantity = quantity.toIntOrNull() ?: return null
        val unitPriceCents = parseAmountToCents(unitPriceHt) ?: return null
        return InvoiceLine(label = label, quantity = quantity, unitPriceHt = Money(unitPriceCents), vatRate = vatRate)
    }

    private fun buildInvoice(state: InvoiceFormUiState): Invoice = Invoice(
        number = state.invoiceNumber,
        issueDate = state.issueDate,
        issuer = Party(state.issuerName, state.issuerSiren, state.issuerSiret),
        recipient = Party(state.recipientName, state.recipientSiren, state.recipientSiret),
        lines = state.lines.map { line ->
            InvoiceLine(
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

        val invoice = buildInvoice(revalidated)
        _uiState.value = revalidated.copy(submissionStatus = SubmissionStatus.Loading)

        scope.launch {
            val result = submitInvoiceUseCase(invoice)
            _uiState.update { current ->
                result.fold(
                    onSuccess = {
                        current.copy(submissionStatus = SubmissionStatus.Success, submittedInvoice = invoice)
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
