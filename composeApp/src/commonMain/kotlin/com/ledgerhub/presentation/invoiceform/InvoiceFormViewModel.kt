package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.data.invoice.MockInvoiceRepository
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
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // L'état initial reflète aussi les erreurs de validation (formulaire vide = invalide) :
    // sans ce revalidate, isSubmitEnabled serait incorrectement `true` avant toute saisie.
    private val _uiState = MutableStateFlow(
        revalidate(
            InvoiceFormUiState(
                issuer = issuer,
                lines = listOf(InvoiceLineFormState(vatRate = defaultVatRate)),
            ),
        ),
    )
    val uiState: StateFlow<InvoiceFormUiState> = _uiState.asStateFlow()

    fun processIntent(intent: InvoiceFormIntent) {
        when (intent) {
            InvoiceFormIntent.SaveDraft -> submit(InvoiceStatus.DRAFT)
            InvoiceFormIntent.ValidateAndIssue -> submit(InvoiceStatus.VALIDATED)
            else -> _uiState.update { current -> revalidate(applyChange(current, intent)) }
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

            is InvoiceFormIntent.ClientNameChanged ->
                current.copy(clientName = intent.value).touch(InvoiceFormField.CLIENT_NAME)

            is InvoiceFormIntent.ClientSiretChanged ->
                current.copy(clientSiret = intent.value).touch(InvoiceFormField.CLIENT_SIRET)

            is InvoiceFormIntent.ClientEmailChanged ->
                current.copy(clientEmail = intent.value).touch(InvoiceFormField.CLIENT_EMAIL)
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

            // Les deux intentions d'écriture sont interceptées en amont par processIntent.
            InvoiceFormIntent.SaveDraft, InvoiceFormIntent.ValidateAndIssue -> current
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
     * [InvoiceStatus.DRAFT] pour un enregistrement, [InvoiceStatus.VALIDATED] pour une émission.
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
