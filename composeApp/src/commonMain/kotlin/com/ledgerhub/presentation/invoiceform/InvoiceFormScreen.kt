package com.ledgerhub.presentation.invoiceform

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.invoice.VatRate

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object InvoiceFormTags {
    const val SCREEN = "invoice_form_screen"
    const val INVOICE_NUMBER = "invoice_form_number"
    const val ISSUE_DATE = "invoice_form_issue_date"
    const val ISSUER_NAME = "invoice_form_issuer_name"
    const val ISSUER_SIREN = "invoice_form_issuer_siren"
    const val ISSUER_SIRET = "invoice_form_issuer_siret"
    const val RECIPIENT_NAME = "invoice_form_recipient_name"
    const val RECIPIENT_SIREN = "invoice_form_recipient_siren"
    const val RECIPIENT_SIRET = "invoice_form_recipient_siret"
    const val ADD_LINE_BUTTON = "invoice_form_add_line_button"
    const val TOTAL_TTC = "invoice_form_total_ttc"
    const val SUBMIT_BUTTON = "invoice_form_submit_button"
    const val LOADING_INDICATOR = "invoice_form_loading_indicator"
    const val SUCCESS_MESSAGE = "invoice_form_success_message"
    const val ERROR_MESSAGE = "invoice_form_error_message"
    const val PREVIEW_MODE_TOGGLE = "invoice_form_preview_mode_toggle"

    fun errorTagFor(field: InvoiceFormField) = "invoice_form_error_${field.name}"

    fun lineLabelTag(index: Int) = "invoice_form_line_${index}_label"
    fun lineQuantityTag(index: Int) = "invoice_form_line_${index}_quantity"
    fun lineUnitPriceTag(index: Int) = "invoice_form_line_${index}_unit_price"
    fun lineVatRateTag(index: Int, rate: VatRate) = "invoice_form_line_${index}_vat_rate_${rate.name}"
    fun lineRemoveButtonTag(index: Int) = "invoice_form_line_${index}_remove_button"
    fun lineErrorTag(index: Int, field: InvoiceLineField) = "invoice_form_line_${index}_error_${field.name}"
}

@Composable
fun InvoiceFormScreen(
    viewModel: InvoiceFormViewModel = remember { InvoiceFormViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()

    // Bascule purement visuelle — n'affecte ni l'état ni la validation, qui restent la même
    // source de vérité (InvoiceFormUiState) pour les deux représentations du formulaire.
    var isPreviewMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        PreviewModeToggle(isPreviewMode = isPreviewMode, onToggle = { isPreviewMode = !isPreviewMode })
        if (isPreviewMode) {
            InvoicePaperCanvas(uiState = uiState, onIntent = viewModel::processIntent)
        } else {
            InvoiceFormContent(uiState = uiState, onIntent = viewModel::processIntent)
        }
    }
}

@Composable
private fun PreviewModeToggle(isPreviewMode: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.semantics { testTag = InvoiceFormTags.PREVIEW_MODE_TOGGLE },
        ) {
            Text(if (isPreviewMode) "Formulaire" else "Aperçu visuel")
        }
    }
}

