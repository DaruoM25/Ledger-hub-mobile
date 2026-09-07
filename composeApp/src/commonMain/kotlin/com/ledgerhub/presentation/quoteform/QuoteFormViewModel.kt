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
import com.ledgerhub.domain.quote.SubmitQuoteUseCase
import com.ledgerhub.domain.quote.totalHtOf
import com.ledgerhub.domain.quote.totalTtcOf
import com.ledgerhub.domain.quote.totalVatOf
import com.ledgerhub.presentation.components.QuickClientDraft
import com.ledgerhub.presentation.components.QuickClientField
import com.ledgerhub.presentation.components.validateQuickClient
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

private fun formatUnitPrice(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString()
    else "${cents / 100L}.${(cents % 100L).toString().padStart(2, '0')}"

/**
 * ViewModel du formulaire de devis — PATTERN UDF/MVVM, symétrique à InvoiceFormViewModel.
 * La validation reste synchrone (locale) ; seule la soumission via [SubmitQuoteUseCase]
 * est asynchrone (délai réseau simulé par [MockQuoteRepository] à ce stade).
 *
 * @param submitQuoteUseCase injecté pour permettre les tests avec un mock configurable
 *   (succès/échec) — le défaut construit un [MockQuoteRepository] tant qu'aucun backend
 *   réel (Ktor) n'est branché.
 * @param dispatcher injecté pour permettre les tests sans dépendance au thread réel.
 * @param initialQuote devis existant à charger dans le formulaire (mode modification).
 */
class QuoteFormViewModel(
    private val submitQuoteUseCase: SubmitQuoteUseCase = SubmitQuoteUseCase(MockQuoteRepository()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /**
     * Annuaire des fiches clients alimentant le sélecteur (US-11). `null` = pas d'annuaire
     * branché : le champ raison sociale du destinataire se comporte alors comme un champ libre,
     * sans suggestion ni création rapide.
     */
    private val clientRepository: ClientRepository? = null,
    initialQuote: Quote? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial doit lui aussi refléter les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(
        revalidate(
            if (initialQuote != null) {
                QuoteFormUiState(
                    quoteNumber = initialQuote.number,
                    issueDate = initialQuote.issueDate,
                    validityDate = initialQuote.validityDate,
                    issuerName = initialQuote.issuer.name,
                    issuerSiren = initialQuote.issuer.siren,
                    issuerSiret = initialQuote.issuer.siret,
                    recipientName = initialQuote.recipient.name,
                    recipientSiren = initialQuote.recipient.siren,
                    recipientSiret = initialQuote.recipient.siret,
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
                QuoteFormUiState(isClientDirectoryAvailable = clientRepository != null)
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
            QuoteFormIntent.Submit -> submit()

            // Le champ raison sociale est un sélecteur depuis US-11 : les deux intentions
            // décrivent le même geste.
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
                        // La frappe efface l'erreur du champ corrigé, pas celles des autres.
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
                // La saisie a pu changer pendant la lecture : on n'écrase pas un état plus récent.
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
                    // Toute frappe manuelle rompt le lien avec la fiche retenue.
                    selectedClient = current.selectedClient?.takeIf { it.name == value },
                    isClientDropdownExpanded = true,
                ),
            )
        }
        refreshSuggestions(value)
    }

    /**
     * Reprend la fiche dans le devis. Le devis porte SIREN **et** SIRET du destinataire : les
     * deux viennent de la fiche, sans dériver le SIREN du SIRET.
     */
    private fun onClientSelected(client: Party) {
        _uiState.update { current ->
            revalidate(
                current.copy(
                    selectedClient = client,
                    clientQuery = client.name,
                    recipientName = client.name,
                    recipientSiren = client.siren,
                    recipientSiret = client.siret,
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
                // Le nom déjà tapé est repris : l'utilisateur ne le ressaisit pas.
                quickClientDraft = QuickClientDraft(name = current.clientQuery.trim()),
            )
        }
    }

    private fun saveQuickClient(name: String, siret: String, email: String) {
        val repository = clientRepository ?: return
        val draft = _uiState.value.quickClientDraft ?: return
        if (draft.isSaving) return

        // Règles partagées avec le formulaire de facture — source unique (voir ClientPicker.kt).
        val errors = validateQuickClient(name, siret, email)
        val submitted = draft.copy(name = name, siret = siret, email = email)
        if (errors.isNotEmpty()) {
            // La modale reste ouverte : c'est là que l'erreur se corrige.
            _uiState.update { it.copy(quickClientDraft = submitted.copy(errors = errors)) }
            return
        }

        _uiState.update { it.copy(quickClientDraft = submitted.copy(errors = emptyMap(), isSaving = true)) }

        val client = Party(
            name = name.trim(),
            // Règle INSEE : le SIREN est le préfixe à 9 chiffres du SIRET, déjà validé.
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
            is QuoteFormIntent.QuoteNumberChanged -> current.copy(quoteNumber = intent.value)
            is QuoteFormIntent.IssueDateChanged -> current.copy(issueDate = intent.value)
            is QuoteFormIntent.ValidityDateChanged -> current.copy(validityDate = intent.value)
            is QuoteFormIntent.IssuerNameChanged -> current.copy(issuerName = intent.value)
            is QuoteFormIntent.IssuerSirenChanged -> current.copy(issuerSiren = intent.value)
            is QuoteFormIntent.IssuerSiretChanged -> current.copy(issuerSiret = intent.value)
            // SIREN et SIRET restent modifiables après une sélection ; les retoucher à la main
            // rompt le lien avec la fiche (l'état cesse d'affirmer qu'un client est sélectionné).
            is QuoteFormIntent.RecipientSirenChanged ->
                current.copy(
                    recipientSiren = intent.value,
                    selectedClient = current.selectedClient?.takeIf { it.siren == intent.value },
                )

            is QuoteFormIntent.RecipientSiretChanged ->
                current.copy(
                    recipientSiret = intent.value,
                    selectedClient = current.selectedClient?.takeIf { it.siret == intent.value },
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

            // Soumission et gestes du sélecteur client : interceptés en amont par processIntent,
            // ils n'atteignent jamais cette branche.
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
