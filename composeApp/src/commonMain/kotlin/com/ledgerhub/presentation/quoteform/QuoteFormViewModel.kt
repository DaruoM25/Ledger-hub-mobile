package com.ledgerhub.presentation.quoteform

import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.QuoteStatus
import com.ledgerhub.domain.quote.SubmitQuoteUseCase
import com.ledgerhub.domain.quote.totalHtOf
import com.ledgerhub.domain.quote.totalTtcOf
import com.ledgerhub.domain.quote.totalVatOf
import com.ledgerhub.presentation.components.QuickClientDraft
import com.ledgerhub.presentation.components.QuickClientField
import com.ledgerhub.presentation.components.validateQuickClient
import com.ledgerhub.presentation.invoiceform.CabinetIdentity
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
private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

private fun formatUnitPrice(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString()
    else "${cents / 100L}.${(cents % 100L).toString().padStart(2, '0')}"

/**
 * ViewModel du formulaire de devis — PATTERN UDF/MVVM, symétrique à InvoiceFormViewModel.
 * La validation reste synchrone (locale) ; seule la soumission via [SubmitQuoteUseCase]
 * est asynchrone.
 */
class QuoteFormViewModel(
    private val submitQuoteUseCase: SubmitQuoteUseCase = SubmitQuoteUseCase(MockQuoteRepository()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Annuaire des fiches clients alimentant le sélecteur (US-11).
     */
    private val clientRepository: ClientRepository? = null,
    initialQuote: Quote? = null,
    issuer: Party = CabinetIdentity.party,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial reflète aussi les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(
        revalidate(
            if (initialQuote != null) {
                QuoteFormUiState(
                    quoteNumber = initialQuote.number,
                    issueDate = initialQuote.issueDate,
                    validityDate = initialQuote.validityDate,
                    recipientName = initialQuote.recipient.name,
                    recipientSiren = initialQuote.recipient.siren,
                    recipientSiret = initialQuote.recipient.siret,
                    recipientEmail = initialQuote.recipient.email,
                    issuer = initialQuote.issuer.takeIf { it.name.isNotBlank() } ?: issuer,
                    clientQuery = initialQuote.recipient.name,
                    lines = initialQuote.lines.map {
                        QuoteLineFormState(
                            label = it.label,
                            quantity = it.quantity.toString(),
                            unitPriceHt = formatUnitPrice(it.unitPriceHt.cents),
                            vatRate = it.vatRate,
                        )
                    }.ifEmpty { listOf(QuoteLineFormState()) },
                    isClientDirectoryAvailable = clientRepository != null,
                )
            } else {
                QuoteFormUiState(
                    issuer = issuer,
                    isClientDirectoryAvailable = clientRepository != null,
                )
            }
        ),
    )
    val uiState: StateFlow<QuoteFormUiState> = _uiState.asStateFlow()

    init {
        // Le sélecteur doit proposer les fiches connues dès l'ouverture, avant toute frappe.
        refreshSuggestions(query = "")
    }

    fun processIntent(intent: QuoteFormIntent) {
        when (intent) {
            QuoteFormIntent.SaveDraft -> submit(QuoteStatus.DRAFT)
            QuoteFormIntent.FinalizeQuote, QuoteFormIntent.Submit -> submit(QuoteStatus.SENT)

            is QuoteFormIntent.RecipientNameChanged -> onClientQueryChanged(intent.value)
            is QuoteFormIntent.OnClientQueryChanged -> onClientQueryChanged(intent.value)

            is QuoteFormIntent.OnClientSelected -> onClientSelected(intent.client)
            QuoteFormIntent.OnOpenQuickClientDialog -> openQuickClientDialog()
            QuoteFormIntent.OnDismissQuickClientDialog ->
                _uiState.update { it.copy(showQuickClientDialog = false, quickClientDraft = null) }

            is QuoteFormIntent.OnQuickClientFieldChanged -> _uiState.update { current ->
                val draft = current.quickClientDraft ?: return@update current
                current.copy(
                    quickClientDraft = draft.copy(
                        name = intent.name,
                        siret = intent.siret,
                        email = intent.email,
                        errors = draft.errors.filterKeys { field ->
                            when (field) {
                                QuickClientField.NAME -> intent.name == draft.name
                                QuickClientField.SIRET -> intent.siret == draft.siret
                                QuickClientField.EMAIL -> intent.email == draft.email
                            }
                        },
                    ),
                )
            }

            is QuoteFormIntent.OnSaveQuickClient ->
                saveQuickClient(intent.name, intent.siret, intent.email)

            else -> _uiState.update { current -> revalidate(applyChange(current, intent)) }
        }
    }

    // ── Sélecteur client (US-11) — symétrique à InvoiceFormViewModel ─────────

    private fun refreshSuggestions(query: String) {
        val repository = clientRepository ?: return
        scope.launch {
            val matches = repository.searchClients(query).getOrDefault(emptyList())
            _uiState.update { current ->
                if (current.clientQuery != query) current
                else current.copy(clientSuggestions = matches)
            }
        }
    }

    private fun onClientQueryChanged(value: String) {
        _uiState.update { current ->
            revalidate(
                current.copy(
                    clientQuery = value,
                    recipientName = value,
                    touchedFields = current.touchedFields + QuoteFormField.RECIPIENT_NAME,
                    selectedClient = current.selectedClient?.takeIf { it.name == value },
                    isClientDropdownExpanded = true,
                ),
            )
        }
        refreshSuggestions(value)
    }

    private fun onClientSelected(client: Party) {
        _uiState.update { current ->
            revalidate(
                current.copy(
                    selectedClient = client,
                    clientQuery = client.name,
                    recipientName = client.name,
                    recipientSiren = client.siren,
                    recipientSiret = client.siret,
                    recipientEmail = client.email,
                    touchedFields = current.touchedFields + setOf(
                        QuoteFormField.RECIPIENT_NAME,
                        QuoteFormField.RECIPIENT_SIRET,
                        QuoteFormField.RECIPIENT_EMAIL,
                    ),
                    isClientDropdownExpanded = false,
                    clientSuggestions = emptyList(),
                ),
            )
        }
    }

    private fun openQuickClientDialog() {
        _uiState.update { current ->
            current.copy(
                showQuickClientDialog = true,
                isClientDropdownExpanded = false,
                quickClientDraft = QuickClientDraft(name = current.clientQuery.trim()),
            )
        }
    }

    private fun saveQuickClient(name: String, siret: String, email: String) {
        val repository = clientRepository ?: return
        val draft = _uiState.value.quickClientDraft ?: return
        if (draft.isSaving) return

        val errors = validateQuickClient(name, siret, email)
        val submitted = draft.copy(name = name, siret = siret, email = email)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(quickClientDraft = submitted.copy(errors = errors)) }
            return
        }

        _uiState.update { it.copy(quickClientDraft = submitted.copy(errors = emptyMap(), isSaving = true)) }

        val client = Party(
            name = name.trim(),
            siren = siret.take(9),
            siret = siret,
            email = email.trim(),
        )
        scope.launch {
            repository.createClient(client).fold(
                onSuccess = {
                    _uiState.update { it.copy(showQuickClientDialog = false, quickClientDraft = null) }
                    onClientSelected(client)
                },
                onFailure = { throwable ->
                    val message = (throwable as? DuplicateClientException)
                        ?.let { "Un client porte déjà ce SIRET" }
                        ?: throwable.message
                        ?: "Enregistrement du client impossible"
                    _uiState.update {
                        it.copy(
                            quickClientDraft = submitted.copy(
                                isSaving = false,
                                errors = mapOf(QuickClientField.SIRET to message),
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun applyChange(current: QuoteFormUiState, intent: QuoteFormIntent): QuoteFormUiState =
        when (intent) {
            is QuoteFormIntent.QuoteNumberChanged ->
                current.copy(
                    quoteNumber = intent.value,
                    touchedFields = current.touchedFields + QuoteFormField.QUOTE_NUMBER,
                )

            is QuoteFormIntent.IssueDateChanged ->
                current.copy(
                    issueDate = intent.value,
                    touchedFields = current.touchedFields + QuoteFormField.ISSUE_DATE,
                )

            is QuoteFormIntent.ValidityDateChanged ->
                current.copy(
                    validityDate = intent.value,
                    touchedFields = current.touchedFields + QuoteFormField.VALIDITY_DATE,
                )

            is QuoteFormIntent.IssuerNameChanged ->
                current.copy(
                    issuer = current.issuer.copy(name = intent.value),
                    touchedFields = current.touchedFields + QuoteFormField.ISSUER_NAME,
                )

            is QuoteFormIntent.IssuerSirenChanged ->
                current.copy(
                    issuer = current.issuer.copy(siren = intent.value),
                    touchedFields = current.touchedFields + QuoteFormField.ISSUER_SIREN,
                )

            is QuoteFormIntent.IssuerSiretChanged ->
                current.copy(
                    issuer = current.issuer.copy(siret = intent.value),
                    touchedFields = current.touchedFields + QuoteFormField.ISSUER_SIRET,
                )

            is QuoteFormIntent.RecipientSirenChanged ->
                current.copy(
                    recipientSiren = intent.value,
                    touchedFields = current.touchedFields + QuoteFormField.RECIPIENT_SIREN,
                    selectedClient = current.selectedClient?.takeIf { it.siren == intent.value },
                )

            is QuoteFormIntent.RecipientSiretChanged ->
                current.copy(
                    recipientSiret = intent.value,
                    recipientSiren = if (intent.value.length >= 9) intent.value.take(9) else current.recipientSiren,
                    touchedFields = current.touchedFields + QuoteFormField.RECIPIENT_SIRET,
                    selectedClient = current.selectedClient?.takeIf { it.siret == intent.value },
                )

            is QuoteFormIntent.RecipientEmailChanged ->
                current.copy(
                    recipientEmail = intent.value,
                    touchedFields = current.touchedFields + QuoteFormField.RECIPIENT_EMAIL,
                    selectedClient = current.selectedClient?.takeIf { it.email == intent.value },
                )

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
                                val touched = line.touched.toMutableSet()
                                if (line.label != intent.label) touched += QuoteLineField.LABEL
                                if (line.quantity != intent.quantity) touched += QuoteLineField.QUANTITY
                                if (line.unitPriceHt != intent.unitPriceHt) touched += QuoteLineField.UNIT_PRICE
                                line.copy(
                                    label = intent.label,
                                    quantity = intent.quantity,
                                    unitPriceHt = intent.unitPriceHt,
                                    vatRate = intent.vatRate,
                                    touched = touched,
                                )
                            } else {
                                line
                            }
                        },
                    )
                }

            QuoteFormIntent.SaveDraft,
            QuoteFormIntent.FinalizeQuote,
            QuoteFormIntent.Submit,
            is QuoteFormIntent.RecipientNameChanged,
            is QuoteFormIntent.OnClientQueryChanged,
            is QuoteFormIntent.OnClientSelected,
            QuoteFormIntent.OnOpenQuickClientDialog,
            QuoteFormIntent.OnDismissQuickClientDialog,
            is QuoteFormIntent.OnQuickClientFieldChanged,
            is QuoteFormIntent.OnSaveQuickClient,
            -> current
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
        (FiscalValidation.validateSiret(state.recipientSiret) as? ValidationResult.Invalid)?.let {
            errors[QuoteFormField.RECIPIENT_SIRET] = it.reason
        }
        if (state.recipientEmail.isNotBlank() && !EMAIL_REGEX.matches(state.recipientEmail)) {
            errors[QuoteFormField.RECIPIENT_EMAIL] = "Format d'adresse e-mail invalide"
        }

        val validatedLines = state.lines.map(::validateLine)
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

    private fun QuoteLineFormState.toDomainOrNull(): QuoteLine? {
        if (errors.isNotEmpty()) return null
        val quantity = quantity.toIntOrNull() ?: return null
        val unitPriceCents = parseAmountToCents(unitPriceHt) ?: return null
        return QuoteLine(label = label, quantity = quantity, unitPriceHt = Money(unitPriceCents), vatRate = vatRate)
    }

    private fun buildQuote(state: QuoteFormUiState, status: QuoteStatus): Quote = Quote(
        number = state.quoteNumber,
        issueDate = state.issueDate,
        validityDate = state.validityDate,
        issuer = state.issuer,
        recipient = Party(
            name = state.recipientName,
            siren = if (state.recipientSiret.length >= 9) state.recipientSiret.take(9) else state.recipientSiren,
            siret = state.recipientSiret,
            email = state.recipientEmail,
        ),
        lines = state.lines.map { line ->
            QuoteLine(
                label = line.label,
                quantity = line.quantity.toInt(),
                unitPriceHt = Money(parseAmountToCents(line.unitPriceHt)!!),
                vatRate = line.vatRate,
            )
        },
        status = status,
    )

    private fun submit(targetStatus: QuoteStatus) {
        if (_uiState.value.isSubmitting) return

        val revalidated = revalidate(_uiState.value).copy(submitAttempted = true)
        val hasLineErrors = revalidated.lines.any { it.errors.isNotEmpty() }
        if (revalidated.errors.isNotEmpty() || hasLineErrors) {
            _uiState.value = revalidated
            return
        }

        val quote = buildQuote(revalidated, targetStatus)
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

    fun onCleared() = scope.cancel()
}

