package com.ledgerhub.presentation.quoteform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.components.ClientPicker
import com.ledgerhub.presentation.components.ClientPickerTags
import com.ledgerhub.presentation.components.QuickClientDialog
import com.ledgerhub.presentation.components.filterAmount
import com.ledgerhub.presentation.components.filterQuantity
import com.ledgerhub.presentation.components.filterSiret
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
import com.ledgerhub.presentation.invoices.formatEuros
import com.ledgerhub.presentation.theme.LedgerHubTheme

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object QuoteFormTags {
    const val SCREEN = "quote_form_screen"
    const val QUOTE_NUMBER = "quote_form_number"
    const val ISSUE_DATE = "quote_form_issue_date"
    const val VALIDITY_DATE = "quote_form_validity_date"
    const val ISSUER_NAME = "quote_form_issuer_name"
    const val ISSUER_SIREN = "quote_form_issuer_siren"
    const val ISSUER_SIRET = "quote_form_issuer_siret"
    /**
     * Raison sociale du destinataire — devenue le champ de recherche du sélecteur client (US-11).
     */
    const val RECIPIENT_NAME = ClientPickerTags.CLIENT_SEARCH_INPUT

    const val RECIPIENT_SIREN = "quote_form_recipient_siren"
    const val RECIPIENT_SIRET = "quote_form_recipient_siret"
    const val RECIPIENT_EMAIL = "quote_form_recipient_email"
    const val ADD_LINE_BUTTON = "quote_form_add_line_button"
    const val TOTAL_HT = "quote_form_total_ht"
    const val TOTAL_VAT = "quote_form_total_vat"
    const val TOTAL_TTC = "quote_form_total_ttc"
    const val SAVE_DRAFT_BUTTON = "quote_form_save_draft_button"
    const val SUBMIT_BUTTON = "quote_form_submit_button"
    const val LOADING_INDICATOR = "quote_form_loading_indicator"
    const val SUCCESS_MESSAGE = "quote_form_success_message"
    const val ERROR_MESSAGE = "quote_form_error_message"

    fun errorTagFor(field: QuoteFormField) = "quote_form_error_${field.name}"

    fun lineLabelTag(index: Int) = "quote_form_line_${index}_label"
    fun lineQuantityTag(index: Int) = "quote_form_line_${index}_quantity"
    fun lineUnitPriceTag(index: Int) = "quote_form_line_${index}_unit_price"
    fun lineVatRateFieldTag(index: Int) = "quote_form_line_${index}_vat_rate_field"
    fun lineVatRateTag(index: Int, rate: VatRate) = "quote_form_line_${index}_vat_rate_${rate.name}"
    fun lineRemoveButtonTag(index: Int) = "quote_form_line_${index}_remove_button"
    fun lineErrorTag(index: Int, field: QuoteLineField) = "quote_form_line_${index}_error_${field.name}"
}

