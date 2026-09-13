package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.compliance.ComplianceAuditor
import com.ledgerhub.domain.compliance.ComplianceSubject
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.i18n.ValidationErrorKey
import com.ledgerhub.domain.invoice.DeliveryAddress
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.SirenValidator
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.invoice.TransactionMode
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.invoice.totalHtOf
import com.ledgerhub.domain.invoice.totalTtcOf
import com.ledgerhub.domain.invoice.totalVatOf
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.degraded.DegradedModeNetworkState
import com.ledgerhub.domain.degraded.DegradedChannel
import com.ledgerhub.domain.degraded.EnqueueDegradedInvoiceUseCase
import com.ledgerhub.presentation.components.QuickClientDraft
import com.ledgerhub.presentation.components.QuickClientField
import com.ledgerhub.presentation.components.validateQuickClient
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

/** Un SIRET est un SIREN de 9 chiffres suivi d'un NIC de 5 — voir `LuhnChecksum`. */
private const val SIREN_LENGTH = 9

private val ISO_DATE_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val EMAIL_REGEX = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

private fun formatUnitPrice(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString()
    else "${cents / 100L}.${(cents % 100L).toString().padStart(2, '0')}"

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
     * Numéro de TVA intracommunautaire de l'émetteur, audité par le panneau de conformité (US-24).
     *
     * Injecté plutôt que lu dans l'état : il appartient aux paramètres fiscaux du cabinet
     * ([TaxSettings]), pas à la facture. L'y recopier en ferait une donnée à tenir synchronisée
     * pour rien.
     */
    private val issuerVatNumber: String = TaxSettings.Default.vatNumber,
    /**
     * Annuaire des fiches clients alimentant le sélecteur (US-11). `null` = pas d'annuaire
     * branché : le champ raison sociale se comporte alors comme un champ libre, sans suggestion
     * ni création rapide (utile aux tests qui n'ont que faire du sélecteur).
     */
    private val clientRepository: ClientRepository? = null,
    /** Cas d'usage d'enfilement en mode dégradé (US-29). */
    private val enqueueDegradedInvoiceUseCase: EnqueueDegradedInvoiceUseCase? = null,
    /** État initial du réseau pour la simulation (US-29). */
    initialNetworkState: DegradedModeNetworkState = DegradedModeNetworkState.OPERATIONAL,
    /** Devis d'origine en cas de conversion — pré-remplit les coordonnées et les lignes. */
    sourceQuote: Quote? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial reflète aussi les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(
        revalidate(
            if (sourceQuote != null) {
                InvoiceFormUiState(
                    clientName = sourceQuote.recipient.name,
                    clientSiret = sourceQuote.recipient.siret,
                    clientSiren = sourceQuote.recipient.siren.ifBlank { sourceQuote.recipient.siret.take(9) },
                    clientEmail = sourceQuote.recipient.email,
                    clientQuery = sourceQuote.recipient.name,
                    issuer = sourceQuote.issuer.takeIf { it.name.isNotBlank() } ?: issuer,
                    lines = sourceQuote.lines.map {
                        InvoiceLineFormState(
                            label = it.label,
                            quantity = it.quantity.toString(),
                            unitPriceHt = formatUnitPrice(it.unitPriceHt.cents),
                            vatRate = it.vatRate,
                        )
                    }.ifEmpty { listOf(InvoiceLineFormState(vatRate = defaultVatRate)) },
                    sourceQuoteId = sourceQuote.number,
                    isClientDirectoryAvailable = clientRepository != null,
                    networkState = initialNetworkState,
                )
            } else {
                InvoiceFormUiState(
                    issuer = issuer,
                    lines = listOf(InvoiceLineFormState(vatRate = defaultVatRate)),
                    isClientDirectoryAvailable = clientRepository != null,
                    networkState = initialNetworkState,
                )
            }
        )
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
            InvoiceFormIntent.SubmitDegraded -> submitDegraded()
            is InvoiceFormIntent.NetworkStateChanged ->
                _uiState.update { it.copy(networkState = intent.state) }
            is InvoiceFormIntent.DegradedChannelChanged ->
                _uiState.update { it.copy(degradedChannel = intent.channel) }

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

            // Hors du chemin de `revalidate`, qui invalide justement le rapport : y passer
            // effacerait le rapport dans le geste même qui le demande.
            InvoiceFormIntent.ComplianceScanRequested -> runComplianceScan()

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
                    clientSiren = client.siren.ifBlank { client.siret.take(9) },
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
        // Règles partagées avec le formulaire de devis — source unique (voir ClientPicker.kt).
        val errors = validateQuickClient(name, siret, email)

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
                    clientSiren = if (current.clientSiren.isBlank()) intent.value.take(9) else current.clientSiren,
                    selectedClient = current.selectedClient?.takeIf { it.siret == intent.value },
                ).touch(InvoiceFormField.CLIENT_SIRET)

            is InvoiceFormIntent.ClientEmailChanged ->
                current.copy(
                    clientEmail = intent.value,
                    selectedClient = current.selectedClient?.takeIf { it.email == intent.value },
                ).touch(InvoiceFormField.CLIENT_EMAIL)

            is InvoiceFormIntent.ToggleFacturX -> current.copy(generateFacturX = intent.enabled)

            // ── Réforme fiscale 2026 (US-27) ──────────────────────────────────────────
            is InvoiceFormIntent.TransactionModeChanged ->
                current.copy(transactionMode = intent.mode)

            is InvoiceFormIntent.ClientSirenChanged ->
                current.copy(clientSiren = intent.value).touch(InvoiceFormField.CLIENT_SIREN)

            is InvoiceFormIntent.NatureOperationChanged ->
                current.copy(natureOperation = intent.value)

            is InvoiceFormIntent.ToggleOptionTvaDebit ->
                current.copy(optionTvaDebit = intent.enabled)

            is InvoiceFormIntent.ToggleDifferentDeliveryAddress ->
                current.copy(hasDifferentDeliveryAddress = intent.enabled)

            is InvoiceFormIntent.DeliveryStreetChanged ->
                current.copy(deliveryStreet = intent.value).touch(InvoiceFormField.DELIVERY_STREET)

            is InvoiceFormIntent.DeliveryZipChanged ->
                current.copy(deliveryZip = intent.value).touch(InvoiceFormField.DELIVERY_ZIP)

            is InvoiceFormIntent.DeliveryCityChanged ->
                current.copy(deliveryCity = intent.value).touch(InvoiceFormField.DELIVERY_CITY)

            is InvoiceFormIntent.DeliveryCountryChanged ->
                current.copy(deliveryCountry = intent.value).touch(InvoiceFormField.DELIVERY_COUNTRY)

            // Mention légale, pas champ de saisie : aucune erreur à produire, aucun total à
            // recalculer — d'où l'absence de `touch(...)` ici.
            is InvoiceFormIntent.ToggleB2bPenalties ->
                current.copy(applyB2bPenalties = intent.enabled)

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

            // Écritures et gestes du sélecteur client & mode dégradé : interceptés en amont par processIntent,
            // ils n'atteignent jamais cette branche.
            InvoiceFormIntent.SaveDraft,
            InvoiceFormIntent.ValidateAndIssue,
            InvoiceFormIntent.SubmitDegraded,
            is InvoiceFormIntent.NetworkStateChanged,
            is InvoiceFormIntent.DegradedChannelChanged,
            InvoiceFormIntent.ComplianceScanRequested,
            is InvoiceFormIntent.ClientNameChanged,
            is InvoiceFormIntent.OnClientQueryChanged,
            is InvoiceFormIntent.OnClientSelected,
            InvoiceFormIntent.OnOpenQuickClientDialog,
            InvoiceFormIntent.OnDismissQuickClientDialog,
            is InvoiceFormIntent.OnQuickClientFieldChanged,
            is InvoiceFormIntent.OnSaveQuickClient,
            -> current
        }

    // ── Audit de conformité (US-24) ──────────────────────────────────────────

    /**
     * Produit le rapport d'audit à partir de l'état courant.
     *
     * Le sujet ne retient que les **lignes valides** : une ligne encore incomplète sous le doigt
     * de l'utilisateur n'est pas un manquement réglementaire, c'est une saisie en cours. Les
     * totaux, eux, sont ceux que l'écran affiche — c'est leur cohérence avec les lignes que
     * l'auditeur vérifie, et la vérifier sur des valeurs recalculées pour l'occasion ne
     * prouverait rien.
     */
    private fun runComplianceScan() {
        _uiState.update { current ->
            current.copy(
                complianceReport = ComplianceAuditor.audit(
                    ComplianceSubject(
                        issuer = current.issuer,
                        issuerVatNumber = issuerVatNumber,
                        client = Party(
                            name = current.clientName,
                            siren = current.clientSiret.take(SIREN_LENGTH),
                            siret = current.clientSiret,
                            email = current.clientEmail,
                        ),
                        lines = current.lines.mapNotNull { it.toDomainOrNull() },
                        totalHt = current.totalHt,
                        totalVat = current.totalVat,
                        totalTtc = current.totalTtc,
                        applyB2bPenalties = current.applyB2bPenalties,
                        generateFacturX = current.generateFacturX,
                    ),
                ),
            )
        }
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
        // Validation SIRET
        if (state.transactionMode == TransactionMode.E_INVOICING) {
            if (FiscalValidation.validateSiret(state.clientSiret) is ValidationResult.Invalid) {
                errors[InvoiceFormField.CLIENT_SIRET] = ValidationErrorKey.CLIENT_SIRET_INVALID
            }
        } else {
            if (state.clientSiret.isNotBlank() && FiscalValidation.validateSiret(state.clientSiret) is ValidationResult.Invalid) {
                errors[InvoiceFormField.CLIENT_SIRET] = ValidationErrorKey.CLIENT_SIRET_INVALID
            }
        }

        // Validation conditionnelle SIREN (US-27, SirenValidator) selon le mode de transaction (B2B vs e-Reporting)
        if (SirenValidator.validate(state.clientSiren, state.transactionMode) is ValidationResult.Invalid) {
            errors[InvoiceFormField.CLIENT_SIREN] = ValidationErrorKey.CLIENT_SIRET_INVALID
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
            // La facture a changé : le rapport d'audit ne porte plus sur elle (US-24). Invalidé
            // ici parce que c'est le passage obligé de toute modification — un chemin parallèle
            // finirait par en oublier un.
            complianceReport = null,
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

    private fun buildInvoice(state: InvoiceFormUiState, status: InvoiceStatus): Invoice {
        val siren = if (state.clientSiret.length >= 9) state.clientSiret.take(9) else state.clientSiren
        return Invoice(
            number = state.invoiceNumber,
            issueDate = state.issueDate,
            status = status,
            issuer = state.issuer,
            recipient = Party(
                name = state.clientName,
                // Le SIREN est les 9 premiers chiffres du SIRET (règle INSEE) si disponible
                siren = siren,
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
            applyB2bPenalties = state.applyB2bPenalties,
            sourceQuoteId = state.sourceQuoteId,
            clientSiren = siren,
            natureOperation = state.natureOperation,
            optionTvaDebit = state.optionTvaDebit,
            isEReporting = state.transactionMode == TransactionMode.E_REPORTING,
            deliveryAddress = if (state.hasDifferentDeliveryAddress) {
                DeliveryAddress(
                    street = state.deliveryStreet,
                    zip = state.deliveryZip,
                    city = state.deliveryCity,
                    country = state.deliveryCountry.ifBlank { "France" },
                )
            } else {
                DeliveryAddress()
            },
        )
    }

    /**
     * Chemin d'écriture commun aux deux actions. [targetStatus] est la seule différence :
     * [InvoiceStatus.DRAFT] pour un enregistrement, [InvoiceStatus.DEPOSITED] pour une émission.
     *
     * Une écriture déjà en cours est ignorée : le garde-fou complète la désactivation des boutons
     * côté écran et couvre le double appui rapide.
     */
    private fun submit(targetStatus: InvoiceStatus) {
        if (_uiState.value.isSubmitting) return

        if (targetStatus == InvoiceStatus.DEPOSITED && _uiState.value.networkState == DegradedModeNetworkState.OUTAGE) {
            submitDegraded()
            return
        }

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

    private fun submitDegraded() {
        if (_uiState.value.isSubmitting) return

        val revalidated = revalidate(_uiState.value).copy(submitAttempted = true)
        val hasLineErrors = revalidated.lines.any { it.errors.isNotEmpty() }
        if (revalidated.errors.isNotEmpty() || hasLineErrors) {
            _uiState.value = revalidated
            return
        }

        val invoice = buildInvoice(revalidated, InvoiceStatus.PENDING_REGULARIZATION)
        _uiState.value = revalidated.copy(submissionStatus = SubmissionStatus.Loading)

        val enqueue = enqueueDegradedInvoiceUseCase
        if (enqueue != null) {
            scope.launch {
                val result = enqueue(invoice, revalidated.degradedChannel)
                _uiState.update { current ->
                    result.fold(
                        onSuccess = {
                            current.copy(
                                submissionStatus = SubmissionStatus.Success,
                                submittedInvoice = invoice.copy(status = InvoiceStatus.PENDING_REGULARIZATION),
                            )
                        },
                        onFailure = { throwable ->
                            current.copy(
                                submissionStatus = SubmissionStatus.Error(
                                    throwable.message ?: "Erreur lors de l'enregistrement en mode dégradé"
                                )
                            )
                        },
                    )
                }
            }
        } else {
            scope.launch {
                val result = submitInvoiceUseCase(invoice.copy(status = InvoiceStatus.PENDING_REGULARIZATION))
                _uiState.update { current ->
                    result.fold(
                        onSuccess = {
                            current.copy(
                                submissionStatus = SubmissionStatus.Success,
                                submittedInvoice = invoice.copy(status = InvoiceStatus.PENDING_REGULARIZATION),
                            )
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
    }

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : depuis onDestroy() ou rememberViewModel().
     * iOS     : depuis le deinit de la UIViewController.
     */
    fun onCleared() = scope.cancel()
}