@Composable
internal fun InvoiceFormContent(
    uiState: InvoiceFormUiState,
    onIntent: (InvoiceFormIntent) -> Unit = {},
) {
    val fieldsEnabled = uiState.isFormEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = InvoiceFormTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Facture")
        FormField(
            label = "Numéro de facture",
            value = uiState.invoiceNumber,
            tag = InvoiceFormTags.INVOICE_NUMBER,
            error = uiState.errors[InvoiceFormField.INVOICE_NUMBER],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.INVOICE_NUMBER),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.InvoiceNumberChanged(it)) },
        )
        FormField(
            label = "Date d'émission (AAAA-MM-JJ)",
            value = uiState.issueDate,
            tag = InvoiceFormTags.ISSUE_DATE,
            error = uiState.errors[InvoiceFormField.ISSUE_DATE],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUE_DATE),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.IssueDateChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Émetteur")
        FormField(
            label = "Raison sociale",
            value = uiState.issuerName,
            tag = InvoiceFormTags.ISSUER_NAME,
            error = uiState.errors[InvoiceFormField.ISSUER_NAME],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUER_NAME),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.IssuerNameChanged(it)) },
        )
        FormField(
            label = "SIREN (9 chiffres)",
            value = uiState.issuerSiren,
            tag = InvoiceFormTags.ISSUER_SIREN,
            error = uiState.errors[InvoiceFormField.ISSUER_SIREN],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUER_SIREN),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.IssuerSirenChanged(it)) },
        )
        FormField(
            label = "SIRET (14 chiffres)",
            value = uiState.issuerSiret,
            tag = InvoiceFormTags.ISSUER_SIRET,
            error = uiState.errors[InvoiceFormField.ISSUER_SIRET],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUER_SIRET),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.IssuerSiretChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Destinataire")
        FormField(
            label = "Raison sociale",
            value = uiState.recipientName,
            tag = InvoiceFormTags.RECIPIENT_NAME,
            error = uiState.errors[InvoiceFormField.RECIPIENT_NAME],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.RECIPIENT_NAME),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.RecipientNameChanged(it)) },
        )
        FormField(
            label = "SIREN (9 chiffres)",
            value = uiState.recipientSiren,
            tag = InvoiceFormTags.RECIPIENT_SIREN,
            error = uiState.errors[InvoiceFormField.RECIPIENT_SIREN],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.RECIPIENT_SIREN),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.RecipientSirenChanged(it)) },
        )
        FormField(
            label = "SIRET (14 chiffres)",
            value = uiState.recipientSiret,
            tag = InvoiceFormTags.RECIPIENT_SIRET,
            error = uiState.errors[InvoiceFormField.RECIPIENT_SIRET],
            errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.RECIPIENT_SIRET),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(InvoiceFormIntent.RecipientSiretChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Lignes de facturation")
        uiState.lines.forEachIndexed { index, line ->
            InvoiceLineForm(
                index = index,
                line = line,
                enabled = fieldsEnabled,
                canRemove = uiState.canRemoveLines,
                onIntent = onIntent,
            )
            if (index != uiState.lines.lastIndex) HorizontalDivider()
        }
        TextButton(
            onClick = { onIntent(InvoiceFormIntent.AddLine) },
            enabled = fieldsEnabled,
            modifier = Modifier.semantics { testTag = InvoiceFormTags.ADD_LINE_BUTTON },
        ) {
            Text("+ Ajouter une ligne")
        }

        HorizontalDivider()
        SectionTitle("Total")
        Text(
            text = "TTC : ${formatCents(uiState.totalTtc.cents)} €",
            modifier = Modifier.semantics { testTag = InvoiceFormTags.TOTAL_TTC },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("HT : ${formatCents(uiState.totalHt.cents)} € — TVA : ${formatCents(uiState.totalVat.cents)} €")

        when (val status = uiState.submissionStatus) {
            SubmissionStatus.Loading -> LoadingIndicator()
            SubmissionStatus.Success -> StatusBanner(
                text = "Facture créée et verrouillée avec succès",
                tag = InvoiceFormTags.SUCCESS_MESSAGE,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            is SubmissionStatus.Error -> StatusBanner(
                text = status.message,
                tag = InvoiceFormTags.ERROR_MESSAGE,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            SubmissionStatus.Idle -> Unit
        }

        Button(
            onClick = { onIntent(InvoiceFormIntent.Submit) },
            enabled = uiState.isSubmitEnabled,
            modifier = Modifier.semantics { testTag = InvoiceFormTags.SUBMIT_BUTTON },
        ) {
            Text("Finaliser et Verrouiller")
        }
    }
}

@Composable
private fun InvoiceLineForm(
    index: Int,
    line: InvoiceLineFormState,
    enabled: Boolean,
    canRemove: Boolean,
    onIntent: (InvoiceFormIntent) -> Unit,
) {
    fun update(label: String = line.label, quantity: String = line.quantity, unitPriceHt: String = line.unitPriceHt, vatRate: VatRate = line.vatRate) {
        onIntent(InvoiceFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SectionTitle("Ligne ${index + 1}")
            IconButton(
                onClick = { onIntent(InvoiceFormIntent.RemoveLine(index)) },
                enabled = canRemove,
                modifier = Modifier.semantics { testTag = InvoiceFormTags.lineRemoveButtonTag(index) },
            ) {
                Text("🗑")
            }
        }
        FormField(
            label = "Libellé",
            value = line.label,
            tag = InvoiceFormTags.lineLabelTag(index),
            error = line.errors[InvoiceLineField.LABEL],
            errorTag = InvoiceFormTags.lineErrorTag(index, InvoiceLineField.LABEL),
            enabled = enabled,
            onValueChange = { update(label = it) },
        )
        FormField(
            label = "Quantité",
            value = line.quantity,
            tag = InvoiceFormTags.lineQuantityTag(index),
            error = line.errors[InvoiceLineField.QUANTITY],
            errorTag = InvoiceFormTags.lineErrorTag(index, InvoiceLineField.QUANTITY),
            enabled = enabled,
            onValueChange = { update(quantity = it) },
        )
        FormField(
            label = "Prix unitaire HT",
            value = line.unitPriceHt,
            tag = InvoiceFormTags.lineUnitPriceTag(index),
            error = line.errors[InvoiceLineField.UNIT_PRICE],
            errorTag = InvoiceFormTags.lineErrorTag(index, InvoiceLineField.UNIT_PRICE),
            enabled = enabled,
            onValueChange = { update(unitPriceHt = it) },
        )
        Text("Taux de TVA", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VatRate.entries.forEach { rate ->
                VatRateChip(
                    tag = InvoiceFormTags.lineVatRateTag(index, rate),
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
        modifier = Modifier.semantics { testTag = InvoiceFormTags.LOADING_INDICATOR },
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

/**
 * Formate des centimes en chaîne décimale (ex: 1250 -> "12.50"), sans dépendre de NumberFormat
 * (JVM-only). `internal` pour être réutilisé par [InvoicePaperCanvas], seconde représentation du
 * même [InvoiceFormUiState].
 */
internal fun formatCents(cents: Long): String {
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "$whole.$fraction"
}