@Composable
fun QuoteFormScreen(
    viewModel: QuoteFormViewModel = remember { QuoteFormViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    QuoteFormContent(uiState = uiState, onIntent = viewModel::processIntent)
}

@Composable
internal fun QuoteFormContent(
    uiState: QuoteFormUiState,
    onIntent: (QuoteFormIntent) -> Unit = {},
) {
    val enabled = uiState.isFormEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = QuoteFormTags.SCREEN }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Créer un Devis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Proposition commerciale et engagement de prix.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Informations Client", glyph = "🏢") {
            // Sélecteur dynamique (US-11) : frappe filtre l'annuaire, sélection remplit SIRET/email
            ClientPicker(
                label = "Raison sociale",
                query = uiState.clientQuery,
                suggestions = uiState.clientSuggestions,
                isExpanded = uiState.isClientDropdownExpanded,
                showAddNewClientButton = uiState.showAddNewClientButton,
                enabled = enabled,
                error = uiState.visibleErrors[QuoteFormField.RECIPIENT_NAME],
                errorTag = QuoteFormTags.errorTagFor(QuoteFormField.RECIPIENT_NAME),
                onQueryChanged = { onIntent(QuoteFormIntent.OnClientQueryChanged(it)) },
                onClientSelected = { onIntent(QuoteFormIntent.OnClientSelected(it)) },
                onAddNewClient = { onIntent(QuoteFormIntent.OnOpenQuickClientDialog) },
            )
            FormField(
                label = "SIRET (14 chiffres)",
                value = uiState.recipientSiret,
                tag = QuoteFormTags.RECIPIENT_SIRET,
                error = uiState.visibleErrors[QuoteFormField.RECIPIENT_SIRET],
                errorTag = QuoteFormTags.errorTagFor(QuoteFormField.RECIPIENT_SIRET),
                enabled = enabled,
                onValueChange = { onIntent(QuoteFormIntent.RecipientSiretChanged(it)) },
                keyboardType = KeyboardType.Number,
                inputFilter = ::filterSiret,
            )
            FormField(
                label = "Adresse e-mail",
                value = uiState.recipientEmail,
                tag = QuoteFormTags.RECIPIENT_EMAIL,
                error = uiState.visibleErrors[QuoteFormField.RECIPIENT_EMAIL],
                errorTag = QuoteFormTags.errorTagFor(QuoteFormField.RECIPIENT_EMAIL),
                enabled = enabled,
                onValueChange = { onIntent(QuoteFormIntent.RecipientEmailChanged(it)) },
                keyboardType = KeyboardType.Email,
            )
        }

        SectionCard(title = "Détails du Devis", glyph = "📄") {
            FormField(
                label = "Numéro de devis",
                value = uiState.quoteNumber,
                tag = QuoteFormTags.QUOTE_NUMBER,
                error = uiState.visibleErrors[QuoteFormField.QUOTE_NUMBER],
                errorTag = QuoteFormTags.errorTagFor(QuoteFormField.QUOTE_NUMBER),
                enabled = enabled,
                onValueChange = { onIntent(QuoteFormIntent.QuoteNumberChanged(it)) },
            )
            FormField(
                label = "Date d'émission (AAAA-MM-JJ)",
                value = uiState.issueDate,
                tag = QuoteFormTags.ISSUE_DATE,
                error = uiState.visibleErrors[QuoteFormField.ISSUE_DATE],
                errorTag = QuoteFormTags.errorTagFor(QuoteFormField.ISSUE_DATE),
                enabled = enabled,
                onValueChange = { onIntent(QuoteFormIntent.IssueDateChanged(it)) },
            )
            FormField(
                label = "Date de validité (AAAA-MM-JJ)",
                value = uiState.validityDate,
                tag = QuoteFormTags.VALIDITY_DATE,
                error = uiState.visibleErrors[QuoteFormField.VALIDITY_DATE],
                errorTag = QuoteFormTags.errorTagFor(QuoteFormField.VALIDITY_DATE),
                enabled = enabled,
                onValueChange = { onIntent(QuoteFormIntent.ValidityDateChanged(it)) },
            )
        }

        SectionCard(title = "Lignes de prestation", glyph = "🧾") {
            uiState.lines.forEachIndexed { index, line ->
                QuoteLineForm(
                    index = index,
                    line = line,
                    enabled = enabled,
                    canRemove = uiState.canRemoveLines,
                    revealAllErrors = uiState.submitAttempted,
                    onIntent = onIntent,
                )
                if (index != uiState.lines.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
            TextButton(
                onClick = { onIntent(QuoteFormIntent.AddLine) },
                enabled = enabled,
                modifier = Modifier.semantics { testTag = QuoteFormTags.ADD_LINE_BUTTON },
            ) {
                Text("+ Ajouter une ligne")
            }
        }

        SectionCard(title = "Récapitulatif", glyph = "📊") {
            RecapRow("Total HT", uiState.totalHt.formatEuros(), QuoteFormTags.TOTAL_HT)
            RecapRow("Total TVA", uiState.totalVat.formatEuros(), QuoteFormTags.TOTAL_VAT)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total TTC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = uiState.totalTtc.formatEuros(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { testTag = QuoteFormTags.TOTAL_TTC },
                )
            }
        }

        when (val status = uiState.submissionStatus) {
            SubmissionStatus.Loading -> LoadingIndicator()
            SubmissionStatus.Success -> StatusBanner(
                text = "Devis créé avec succès",
                tag = QuoteFormTags.SUCCESS_MESSAGE,
                containerColor = LedgerHubTheme.palette.StatusPaidBg,
                contentColor = LedgerHubTheme.palette.StatusPaidFg,
            )
            is SubmissionStatus.Error -> StatusBanner(
                text = status.message,
                tag = QuoteFormTags.ERROR_MESSAGE,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            SubmissionStatus.Idle -> Unit
        }

        OutlinedButton(
            onClick = { onIntent(QuoteFormIntent.SaveDraft) },
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth().semantics { testTag = QuoteFormTags.SAVE_DRAFT_BUTTON },
        ) {
            Text("💾  Enregistrer le brouillon")
        }

        Button(
            onClick = { onIntent(QuoteFormIntent.FinalizeQuote) },
            enabled = !uiState.isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().semantics { testTag = QuoteFormTags.SUBMIT_BUTTON },
        ) {
            Text("📄  Finaliser le devis")
        }
    }

    val quickClientDraft = uiState.quickClientDraft
    if (uiState.showQuickClientDialog && quickClientDraft != null) {
        QuickClientDialog(
            name = quickClientDraft.name,
            siret = quickClientDraft.siret,
            email = quickClientDraft.email,
            errors = quickClientDraft.errors.mapKeys { (field, _) -> field.name },
            isSaving = quickClientDraft.isSaving,
            onFieldChanged = { name, siret, email ->
                onIntent(QuoteFormIntent.OnQuickClientFieldChanged(name, siret, email))
            },
            onSave = {
                onIntent(
                    QuoteFormIntent.OnSaveQuickClient(
                        name = quickClientDraft.name,
                        siret = quickClientDraft.siret,
                        email = quickClientDraft.email,
                    ),
                )
            },
            onDismiss = { onIntent(QuoteFormIntent.OnDismissQuickClientDialog) },
        )
    }
}

