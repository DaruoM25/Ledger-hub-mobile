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
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
import com.ledgerhub.presentation.invoices.formatMoney

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
    const val CROSS_REFERENCE = "credit_note_form_cross_reference"
    const val BLOCKED_BANNER = "credit_note_form_blocked_banner"
    const val LINES = "credit_note_form_lines"
    const val VAT_BREAKDOWN = "credit_note_form_vat_breakdown"

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
    // Formatage monétaire partagé avec le reste de l'application (Sprint 2 US-02) : l'écran
    // d'avoir avait jusqu'ici son propre formateur, qui ignorait la locale active.
    val lang = LocalAppLanguage.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = CreditNoteFormTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Avoir")

        // Émission refusée d'emblée : inutile de laisser saisir un motif pour échouer ensuite.
        uiState.blockedByExistingCreditNote?.let { existing ->
            StatusBanner(
                text = "Cette facture a déjà été annulée par l'avoir $existing",
                tag = CreditNoteFormTags.BLOCKED_BANNER,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        // Référence croisée Factur-X 2026 : le couple numéro + date de la facture annulée.
        Text(
            text = "Annule la facture ${uiState.invoiceId} du ${uiState.originalInvoiceDate}",
            modifier = Modifier.semantics {
                testTag = CreditNoteFormTags.INVOICE_ID
                contentDescription = CreditNoteFormTags.CROSS_REFERENCE
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Émetteur : ${uiState.issuerName}")
        Text("Destinataire : ${uiState.recipientName}")

        // Numéro attribué par la séquence, jamais saisi : la continuité est une obligation fiscale.
        Text(
            text = "Numéro d'avoir : ${uiState.creditNoteNumber.ifBlank { "…" }}",
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.CREDIT_NOTE_NUMBER },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        FormField(
            label = "Date d'émission (AAAA-MM-JJ)",
            value = uiState.issueDate,
            tag = CreditNoteFormTags.ISSUE_DATE,
            error = uiState.visibleErrors[CreditNoteFormField.ISSUE_DATE],
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
            error = uiState.visibleErrors[CreditNoteFormField.REASON],
            errorTag = CreditNoteFormTags.errorTagFor(CreditNoteFormField.REASON),
            enabled = fieldsEnabled,
            onValueChange = { onIntent(CreditNoteFormIntent.ReasonChanged(it)) },
        )

        HorizontalDivider()
        // Lignes recopiées de la facture annulée : l'avoir est une pièce autoportante.
        SectionTitle("Lignes annulées")
        Column(modifier = Modifier.semantics { testTag = CreditNoteFormTags.LINES }) {
            uiState.lines.forEach { line ->
                Text(
                    "${line.quantity} × ${line.label} — ${formatMoney(line.unitPriceHt.cents, lang)} HT (${line.vatRate.label})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        SectionTitle("Assiettes de TVA (au crédit)")
        Column(modifier = Modifier.semantics { testTag = CreditNoteFormTags.VAT_BREAKDOWN }) {
            uiState.vatBreakdown.forEach { breakdown ->
                Text(
                    "Base ${breakdown.rate.label} : ${formatMoney(breakdown.baseHt.cents, lang)} — TVA ${formatMoney(breakdown.vatAmount.cents, lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        HorizontalDivider()
        SectionTitle("Montants (annulation — toujours négatifs)")
        Text(
            text = "TTC : ${formatMoney(uiState.totalTtc.cents, lang)}",
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.TOTAL_TTC },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "HT : ${formatMoney(uiState.totalHt.cents, lang)} — TVA : ${formatMoney(uiState.totalVat.cents, lang)}",
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

