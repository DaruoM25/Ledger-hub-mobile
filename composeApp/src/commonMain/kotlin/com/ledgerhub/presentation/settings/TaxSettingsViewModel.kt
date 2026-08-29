package com.ledgerhub.presentation.settings

import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.settings.TaxSettingsRepository
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

/** Champ du formulaire de paramètres fiscaux. */
enum class TaxSettingsField {
    ISSUER_NAME,
    ISSUER_SIRET,
    VAT_NUMBER,
}

/**
 * État de l'écran Paramètres fiscaux.
 *
 * [availableVatRates] est une **référence en lecture seule** : les taux sont fixés par la loi et
 * portés par [VatRate]. Seul [defaultVatRate] se configure — voir l'arbitrage consigné à l'audit.
 */
data class TaxSettingsUiState(
    val isLoading: Boolean = true,
    val issuerName: String = "",
    val issuerSiret: String = "",
    val vatNumber: String = "",
    val defaultVatRate: VatRate = VatRate.TAUX_NORMAL,
    val facturXEnabled: Boolean = true,
    val errors: Map<TaxSettingsField, String> = emptyMap(),
    val touchedFields: Set<TaxSettingsField> = emptySet(),
    val saveAttempted: Boolean = false,
    val isSaving: Boolean = false,
    /** Message de confirmation à présenter en Snackbar, consommé par [TaxSettingsIntent.FeedbackShown]. */
    val savedMessage: String? = null,
    val errorMessage: String? = null,
) {
    val availableVatRates: List<VatRate> get() = VatRate.entries

    /** SIREN dérivé du SIRET saisi (règle INSEE) — affiché, jamais saisi séparément. */
    val derivedSiren: String get() = issuerSiret.take(9)

    val isValid: Boolean get() = errors.isEmpty()

    val visibleErrors: Map<TaxSettingsField, String>
        get() = if (saveAttempted) errors else errors.filterKeys { it in touchedFields }
}

sealed interface TaxSettingsIntent {
    data object Load : TaxSettingsIntent
    data class IssuerNameChanged(val value: String) : TaxSettingsIntent
    data class IssuerSiretChanged(val value: String) : TaxSettingsIntent
    data class VatNumberChanged(val value: String) : TaxSettingsIntent
    data class DefaultVatRateSelected(val rate: VatRate) : TaxSettingsIntent
    data class FacturXToggled(val enabled: Boolean) : TaxSettingsIntent
    data object Save : TaxSettingsIntent
    data object FeedbackShown : TaxSettingsIntent
}

/**
 * ViewModel des paramètres fiscaux. Les valeurs enregistrées alimentent l'émetteur porté par
 * toute facture émise (voir `InvoiceFormViewModel`) : l'écran n'est pas décoratif.
 */
class TaxSettingsViewModel(
    private val repository: TaxSettingsRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(TaxSettingsUiState())
    val uiState: StateFlow<TaxSettingsUiState> = _uiState.asStateFlow()

    init {
        processIntent(TaxSettingsIntent.Load)
    }

    fun processIntent(intent: TaxSettingsIntent) {
        when (intent) {
            TaxSettingsIntent.Load -> load()

            is TaxSettingsIntent.IssuerNameChanged -> update(TaxSettingsField.ISSUER_NAME) {
                it.copy(issuerName = intent.value)
            }

            is TaxSettingsIntent.IssuerSiretChanged -> update(TaxSettingsField.ISSUER_SIRET) {
                it.copy(issuerSiret = intent.value)
            }

            is TaxSettingsIntent.VatNumberChanged -> update(TaxSettingsField.VAT_NUMBER) {
                it.copy(vatNumber = intent.value.uppercase())
            }

            is TaxSettingsIntent.DefaultVatRateSelected ->
                _uiState.update { revalidate(it.copy(defaultVatRate = intent.rate)) }

            is TaxSettingsIntent.FacturXToggled ->
                _uiState.update { revalidate(it.copy(facturXEnabled = intent.enabled)) }

            TaxSettingsIntent.Save -> save()

            TaxSettingsIntent.FeedbackShown ->
                _uiState.update { it.copy(savedMessage = null, errorMessage = null) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true) }
        scope.launch {
            val settings = repository.loadSettings().getOrDefault(TaxSettings.Default)
            _uiState.update {
                revalidate(
                    it.copy(
                        isLoading = false,
                        issuerName = settings.issuerName,
                        issuerSiret = settings.issuerSiret,
                        vatNumber = settings.vatNumber,
                        defaultVatRate = settings.defaultVatRate,
                        facturXEnabled = settings.facturXEnabled,
                    ),
                )
            }
        }
    }

    private fun update(field: TaxSettingsField, change: (TaxSettingsUiState) -> TaxSettingsUiState) {
        _uiState.update { current ->
            if (current.isSaving) return@update current
            revalidate(change(current).copy(touchedFields = current.touchedFields + field))
        }
    }

    private fun revalidate(state: TaxSettingsUiState): TaxSettingsUiState {
        val errors = mutableMapOf<TaxSettingsField, String>()
        (FiscalValidation.validateCompanyName(state.issuerName) as? ValidationResult.Invalid)?.let {
            errors[TaxSettingsField.ISSUER_NAME] = it.reason
        }
        (FiscalValidation.validateSiret(state.issuerSiret) as? ValidationResult.Invalid)?.let {
            errors[TaxSettingsField.ISSUER_SIRET] = it.reason
        }
        // TVA intracommunautaire optionnelle : une entreprise en franchise n'en a pas.
        (FiscalValidation.validateVatNumber(state.vatNumber) as? ValidationResult.Invalid)?.let {
            errors[TaxSettingsField.VAT_NUMBER] = it.reason
        }
        return state.copy(errors = errors)
    }

    private fun save() {
        val current = _uiState.value
        if (current.isSaving) return

        val revalidated = revalidate(current).copy(saveAttempted = true)
        if (!revalidated.isValid) {
            _uiState.value = revalidated
            return
        }

        _uiState.value = revalidated.copy(isSaving = true)
        val settings = TaxSettings(
            issuerName = revalidated.issuerName.trim(),
            issuerSiren = revalidated.derivedSiren,
            issuerSiret = revalidated.issuerSiret,
            issuerEmail = TaxSettings.Default.issuerEmail,
            vatNumber = revalidated.vatNumber.trim(),
            defaultVatRate = revalidated.defaultVatRate,
            facturXEnabled = revalidated.facturXEnabled,
        )

        scope.launch {
            repository.saveSettings(settings).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, savedMessage = SAVED_MESSAGE) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = throwable.message ?: SAVE_FAILED_MESSAGE)
                    }
                },
            )
        }
    }

    fun onCleared() = scope.cancel()

    private companion object {
        const val SAVED_MESSAGE = "Paramètres fiscaux mis à jour avec succès"
        const val SAVE_FAILED_MESSAGE = "Enregistrement des paramètres impossible"
    }
}
