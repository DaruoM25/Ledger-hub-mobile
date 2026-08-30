package com.ledgerhub.presentation.creditnoteform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.creditnote.CreditNoteReason
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.invoiceform.SubmissionStatus
import com.ledgerhub.presentation.invoices.formatMoney
import com.ledgerhub.presentation.theme.CreditNoteColors
import com.ledgerhub.presentation.theme.CreditNoteTheme

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object CreditNoteFormTags {
    const val SCREEN = "credit_note_form_screen"
    const val TOP_BAR = "credit_note_form_top_bar"
    const val TOP_BAR_TITLE = "credit_note_form_top_bar_title"
    const val BACK_BUTTON = "credit_note_form_back"
    const val DRAFT_BADGE = "credit_note_form_draft_badge"
    const val INVOICE_ID = "credit_note_form_invoice_id"
    const val CROSS_REFERENCE = "credit_note_form_cross_reference"
    const val RECIPIENT_BLOCK = "credit_note_form_recipient_block"
    const val CREDIT_NOTE_NUMBER = "credit_note_form_number"
    const val ISSUE_DATE = "credit_note_form_issue_date"
    const val REASON_SECTION = "credit_note_form_reason_section"
    const val REASON_FREE_TEXT = "credit_note_form_reason_free_text"

    /** Alias hérité — pointe sur le champ libre du motif (visible quand « Autre motif » est choisi). */
    const val REASON = REASON_FREE_TEXT

    const val LINES = "credit_note_form_lines"
    const val VAT_BREAKDOWN = "credit_note_form_vat_breakdown"
    const val TOTALS_CARTRIDGE = "credit_note_form_totals_cartridge"
    const val TOTAL_TTC = "credit_note_form_total_ttc"
    const val TOTAL_BREAKDOWN = "credit_note_form_total_breakdown"
    const val SUBMIT_BUTTON = "credit_note_form_submit_button"
    const val LOADING_INDICATOR = "credit_note_form_loading_indicator"
    const val SUCCESS_MESSAGE = "credit_note_form_success_message"
    const val ERROR_MESSAGE = "credit_note_form_error_message"
    const val BLOCKED_BANNER = "credit_note_form_blocked_banner"

    fun errorTagFor(field: CreditNoteFormField) = "credit_note_form_error_${field.name}"

    fun reasonChip(kind: CreditNoteReason) = "credit_note_form_reason_${kind.name}"
}

@Composable
fun CreditNoteFormScreen(
    viewModel: CreditNoteFormViewModel,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    CreditNoteFormContent(uiState = uiState, onIntent = viewModel::processIntent, onBack = onBack)
}

@Composable
internal fun CreditNoteFormContent(
    uiState: CreditNoteFormUiState,
    onIntent: (CreditNoteFormIntent) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val fieldsEnabled = uiState.isFormEnabled
    // Formatage monétaire partagé avec le reste de l'application (Sprint 2 US-02).
    val lang = LocalAppLanguage.current

    CreditNoteTheme {
        Scaffold(
            containerColor = CreditNoteColors.Background,
            topBar = { CreditNoteTopBar(onBack = onBack) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = CreditNoteFormTags.SCREEN }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DraftBadge()

                // Émission refusée d'emblée : inutile de laisser saisir un motif pour échouer ensuite.
                uiState.blockedByExistingCreditNote?.let { existing ->
                    StatusBanner(
                        text = "Cette facture a déjà été annulée par l'avoir $existing",
                        tag = CreditNoteFormTags.BLOCKED_BANNER,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                ReferenceCard(uiState = uiState)

                CreditNoteCard {
                    FormField(
                        label = "Date d'émission (AAAA-MM-JJ)",
                        value = uiState.issueDate,
                        tag = CreditNoteFormTags.ISSUE_DATE,
                        error = uiState.visibleErrors[CreditNoteFormField.ISSUE_DATE],
                        errorTag = CreditNoteFormTags.errorTagFor(CreditNoteFormField.ISSUE_DATE),
                        enabled = fieldsEnabled,
                        onValueChange = { onIntent(CreditNoteFormIntent.IssueDateChanged(it)) },
                    )
                }

                ReasonCard(uiState = uiState, enabled = fieldsEnabled, onIntent = onIntent)

                CreditNoteCard {
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
                }

                TotalsCartridge(uiState = uiState, lang = lang)

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { testTag = CreditNoteFormTags.SUBMIT_BUTTON },
                    colors = ButtonDefaults.buttonColors(containerColor = CreditNoteColors.Accent),
                ) {
                    Text("Valider l'avoir")
                }
            }
        }
    }
}

