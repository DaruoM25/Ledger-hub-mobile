package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.directory.LuhnChecksum
import com.ledgerhub.domain.i18n.ValidationErrorKey
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.VatRate
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
private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

/**
 * ViewModel du formulaire de facture — PATTERN UDF/MVVM.
 * La validation reste synchrone (locale) ; seule la soumission via [SubmitInvoiceUseCase]
 * est asynchrone. Le calcul des totaux réutilise **exclusivement** le moteur domaine
 * ([totalHtOf] / [totalVatOf] / [totalTtcOf] → `computeVatBreakdown`, arithmétique Long au centime).
 *
 * @param submitInvoiceUseCase injecté pour les tests (succès/échec configurables).
 * @param dispatcher injecté pour les tests sans dépendance au thread réel.
 */
class InvoiceFormViewModel(
    private val submitInvoiceUseCase: SubmitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository()),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Émetteur issu des paramètres fiscaux enregistrés ; [CabinetIdentity] sert de repli. */
    issuer: Party = CabinetIdentity.party,
    /** Taux pré-sélectionné sur toute nouvelle ligne — configurable aux paramètres fiscaux. */
    private val defaultVatRate: VatRate = VatRate.TAUX_NORMAL,
    /**
     * Annuaire des fiches clients alimentant le sélecteur (US-11). `null` = pas d'annuaire
     * branché : le champ raison sociale se comporte alors comme un champ libre, sans suggestion
     * ni création rapide (utile aux tests qui n'ont que faire du sélecteur).
     */
    private val clientRepository: ClientRepository? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial reflète aussi les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(
        revalidate(
            InvoiceFormUiState(
                issuer = issuer,
                lines = listOf(InvoiceLineFormState(vatRate = defaultVatRate)),
                isClientDirectoryAvailable = clientRepository != null,
            ),
        ),
    )
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    init {
        // Le sélecteur doit proposer les fiches connues dès l'ouverture, avant toute frappe.
        refreshSuggestions(query = "")
    }

    fun processIntent(intent: InvoiceFormIntent) {
        when (intent) {
            InvoiceFormIntent.SaveDraft -> submit(InvoiceStatus.DRAFT)
            InvoiceFormIntent.ValidateAndIssue -> submit(InvoiceStatus.DEPOSITED)

            // Le champ raison sociale est un sélecteur depuis US-11 : les deux intentions
            // décrivent le même geste.
            is InvoiceFormIntent.ClientNameChanged -> onClientQueryChanged(intent.value)
            is InvoiceFormIntent.OnClientQueryChanged -> onClientQueryChanged(intent.value)

            is InvoiceFormIntent.OnClientSelected -> onClientSelected(intent.client)
            InvoiceFormIntent.OnOpenQuickClientDialog -> openQuickClientDialog()
            InvoiceFormIntent.OnDismissQuickClientDialog ->
                _uiState.update { it.copy(showQuickClientDialog = false, quickClientDraft = null) }

            is InvoiceFormIntent.OnQuickClientFieldChanged -> _uiState.update { current ->
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

            is InvoiceFormIntent.OnSaveQuickClient ->
                saveQuickClient(intent.name, intent.siret, intent.email)

            else -> _uiState.update { current -> revalidate(applyChange(current, intent)) }
        }
    }

    // ── Sélecteur client (US-11) ─────────────────────────────────────────────

    /**
     * Recharge les suggestions pour [query]. Chaque frappe relance une lecture : le volume d'un
     * annuaire client tient largement en mémoire, et l'écriture reste la seule opération dont la
     * latence se voit à l'écran.
     */
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
                    clientName = value,
                    // Toute frappe manuelle rompt le lien avec la fiche retenue : l'état ne doit
                    // pas prétendre qu'un client est sélectionné alors que son nom a été modifié.
                    selectedClient = current.selectedClient?.takeIf { it.name == value },
                    isClientDropdownExpanded = true,
                ).touch(InvoiceFormField.CLIENT_NAME),
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
                    clientName = client.name,
                    clientSiret = client.siret,
                    clientEmail = client.email,
                    isClientDropdownExpanded = false,
                    clientSuggestions = emptyList(),
                )
                    .touch(InvoiceFormField.CLIENT_NAME)
                    .touch(InvoiceFormField.CLIENT_SIRET)
                    .touch(InvoiceFormField.CLIENT_EMAIL),
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

    /**
     * Valide puis persiste la fiche. Le SIRET est contrôlé par [LuhnChecksum.isValidSiret]
     * (14 chiffres **et** clé de Luhn), pas seulement par la longueur : une saisie rapide est
     * précisément le moment où une coquille passe inaperçue.
     */
    private fun saveQuickClient(name: String, siret: String, email: String) {
        val repository = clientRepository ?: return
        val current = _uiState.value
        val draft = current.quickClientDraft ?: return
        if (draft.isSaving) return

        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val errors = mutableMapOf<QuickClientField, String>()
        if (FiscalValidation.validateCompanyName(trimmedName) is ValidationResult.Invalid) {
            errors[QuickClientField.NAME] = "La raison sociale est obligatoire"
        }
        if (!LuhnChecksum.isValidSiret(siret)) {
            errors[QuickClientField.SIRET] = "SIRET invalide : 14 chiffres et clé de Luhn correcte"
        }
        if (!EMAIL_REGEX.matches(trimmedEmail)) {
            errors[QuickClientField.EMAIL] = "Adresse email invalide"
        }

        val submitted = draft.copy(name = name, siret = siret, email = email)
        if (errors.isNotEmpty()) {
            // La modale reste ouverte : c'est là que l'erreur se corrige.
            _uiState.update { it.copy(quickClientDraft = submitted.copy(errors = errors)) }
            return
        }

        _uiState.update { it.copy(quickClientDraft = submitted.copy(errors = emptyMap(), isSaving = true)) }

        val client = Party(
            name = trimmedName,
            // Règle INSEE : le SIREN est le préfixe à 9 chiffres du SIRET, déjà validé.
            siren = siret.take(9),
            siret = siret,
            email = trimmedEmail,
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

    private fun applyChange(current: InvoiceFormUiState, intent: InvoiceFormIntent): InvoiceFormUiState =
        when (intent) {
            is InvoiceFormIntent.InvoiceNumberChanged ->
                current.copy(invoiceNumber = intent.value).touch(InvoiceFormField.INVOICE_NUMBER)

            is InvoiceFormIntent.IssueDateChanged ->
                current.copy(issueDate = intent.value).touch(InvoiceFormField.ISSUE_DATE)

            is InvoiceFormIntent.DueDateChanged ->
                current.copy(dueDate = intent.value).touch(InvoiceFormField.DUE_DATE)

            // SIRET et email restent modifiables après une sélection ; les retoucher à la main
            // rompt le lien avec la fiche (l'état cesse d'affirmer qu'un client est sélectionné).
            is InvoiceFormIntent.ClientSiretChanged ->
                current.copy(
                    clientSiret = intent.value,
                    selectedClient = current.selectedClient?.takeIf { it.siret == intent.value },
                ).touch(InvoiceFormField.CLIENT_SIRET)

            is InvoiceFormIntent.ClientEmailChanged ->
                current.copy(
                    clientEmail = intent.value,
                    selectedClient = current.selectedClient?.takeIf { it.email == intent.value },
                ).touch(InvoiceFormField.CLIENT_EMAIL)

            is InvoiceFormIntent.ToggleFacturX -> current.copy(generateFacturX = intent.enabled)

            InvoiceFormIntent.AddLine ->
                current.copy(lines = current.lines + InvoiceLineFormState(vatRate = defaultVatRate))

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
                                    // L'intention porte les trois champs à chaque frappe : seuls
                                    // ceux dont la valeur change comptent comme réellement saisis.
                                    touched = line.touched +
                                        listOfNotNull(
                                            InvoiceLineField.LABEL.takeIf { intent.label != line.label },
                                            InvoiceLineField.QUANTITY.takeIf { intent.quantity != line.quantity },
                                            InvoiceLineField.UNIT_PRICE.takeIf { intent.unitPriceHt != line.unitPriceHt },
                                        ),
                                )
                            } else {
                                line
                            }
                        },
                    )
                }

            // Écritures et gestes du sélecteur client : interceptés en amont par processIntent,
            // ils n'atteignent jamais cette branche.
            InvoiceFormIntent.SaveDraft,
            InvoiceFormIntent.ValidateAndIssue,
            is InvoiceFormIntent.ClientNameChanged,
            is InvoiceFormIntent.OnClientQueryChanged,
            is InvoiceFormIntent.OnClientSelected,
            InvoiceFormIntent.OnOpenQuickClientDialog,
            InvoiceFormIntent.OnDismissQuickClientDialog,
            is InvoiceFormIntent.OnQuickClientFieldChanged,
            is InvoiceFormIntent.OnSaveQuickClient,
            -> current
        }

    /** Marque [field] comme saisi — ses erreurs deviennent affichables (voir [InvoiceFormUiState.visibleErrors]). */
    private fun InvoiceFormUiState.touch(field: InvoiceFormField): InvoiceFormUiState =
        copy(touchedFields = touchedFields + field)

    private fun revalidate(state: InvoiceFormUiState): InvoiceFormUiState {
        val errors = mutableMapOf<InvoiceFormField, ValidationErrorKey>()

        if (state.invoiceNumber.isBlank()) {
            errors[InvoiceFormField.INVOICE_NUMBER] = ValidationErrorKey.INVOICE_NUMBER_REQUIRED
        }
        if (!ISO_DATE_REGEX.matches(state.issueDate)) {
            errors[InvoiceFormField.ISSUE_DATE] = ValidationErrorKey.DATE_FORMAT_INVALID
        }
        if (!ISO_DATE_REGEX.matches(state.dueDate)) {
            errors[InvoiceFormField.DUE_DATE] = ValidationErrorKey.DATE_FORMAT_INVALID
        }
        if (state.clientName.isBlank()) {
            errors[InvoiceFormField.CLIENT_NAME] = ValidationErrorKey.CLIENT_NAME_REQUIRED
        }
        // Exigence explicite : SIRET valide = exactement 14 chiffres (voir FiscalValidation).
        if (FiscalValidation.validateSiret(state.clientSiret) is ValidationResult.Invalid) {
            errors[InvoiceFormField.CLIENT_SIRET] = ValidationErrorKey.CLIENT_SIRET_INVALID
        }
        if (!EMAIL_REGEX.matches(state.clientEmail.trim())) {
            errors[InvoiceFormField.CLIENT_EMAIL] = ValidationErrorKey.CLIENT_EMAIL_INVALID
        }

        val validatedLines = state.lines.map(::validateLine)

        // Seules les lignes sans erreur contribuent aux totaux affichés — une ligne invalide
        // ne fausse pas le total mais bloque la soumission globale (voir isSubmitEnabled).
        val validDomainLines = validatedLines.mapNotNull { line -> line.toDomainOrNull() }

        return state.copy(
            errors = errors,
            lines = validatedLines,
            totalHt = totalHtOf(validDomainLines),
            totalVat = totalVatOf(validDomainLines),
            totalTtc = totalTtcOf(validDomainLines),
        )
    }

    private fun validateLine(line: InvoiceLineFormState): InvoiceLineFormState {
        val lineErrors = mutableMapOf<InvoiceLineField, ValidationErrorKey>()

        if (line.label.isBlank()) {
            lineErrors[InvoiceLineField.LABEL] = ValidationErrorKey.LINE_LABEL_REQUIRED
        }
        val quantity = line.quantity.toIntOrNull()
        if (quantity == null || quantity <= 0) {
            lineErrors[InvoiceLineField.QUANTITY] = ValidationErrorKey.LINE_QUANTITY_INVALID
        }
        val unitPriceCents = parseAmountToCents(line.unitPriceHt)
        if (unitPriceCents == null || unitPriceCents <= 0) {
            lineErrors[InvoiceLineField.UNIT_PRICE] = ValidationErrorKey.LINE_UNIT_PRICE_INVALID
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

    private fun buildInvoice(state: InvoiceFormUiState, status: InvoiceStatus): Invoice = Invoice(
        number = state.invoiceNumber,
        issueDate = state.issueDate,
        status = status,
        issuer = state.issuer,
        recipient = Party(
            name = state.clientName,
            // Le SIREN est les 9 premiers chiffres du SIRET (règle INSEE) — déjà validé à 14 chiffres.
            siren = state.clientSiret.take(9),
            siret = state.clientSiret,
            email = state.clientEmail.trim(),
        ),
        lines = state.lines.map { line ->
            InvoiceLine(
                label = line.label,
                quantity = line.quantity.toInt(),
                unitPriceHt = Money(parseAmountToCents(line.unitPriceHt)!!),
                vatRate = line.vatRate,
            )
        },
        dueDate = state.dueDate,
        facturX = state.generateFacturX,
    )

    /**
     * Chemin d'écriture commun aux deux actions. [targetStatus] est la seule différence :
     * [InvoiceStatus.DRAFT] pour un enregistrement, [InvoiceStatus.DEPOSITED] pour une émission.
     *
     * Une écriture déjà en cours est ignorée : le garde-fou complète la désactivation des boutons
     * côté écran et couvre le double appui rapide.
     */
    private fun submit(targetStatus: InvoiceStatus) {
        if (_uiState.value.isSubmitting) return

        // Une tentative d'écriture révèle toutes les erreurs, y compris sur les champs jamais
        // saisis : l'utilisateur doit voir ce qui bloque, même sans avoir touché au formulaire.
        val revalidated = revalidate(_uiState.value).copy(submitAttempted = true)
        val hasLineErrors = revalidated.lines.any { it.errors.isNotEmpty() }
        if (revalidated.errors.isNotEmpty() || hasLineErrors) {
            _uiState.value = revalidated
            return
        }

        val invoice = buildInvoice(revalidated, targetStatus)
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
