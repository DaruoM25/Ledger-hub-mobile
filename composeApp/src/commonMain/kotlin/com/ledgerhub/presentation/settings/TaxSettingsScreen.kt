package com.ledgerhub.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.clients.DialogField
import com.ledgerhub.presentation.components.filterSiret
import com.ledgerhub.presentation.components.filterVatNumber
import com.ledgerhub.presentation.i18n.tr

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests. */
object TaxSettingsTags {
    const val SCREEN = "settings_screen"
    const val ISSUER_NAME = "settings_issuer_name"
    const val ISSUER_SIRET = "settings_issuer_siret"
    const val DERIVED_SIREN = "settings_derived_siren"
    const val VAT_NUMBER = "settings_vat_number"
    const val VAT_RATES_REFERENCE = "settings_vat_rates_reference"
    const val FACTURX_SWITCH = "settings_facturx_switch"
    const val SAVE_BUTTON = "settings_save_button"
    const val SNACKBAR_HOST = "settings_snackbar_host"

    fun defaultRateChip(rate: VatRate) = "settings_default_rate_${rate.name}"
    fun errorTag(field: TaxSettingsField) = "settings_error_${field.name}"
}

@Composable
fun TaxSettingsScreen(viewModel: TaxSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    TaxSettingsView(uiState = uiState, onIntent = viewModel::processIntent)
}

@Composable
internal fun TaxSettingsView(
    uiState: TaxSettingsUiState,
    onIntent: (TaxSettingsIntent) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Le message de confirmation est un événement : on le consomme dès qu'il est présenté,
    // pour qu'une recomposition ou une rotation ne le rejoue pas.
    LaunchedEffect(uiState.savedMessage, uiState.errorMessage) {
        val message = uiState.savedMessage ?: uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onIntent(TaxSettingsIntent.FeedbackShown)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .semantics { testTag = TaxSettingsTags.SCREEN }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr(StringKey.SETTINGS_TITLE),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.SETTINGS_SUBTITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = tr(StringKey.SETTINGS_SECTION_ISSUER), glyph = "🏛") {
                DialogField(
                    label = tr(StringKey.SETTINGS_FIELD_ISSUER_NAME),
                    value = uiState.issuerName,
                    tag = TaxSettingsTags.ISSUER_NAME,
                    error = uiState.visibleErrors[TaxSettingsField.ISSUER_NAME],
                    errorTag = TaxSettingsTags.errorTag(TaxSettingsField.ISSUER_NAME),
                    enabled = !uiState.isSaving,
                    onValueChange = { onIntent(TaxSettingsIntent.IssuerNameChanged(it)) },
                )
                DialogField(
                    label = tr(StringKey.SETTINGS_FIELD_ISSUER_SIRET),
                    value = uiState.issuerSiret,
                    tag = TaxSettingsTags.ISSUER_SIRET,
                    error = uiState.visibleErrors[TaxSettingsField.ISSUER_SIRET],
                    errorTag = TaxSettingsTags.errorTag(TaxSettingsField.ISSUER_SIRET),
                    enabled = !uiState.isSaving,
                    onValueChange = { onIntent(TaxSettingsIntent.IssuerSiretChanged(it)) },
                    keyboardType = KeyboardType.Number,
                    inputFilter = ::filterSiret,
                )
                // Le SIREN n'est jamais saisi : c'est le préfixe à 9 chiffres du SIRET (règle INSEE).
                ReadOnlyRow(
                    label = tr(StringKey.SETTINGS_FIELD_SIREN_DERIVED),
                    value = uiState.derivedSiren.ifBlank { "—" },
                    tag = TaxSettingsTags.DERIVED_SIREN,
                )
                DialogField(
                    label = tr(StringKey.SETTINGS_FIELD_VAT_NUMBER),
                    value = uiState.vatNumber,
                    tag = TaxSettingsTags.VAT_NUMBER,
                    error = uiState.visibleErrors[TaxSettingsField.VAT_NUMBER],
                    errorTag = TaxSettingsTags.errorTag(TaxSettingsField.VAT_NUMBER),
                    enabled = !uiState.isSaving,
                    onValueChange = { onIntent(TaxSettingsIntent.VatNumberChanged(it)) },
                    inputFilter = ::filterVatNumber,
                )
            }

            SettingsCard(title = tr(StringKey.SETTINGS_SECTION_VAT), glyph = "％") {
                // Les taux sont fixés par la loi : présentés en référence, jamais éditables.
                // Seul le taux pré-sélectionné à la saisie d'une ligne se configure.
                Text(
                    tr(StringKey.SETTINGS_VAT_RATES_REFERENCE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = TaxSettingsTags.VAT_RATES_REFERENCE },
                )
                Text(
                    uiState.availableVatRates.joinToString("  ·  ") { it.label },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    tr(StringKey.SETTINGS_DEFAULT_VAT_RATE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.availableVatRates.forEach { rate ->
                        RateChip(
                            rate = rate,
                            selected = rate == uiState.defaultVatRate,
                            onSelect = { onIntent(TaxSettingsIntent.DefaultVatRateSelected(rate)) },
                        )
                    }
                }
            }

            SettingsCard(title = tr(StringKey.SETTINGS_SECTION_COMPLIANCE), glyph = "🛡") {
                val switchLabel = tr(StringKey.SETTINGS_FACTURX_SWITCH)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        switchLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(
                        checked = uiState.facturXEnabled,
                        onCheckedChange = { onIntent(TaxSettingsIntent.FacturXToggled(it)) },
                        enabled = !uiState.isSaving,
                        modifier = Modifier.semantics {
                            testTag = TaxSettingsTags.FACTURX_SWITCH
                            contentDescription = switchLabel
                        },
                    )
                }
            }

            Button(
                onClick = { onIntent(TaxSettingsIntent.Save) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().semantics { testTag = TaxSettingsTags.SAVE_BUTTON },
            ) {
                Text(tr(StringKey.SETTINGS_SAVE))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics { testTag = TaxSettingsTags.SNACKBAR_HOST },
        )
    }
}

@Composable
private fun SettingsCard(title: String, glyph: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(glyph)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun ReadOnlyRow(label: String, value: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { testTag = tag },
        )
    }
}

@Composable
private fun RateChip(rate: VatRate, selected: Boolean, onSelect: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { testTag = TaxSettingsTags.defaultRateChip(rate) },
    ) {
        Text(
            rate.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else border,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