@Composable
private fun CreditNoteTopBar(onBack: () -> Unit) {
    Surface(
        color = CreditNoteColors.HeaderBar,
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = CreditNoteFormTags.TOP_BAR },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { testTag = CreditNoteFormTags.BACK_BUTTON },
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text(
                "Nouvel avoir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = CreditNoteFormTags.TOP_BAR_TITLE },
            )
        }
    }
}

@Composable
private fun DraftBadge() {
    Surface(
        color = CreditNoteColors.BadgeBg,
        contentColor = CreditNoteColors.BadgeFg,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.semantics {
            testTag = CreditNoteFormTags.DRAFT_BADGE
            contentDescription = "AVOIR EN BROUILLON"
        },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(CreditNoteColors.BadgeFg),
            )
            Text(
                "AVOIR EN BROUILLON",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ReferenceCard(uiState: CreditNoteFormUiState) {
    CreditNoteCard {
        Text(
            "RÉFÉRENCE",
            style = MaterialTheme.typography.labelSmall,
            color = CreditNoteColors.SecondaryText,
        )
        // Référence croisée Factur-X 2026 : le couple numéro + date de la facture annulée.
        Text(
            text = "Annule la facture ${uiState.invoiceId} du ${uiState.originalInvoiceDate}",
            modifier = Modifier.semantics {
                testTag = CreditNoteFormTags.INVOICE_ID
                contentDescription = CreditNoteFormTags.CROSS_REFERENCE
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Émetteur : ${uiState.issuerName}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Destinataire : ${uiState.recipientName}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.RECIPIENT_BLOCK },
        )
        // Numéro attribué par la séquence, jamais saisi : la continuité est une obligation fiscale.
        Text(
            text = "Numéro d'avoir : ${uiState.creditNoteNumber.ifBlank { "…" }}",
            modifier = Modifier.semantics { testTag = CreditNoteFormTags.CREDIT_NOTE_NUMBER },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ReasonCard(
    uiState: CreditNoteFormUiState,
    enabled: Boolean,
    onIntent: (CreditNoteFormIntent) -> Unit,
) {
    CreditNoteCard {
        Column(modifier = Modifier.semantics { testTag = CreditNoteFormTags.REASON_SECTION }) {
            SectionTitle("Raison légale de l'avoir *")
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (CreditNoteReason.PRESETS + CreditNoteReason.OTHER).forEach { kind ->
                    val selected = uiState.reasonKind == kind
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onIntent(CreditNoteFormIntent.ReasonKindChanged(kind)) },
                            )
                            .semantics { testTag = CreditNoteFormTags.reasonChip(kind) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = enabled)
                        Text(
                            if (kind == CreditNoteReason.OTHER) "Autre motif…" else kind.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (uiState.reasonKind == CreditNoteReason.OTHER) {
                FormField(
                    label = "Préciser le motif",
                    value = uiState.reasonFreeText,
                    tag = CreditNoteFormTags.REASON_FREE_TEXT,
                    error = null,
                    errorTag = "${CreditNoteFormTags.REASON_FREE_TEXT}_error",
                    enabled = enabled,
                    onValueChange = { onIntent(CreditNoteFormIntent.ReasonFreeTextChanged(it)) },
                )
            }

            uiState.visibleErrors[CreditNoteFormField.REASON]?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics {
                        testTag = CreditNoteFormTags.errorTagFor(CreditNoteFormField.REASON)
                        contentDescription = error
                    },
                )
            }
        }
    }
}

@Composable
private fun TotalsCartridge(uiState: CreditNoteFormUiState, lang: com.ledgerhub.domain.i18n.AppLanguage) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = CreditNoteFormTags.TOTALS_CARTRIDGE },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CreditNoteColors.CartridgeBg),
        border = BorderStroke(1.dp, CreditNoteColors.CartridgeBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "MONTANT TOTAL À DÉDUIRE / REMBOURSER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = CreditNoteColors.SecondaryText,
            )
            Text(
                text = formatMoney(uiState.totalTtc.cents, lang),
                modifier = Modifier.semantics { testTag = CreditNoteFormTags.TOTAL_TTC },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CreditNoteColors.CreditNegative,
            )
            Text(
                text = "HT ${formatMoney(uiState.totalHt.cents, lang)}  ·  TVA ${formatMoney(uiState.totalVat.cents, lang)}",
                modifier = Modifier.semantics { testTag = CreditNoteFormTags.TOTAL_BREAKDOWN },
                style = MaterialTheme.typography.bodyMedium,
                color = CreditNoteColors.SecondaryText,
            )
        }
    }
}

@Composable
private fun CreditNoteCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CreditNoteColors.Surface),
        border = BorderStroke(1.dp, CreditNoteColors.CardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
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
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(8.dp)) {
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
