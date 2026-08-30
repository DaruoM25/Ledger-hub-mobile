package com.ledgerhub.presentation.quoteform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.components.ClientPicker
import com.ledgerhub.presentation.components.ClientPickerTags
import com.ledgerhub.presentation.components.QuickClientDialog
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

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
     * Alias de [ClientPickerTags.CLIENT_SEARCH_INPUT] : les tests antérieurs continuent de
     * désigner le même nœud.
     */
    const val RECIPIENT_NAME = ClientPickerTags.CLIENT_SEARCH_INPUT

    const val RECIPIENT_SIREN = "quote_form_recipient_siren"
    const val RECIPIENT_SIRET = "quote_form_recipient_siret"
    const val ADD_LINE_BUTTON = "quote_form_add_line_button"
    const val TOTAL_TTC = "quote_form_total_ttc"
    const val SUBMIT_BUTTON = "quote_form_submit_button"
    const val LOADING_INDICATOR = "quote_form_loading_indicator"
    const val SUCCESS_MESSAGE = "quote_form_success_message"
    const val ERROR_MESSAGE = "quote_form_error_message"

    fun errorTagFor(field: QuoteFormField) = "quote_form_error_${field.name}"

    fun lineLabelTag(index: Int) = "quote_form_line_${index}_label"
    fun lineQuantityTag(index: Int) = "quote_form_line_${index}_quantity"
    fun lineUnitPriceTag(index: Int) = "quote_form_line_${index}_unit_price"
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
    val fieldsEnabled = uiState.isFormEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = QuoteFormTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Devis")
        FormField(
            label = "Numéro de devis",
            value = uiState.quoteNumber,
            tag = QuoteFormTags.QUOTE_NUMBER,
            error = uiState.errors[QuoteFormField.QUOTE_NUMBER],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.QUOTE_NUMBER),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.QuoteNumberChanged(it)) },
        )
        FormField(
            label = "Date d'émission (AAAA-MM-JJ)",
            value = uiState.issueDate,
            tag = QuoteFormTags.ISSUE_DATE,
            error = uiState.errors[QuoteFormField.ISSUE_DATE],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.ISSUE_DATE),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.IssueDateChanged(it)) },
        )
        FormField(
            label = "Date de validité (AAAA-MM-JJ)",
            value = uiState.validityDate,
            tag = QuoteFormTags.VALIDITY_DATE,
            error = uiState.errors[QuoteFormField.VALIDITY_DATE],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.VALIDITY_DATE),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.ValidityDateChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Émetteur")
        FormField(
            label = "Raison sociale",
            value = uiState.issuerName,
            tag = QuoteFormTags.ISSUER_NAME,
            error = uiState.errors[QuoteFormField.ISSUER_NAME],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.ISSUER_NAME),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.IssuerNameChanged(it)) },
        )
        FormField(
            label = "SIREN (9 chiffres)",
            value = uiState.issuerSiren,
            tag = QuoteFormTags.ISSUER_SIREN,
            error = uiState.errors[QuoteFormField.ISSUER_SIREN],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.ISSUER_SIREN),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.IssuerSirenChanged(it)) },
        )
        FormField(
            label = "SIRET (14 chiffres)",
            value = uiState.issuerSiret,
            tag = QuoteFormTags.ISSUER_SIRET,
            error = uiState.errors[QuoteFormField.ISSUER_SIRET],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.ISSUER_SIRET),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.IssuerSiretChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Destinataire")
        // Sélecteur dynamique (US-11) : la frappe filtre l'annuaire, la sélection remplit
        // SIREN et SIRET, une saisie inconnue propose la création rapide.
        ClientPicker(
            label = "Raison sociale",
            query = uiState.clientQuery,
            suggestions = uiState.clientSuggestions,
            isExpanded = uiState.isClientDropdownExpanded,
            showAddNewClientButton = uiState.showAddNewClientButton,
            enabled = fieldsEnabled,
            error = uiState.errors[QuoteFormField.RECIPIENT_NAME],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.RECIPIENT_NAME),
            onQueryChanged = { onIntent(QuoteFormIntent.OnClientQueryChanged(it)) },
            onClientSelected = { onIntent(QuoteFormIntent.OnClientSelected(it)) },
            onAddNewClient = { onIntent(QuoteFormIntent.OnOpenQuickClientDialog) },
        )
        FormField(
            label = "SIREN (9 chiffres)",
            value = uiState.recipientSiren,
            tag = QuoteFormTags.RECIPIENT_SIREN,
            error = uiState.errors[QuoteFormField.RECIPIENT_SIREN],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.RECIPIENT_SIREN),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.RecipientSirenChanged(it)) },
        )
        FormField(
            label = "SIRET (14 chiffres)",
            value = uiState.recipientSiret,
            tag = QuoteFormTags.RECIPIENT_SIRET,
            error = uiState.errors[QuoteFormField.RECIPIENT_SIRET],
            errorTag = QuoteFormTags.errorTagFor(QuoteFormField.RECIPIENT_SIRET),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(QuoteFormIntent.RecipientSiretChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Lignes de devis")
        uiState.lines.forEachIndexed { index, line ->
            QuoteLineForm(
                index = index,
                line = line,
                enabled = fieldsEnabled,
                canRemove = uiState.canRemoveLines,
                onIntent = onIntent,
            )
            if (index != uiState.lines.lastIndex) HorizontalDivider()
        }
        TextButton(
            onClick = { onIntent(QuoteFormIntent.AddLine) },
            enabled = fieldsEnabled,
            modifier = Modifier.semantics { testTag = QuoteFormTags.ADD_LINE_BUTTON },
        ) {
            Text("+ Ajouter une ligne")
        }

        HorizontalDivider()
        SectionTitle("Total")
        Text(
            text = "TTC : ${formatCents(uiState.totalTtc.cents)} €",
            modifier = Modifier.semantics { testTag = QuoteFormTags.TOTAL_TTC },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("HT : ${formatCents(uiState.totalHt.cents)} € — TVA : ${formatCents(uiState.totalVat.cents)} €")

        when (val status = uiState.submissionStatus) {
            SubmissionStatus.Loading -> LoadingIndicator()
            SubmissionStatus.Success -> StatusBanner(
                text = "Devis créé avec succès",
                tag = QuoteFormTags.SUCCESS_MESSAGE,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            is SubmissionStatus.Error -> StatusBanner(
                text = status.message,
                tag = QuoteFormTags.ERROR_MESSAGE,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            SubmissionStatus.Idle -> Unit
        }

        Button(
            onClick = { onIntent(QuoteFormIntent.Submit) },
            enabled = uiState.isSubmitEnabled,
            modifier = Modifier.semantics { testTag = QuoteFormTags.SUBMIT_BUTTON },
        ) {
            Text("Créer le devis")
        }
    }

    // Hors de la colonne défilante : une modale ne se fait pas défiler avec le formulaire.
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
    onIntent: (QuoteFormIntent) -> Unit,
) {
    fun update(label: String = line.label, quantity: String = line.quantity, unitPriceHt: String = line.unitPriceHt, vatRate: VatRate = line.vatRate) {
        onIntent(QuoteFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionTitle("Ligne ${index + 1}")
            IconButton(
                onClick = { onIntent(QuoteFormIntent.RemoveLine(index)) },
                enabled = canRemove,
                modifier = Modifier.semantics { testTag = QuoteFormTags.lineRemoveButtonTag(index) },
            ) {
                Text("🗑")
            }
        }
        FormField(
            label = "Libellé",
            value = line.label,
            tag = QuoteFormTags.lineLabelTag(index),
            error = line.errors[QuoteLineField.LABEL],
            errorTag = QuoteFormTags.lineErrorTag(index, QuoteLineField.LABEL),
            enabled = enabled,
            onValueChange = { update(label = it) },
        )
        FormField(
            label = "Quantité",
            value = line.quantity,
            tag = QuoteFormTags.lineQuantityTag(index),
            error = line.errors[QuoteLineField.QUANTITY],
            errorTag = QuoteFormTags.lineErrorTag(index, QuoteLineField.QUANTITY),
            enabled = enabled,
            onValueChange = { update(quantity = it) },
        )
        FormField(
            label = "Prix unitaire HT",
            value = line.unitPriceHt,
            tag = QuoteFormTags.lineUnitPriceTag(index),
            error = line.errors[QuoteLineField.UNIT_PRICE],
            errorTag = QuoteFormTags.lineErrorTag(index, QuoteLineField.UNIT_PRICE),
            enabled = enabled,
            onValueChange = { update(unitPriceHt = it) },
        )
        Text("Taux de TVA", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VatRate.entries.forEach { rate ->
                VatRateChip(
                    tag = QuoteFormTags.lineVatRateTag(index, rate),
                    label = rate.label,
                    selected = line.vatRate == rate,
                    enabled = enabled,
                    onClick = { update(vatRate = rate) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        enabled = enabled,
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
private fun VatRateChip(tag: String, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { testTag = tag }) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { testTag = tag }) {
            Text(label)
        }
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

/**
 * Bannière de statut persistante et testable (au lieu d'un Snackbar éphémère dont le timing
 * d'apparition/disparition animé rendrait les tests instrumentés non déterministes).
 */
@Composable
private fun StatusBanner(text: String, tag: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, contentColor = contentColor) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics {
                    testTag = tag
                    contentDescription = text
                },
        )
    }
}

/** Formate des centimes en chaîne décimale (ex: 1250 -> "12.50"), sans dépendre de NumberFormat (JVM-only). */
private fun formatCents(cents: Long): String {
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "$whole.$fraction"
}
