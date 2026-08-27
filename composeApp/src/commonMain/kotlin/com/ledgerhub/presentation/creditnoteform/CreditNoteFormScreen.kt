package com.ledgerhub.presentation.creditnoteform

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object CreditNoteFormTags {
    const val SCREEN = "credit_note_form_screen"
    const val INVOICE_ID = "credit_note_form_invoice_id"
    const val CREDIT_NOTE_NUMBER = "credit_note_form_number"
    const val ISSUE_DATE = "credit_note_form_issue_date"
    const val REASON = "credit_note_form_reason"
    const val TOTAL_TTC = "credit_note_form_total_ttc"
    const val SUBMIT_BUTTON = "credit_note_form_submit_button"
    const val LOADING_INDICATOR = "credit_note_form_loading_indicator"
    const val SUCCESS_MESSAGE = "credit_note_form_success_message"
    const val ERROR_MESSAGE = "credit_note_form_error_message"

    fun errorTagFor(field: CreditNoteFormField) = "credit_note_form_error_${field.name}"
}

@Composable
fun CreditNoteFormScreen(viewModel: CreditNoteFormViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    CreditNoteFormContent(uiState = uiState, onIntent = viewModel::processIntent)
}

@Composable
internal fun CreditNoteFormContent(
    uiState: CreditNoteFormUiState,
    onIntent: (CreditNoteFormIntent) -> Unit = {},
) {
    val fieldsEnabled = uiState.isFormEnabled

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = CreditNoteFormTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Avoir")
        Text(
            text = "Annule la facture ${uiState.invoiceId}",
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.INVOICE_ID },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Émetteur : ${uiState.issuerName}")
        Text("Destinataire : ${uiState.recipientName}")

        FormField(
            label = "Numéro d'avoir",
            value = uiState.creditNoteNumber,
            tag = CreditNoteFormTags.CREDIT_NOTE_NUMBER,
            error = uiState.errors[CreditNoteFormField.CREDIT_NOTE_NUMBER],
            errorTag = CreditNoteFormTags.errorTagFor(CreditNoteFormField.CREDIT_NOTE_NUMBER),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(CreditNoteFormIntent.CreditNoteNumberChanged(it)) },
        )
        FormField(
            label = "Date d'émission (AAAA-MM-JJ)",
            value = uiState.issueDate,
            tag = CreditNoteFormTags.ISSUE_DATE,
            error = uiState.errors[CreditNoteFormField.ISSUE_DATE],
            errorTag = CreditNoteFormTags.errorTagFor(CreditNoteFormField.ISSUE_DATE),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(CreditNoteFormIntent.IssueDateChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Motif d'annulation")
        FormField(
            label = "Motif (obligatoire)",
            value = uiState.reason,
            tag = CreditNoteFormTags.REASON,
            error = uiState.errors[CreditNoteFormField.REASON],
            errorTag = CreditNoteFormTags.errorTagFor(CreditNoteFormField.REASON),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(CreditNoteFormIntent.ReasonChanged(it)) },
        )

        HorizontalDivider()
        SectionTitle("Montants (annulation — toujours négatifs)")
        Text(
            text = "TTC : ${formatCents(uiState.totalTtc.cents)} €",
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.TOTAL_TTC },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "HT : ${formatCents(uiState.totalHt.cents)} € — TVA : ${formatCents(uiState.totalVat.cents)} €",
            color = MaterialTheme.colorScheme.error,
        )

        when (val status = uiState.submissionStatus) {
            SubmissionStatus.Loading -> LoadingIndicator()
            SubmissionStatus.Success -> StatusBanner(
                text = "Avoir créé avec succès — la facture d'origine est annulée",
                tag = CreditNoteFormTags.SUCCESS_MESSAGE,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            is SubmissionStatus.Error -> StatusBanner(
                text = status.message,
                tag = CreditNoteFormTags.ERROR_MESSAGE,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            SubmissionStatus.Idle -> Unit
        }

        Button(
            onClick = { onIntent(CreditNoteFormIntent.Submit) },
            enabled = uiState.isSubmitEnabled,
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.SUBMIT_BUTTON },
        ) {
            Text("Valider l'avoir")
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
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.semantics { testTag = CreditNoteFormTags.LOADING_INDICATOR },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text("Envoi en cours…")
    }
}

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

/** Formate des centimes (positifs ou négatifs) en chaîne décimale (ex: -1250 -> "-12.50"). */
private fun formatCents(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absCents = kotlin.math.abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    return "$sign$whole.$fraction"
}