@Composable
private fun QuoteLineForm(
    index: Int,
    line: QuoteLineFormState,
    enabled: Boolean,
    canRemove: Boolean,
    revealAllErrors: Boolean,
    onIntent: (QuoteFormIntent) -> Unit,
) {
    val visibleErrors = line.visibleErrors(revealAllErrors)

    fun update(
        label: String = line.label,
        quantity: String = line.quantity,
        unitPriceHt: String = line.unitPriceHt,
        vatRate: VatRate = line.vatRate,
    ) {
        onIntent(QuoteFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Ligne ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = { onIntent(QuoteFormIntent.RemoveLine(index)) },
                enabled = canRemove,
                modifier = Modifier.semantics { testTag = QuoteFormTags.lineRemoveButtonTag(index) },
            ) {
                Text("🗑")
            }
        }
        FormField(
            label = "Libellé de la prestation",
            value = line.label,
            tag = QuoteFormTags.lineLabelTag(index),
            error = visibleErrors[QuoteLineField.LABEL],
            errorTag = QuoteFormTags.lineErrorTag(index, QuoteLineField.LABEL),
            enabled = enabled,
            onValueChange = { update(label = it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormField(
                    label = "Qté",
                    value = line.quantity,
                    tag = QuoteFormTags.lineQuantityTag(index),
                    error = visibleErrors[QuoteLineField.QUANTITY],
                    errorTag = QuoteFormTags.lineErrorTag(index, QuoteLineField.QUANTITY),
                    enabled = enabled,
                    onValueChange = { update(quantity = it) },
                    keyboardType = KeyboardType.Number,
                    inputFilter = ::filterQuantity,
                )
            }
            Box(modifier = Modifier.weight(1.4f)) {
                FormField(
                    label = "Prix unitaire HT",
                    value = line.unitPriceHt,
                    tag = QuoteFormTags.lineUnitPriceTag(index),
                    error = visibleErrors[QuoteLineField.UNIT_PRICE],
                    errorTag = QuoteFormTags.lineErrorTag(index, QuoteLineField.UNIT_PRICE),
                    enabled = enabled,
                    onValueChange = { update(unitPriceHt = it) },
                    keyboardType = KeyboardType.Decimal,
                    inputFilter = ::filterAmount,
                )
            }
        }
        VatRateDropdown(
            index = index,
            selected = line.vatRate,
            enabled = enabled,
            onSelected = { update(vatRate = it) },
        )
    }
}

@Composable
private fun VatRateDropdown(
    index: Int,
    selected: VatRate,
    enabled: Boolean,
    onSelected: (VatRate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Taux de TVA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.semantics { testTag = QuoteFormTags.lineVatRateFieldTag(index) },
            ) {
                Text("${selected.label}  ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                VatRate.entries.forEach { rate ->
                    DropdownMenuItem(
                        text = { Text(rate.label) },
                        onClick = {
                            expanded = false
                            onSelected(rate)
                        },
                        modifier = Modifier.semantics { testTag = QuoteFormTags.lineVatRateTag(index, rate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    glyph: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) Text(glyph, style = MaterialTheme.typography.titleMedium)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    tag: String,
    error: String?,
    errorTag: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    inputFilter: ((String) -> String)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(inputFilter?.invoke(raw) ?: raw) },
        label = { Text(label) },
        isError = error != null,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
        singleLine = true,
    )
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics {
                testTag = errorTag
                contentDescription = error
            },
        )
    }
}

@Composable
private fun RecapRow(label: String, value: String, tag: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.semantics { testTag = tag })
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.semantics { testTag = QuoteFormTags.LOADING_INDICATOR },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text("Envoi en cours…")
    }
}

@Composable
private fun StatusBanner(text: String, tag: String, containerColor: Color, contentColor: Color) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            testTag = tag
            contentDescription = text
        },
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

