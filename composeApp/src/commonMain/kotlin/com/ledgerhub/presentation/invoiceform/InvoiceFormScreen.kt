package com.ledgerhub.presentation.invoiceform

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.format
import com.ledgerhub.presentation.invoices.formatCentsGrouped
import com.ledgerhub.presentation.theme.LedgerHubColors

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest / Robolectric). */
object InvoiceFormTags {
    const val SCREEN = "invoice_form_screen"
    const val INVOICE_NUMBER = "invoice_form_number"
    const val ISSUE_DATE = "invoice_form_issue_date"
    const val DUE_DATE = "invoice_form_due_date"
    const val CLIENT_NAME = "invoice_form_client_name"
    const val CLIENT_SIRET = "invoice_form_client_siret"
    const val CLIENT_EMAIL = "invoice_form_client_email"
    const val ADD_LINE_BUTTON = "invoice_form_add_line_button"
    const val TOTAL_HT = "invoice_form_total_ht"
    const val TOTAL_VAT = "invoice_form_total_vat"
    const val TOTAL_TTC = "invoice_form_total_ttc"
    const val FACTURX_TOGGLE = "invoice_form_facturx_toggle"
    const val SUBMIT_BUTTON = "invoice_form_submit_button"
    const val LOADING_INDICATOR = "invoice_form_loading_indicator"
    const val SUCCESS_MESSAGE = "invoice_form_success_message"
    const val ERROR_MESSAGE = "invoice_form_error_message"
    const val PREVIEW_MODE_TOGGLE = "invoice_form_preview_mode_toggle"

    fun errorTagFor(field: InvoiceFormField) = "invoice_form_error_${field.name}"

    fun lineLabelTag(index: Int) = "invoice_form_line_${index}_label"
    fun lineQuantityTag(index: Int) = "invoice_form_line_${index}_quantity"
    fun lineUnitPriceTag(index: Int) = "invoice_form_line_${index}_unit_price"
    fun lineVatRateFieldTag(index: Int) = "invoice_form_line_${index}_vat_rate_field"
    fun lineVatRateTag(index: Int, rate: VatRate) = "invoice_form_line_${index}_vat_rate_${rate.name}"
    fun lineRemoveButtonTag(index: Int) = "invoice_form_line_${index}_remove_button"
    fun lineErrorTag(index: Int, field: InvoiceLineField) = "invoice_form_line_${index}_error_${field.name}"
}

@Composable
fun InvoiceFormScreen(
    viewModel: InvoiceFormViewModel = remember { InvoiceFormViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()

    // Bascule purement visuelle — n'affecte ni l'état ni la validation (même InvoiceFormUiState pour les deux vues).
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
        modifier = Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 0.dp),
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
    val enabled = uiState.isFormEnabled
    val lang = LocalAppLanguage.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = InvoiceFormTags.SCREEN }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(StringKey.FORM_TITLE), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                tr(StringKey.FORM_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = tr(StringKey.FORM_SECTION_CLIENT), glyph = "🏢") {
            FormField(
                label = tr(StringKey.FIELD_CLIENT_NAME),
                value = uiState.clientName,
                tag = InvoiceFormTags.CLIENT_NAME,
                error = uiState.visibleErrors[InvoiceFormField.CLIENT_NAME]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_NAME),
                enabled = enabled,
                onValueChange = { onIntent(InvoiceFormIntent.ClientNameChanged(it)) },
            )
            FormField(
                label = tr(StringKey.FIELD_CLIENT_SIRET),
                value = uiState.clientSiret,
                tag = InvoiceFormTags.CLIENT_SIRET,
                error = uiState.visibleErrors[InvoiceFormField.CLIENT_SIRET]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_SIRET),
                enabled = enabled,
                onValueChange = { onIntent(InvoiceFormIntent.ClientSiretChanged(it)) },
                keyboardType = KeyboardType.Number,
                inputFilter = ::filterSiret,
            )
            FormField(
                label = tr(StringKey.FIELD_CLIENT_EMAIL),
                value = uiState.clientEmail,
                tag = InvoiceFormTags.CLIENT_EMAIL,
                error = uiState.visibleErrors[InvoiceFormField.CLIENT_EMAIL]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_EMAIL),
                enabled = enabled,
                onValueChange = { onIntent(InvoiceFormIntent.ClientEmailChanged(it)) },
                keyboardType = KeyboardType.Email,
            )
        }

        SectionCard(title = tr(StringKey.FORM_SECTION_DETAILS), glyph = "📄") {
            FormField(
                label = tr(StringKey.FIELD_INVOICE_NUMBER),
                value = uiState.invoiceNumber,
                tag = InvoiceFormTags.INVOICE_NUMBER,
                error = uiState.visibleErrors[InvoiceFormField.INVOICE_NUMBER]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.INVOICE_NUMBER),
                enabled = enabled,
                onValueChange = { onIntent(InvoiceFormIntent.InvoiceNumberChanged(it)) },
            )
            FormField(
                label = tr(StringKey.FIELD_ISSUE_DATE),
                value = uiState.issueDate,
                tag = InvoiceFormTags.ISSUE_DATE,
                error = uiState.visibleErrors[InvoiceFormField.ISSUE_DATE]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUE_DATE),
                enabled = enabled,
                onValueChange = { onIntent(InvoiceFormIntent.IssueDateChanged(it)) },
            )
            FormField(
                label = tr(StringKey.FIELD_DUE_DATE),
                value = uiState.dueDate,
                tag = InvoiceFormTags.DUE_DATE,
                error = uiState.visibleErrors[InvoiceFormField.DUE_DATE]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.DUE_DATE),
                enabled = enabled,
                onValueChange = { onIntent(InvoiceFormIntent.DueDateChanged(it)) },
            )
        }

        SectionCard(title = tr(StringKey.FORM_SECTION_LINES), glyph = "🧾") {
            uiState.lines.forEachIndexed { index, line ->
                InvoiceLineForm(
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
                onClick = { onIntent(InvoiceFormIntent.AddLine) },
                enabled = enabled,
                modifier = Modifier.semantics { testTag = InvoiceFormTags.ADD_LINE_BUTTON },
            ) {
                Text(tr(StringKey.ACTION_ADD_LINE))
            }
        }

        SectionCard(title = tr(StringKey.FORM_SECTION_RECAP)) {
            RecapRow(tr(StringKey.RECAP_TOTAL_HT), uiState.totalHt.format(lang), InvoiceFormTags.TOTAL_HT)
            RecapRow(tr(StringKey.RECAP_TOTAL_VAT), uiState.totalVat.format(lang), InvoiceFormTags.TOTAL_VAT)
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(tr(StringKey.RECAP_TOTAL_TTC), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = uiState.totalTtc.format(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { testTag = InvoiceFormTags.TOTAL_TTC },
                )
            }
        }

        FacturXToggle(
            enabled = enabled,
            checked = uiState.generateFacturX,
            onToggle = { onIntent(InvoiceFormIntent.ToggleFacturX(it)) },
        )

        when (val status = uiState.submissionStatus) {
            SubmissionStatus.Loading -> LoadingIndicator()
            SubmissionStatus.Success -> StatusBanner(
                text = tr(StringKey.TOAST_INVOICE_SUCCESS),
                tag = InvoiceFormTags.SUCCESS_MESSAGE,
                containerColor = LedgerHubColors.StatusPaidBg,
                contentColor = LedgerHubColors.StatusPaidFg,
            )
            is SubmissionStatus.Error -> StatusBanner(
                text = "${tr(StringKey.TOAST_INVOICE_SUBMIT_FAILED_PREFIX)}${status.message}",
                tag = InvoiceFormTags.ERROR_MESSAGE,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            SubmissionStatus.Idle -> Unit
        }

        Button(
            onClick = { onIntent(InvoiceFormIntent.Submit) },
            enabled = uiState.isSubmitEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().semantics { testTag = InvoiceFormTags.SUBMIT_BUTTON },
        ) {
            Text("☁  ${tr(StringKey.ACTION_SUBMIT_INVOICE)}")
        }

        Text(
            tr(StringKey.FORM_ARCHIVE_NOTICE),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InvoiceLineForm(
    index: Int,
    line: InvoiceLineFormState,
    enabled: Boolean,
    canRemove: Boolean,
    revealAllErrors: Boolean,
    onIntent: (InvoiceFormIntent) -> Unit,
) {
    val visibleErrors = line.visibleErrors(revealAllErrors)

    fun update(
        label: String = line.label,
        quantity: String = line.quantity,
        unitPriceHt: String = line.unitPriceHt,
        vatRate: VatRate = line.vatRate,
    ) {
        onIntent(InvoiceFormIntent.UpdateLine(index, label, quantity, unitPriceHt, vatRate))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${tr(StringKey.LINE_HEADER)} ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = { onIntent(InvoiceFormIntent.RemoveLine(index)) },
                enabled = canRemove,
                modifier = Modifier.semantics { testTag = InvoiceFormTags.lineRemoveButtonTag(index) },
            ) {
                Text("🗑")
            }
        }
        FormField(
            label = tr(StringKey.FIELD_LINE_LABEL),
            value = line.label,
            tag = InvoiceFormTags.lineLabelTag(index),
            error = visibleErrors[InvoiceLineField.LABEL]?.let { tr(it.stringKey) },
            errorTag = InvoiceFormTags.lineErrorTag(index, InvoiceLineField.LABEL),
            enabled = enabled,
            onValueChange = { update(label = it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormField(
                    label = tr(StringKey.FIELD_LINE_QTY),
                    value = line.quantity,
                    tag = InvoiceFormTags.lineQuantityTag(index),
                    error = visibleErrors[InvoiceLineField.QUANTITY]?.let { tr(it.stringKey) },
                    errorTag = InvoiceFormTags.lineErrorTag(index, InvoiceLineField.QUANTITY),
                    enabled = enabled,
                    onValueChange = { update(quantity = it) },
                    keyboardType = KeyboardType.Number,
                    inputFilter = ::filterQuantity,
                )
            }
            Box(modifier = Modifier.weight(1.4f)) {
                FormField(
                    label = tr(StringKey.FIELD_LINE_UNIT_PRICE),
                    value = line.unitPriceHt,
                    tag = InvoiceFormTags.lineUnitPriceTag(index),
                    error = visibleErrors[InvoiceLineField.UNIT_PRICE]?.let { tr(it.stringKey) },
                    errorTag = InvoiceFormTags.lineErrorTag(index, InvoiceLineField.UNIT_PRICE),
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
        Text(tr(StringKey.FIELD_VAT_RATE), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.semantics { testTag = InvoiceFormTags.lineVatRateFieldTag(index) },
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
                        modifier = Modifier.semantics { testTag = InvoiceFormTags.lineVatRateTag(index, rate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FacturXToggle(enabled: Boolean, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        color = LedgerHubColors.StatusPaidBg.copy(alpha = 0.4f),
        contentColor = LedgerHubColors.StatusPaidFg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LedgerHubColors.StatusPaidFg.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr(StringKey.FACTURX_TOGGLE_LABEL),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled,
                modifier = Modifier.semantics { testTag = InvoiceFormTags.FACTURX_TOGGLE },
            )
        }
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
                if (glyph != null) Text(glyph)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

// ── Filtres de saisie ────────────────────────────────────────────────────────────
// Ils normalisent la frappe pour éviter les saisies structurellement impossibles ; la validation
// métier (FiscalValidation, parseAmountToCents) reste seule juge de la conformité.

/** SIRET : 14 chiffres exactement — on ne laisse entrer que des chiffres, et pas un de plus. */
private fun filterSiret(input: String): String = input.filter { it.isDigit() }.take(SIRET_LENGTH)

/** Quantité : entier positif, borné pour éviter les saisies aberrantes. */
private fun filterQuantity(input: String): String = input.filter { it.isDigit() }.take(QUANTITY_MAX_DIGITS)

/**
 * Montant : chiffres et un séparateur décimal unique. La virgule et le point sont acceptés
 * (claviers FR et EN), [parseAmountToCents] normalise ensuite.
 */
private fun filterAmount(input: String): String {
    val builder = StringBuilder()
    var separatorSeen = false
    for (char in input) {
        when {
            char.isDigit() -> builder.append(char)
            (char == ',' || char == '.') && !separatorSeen && builder.isNotEmpty() -> {
                separatorSeen = true
                builder.append(char)
            }
        }
    }
    return builder.toString()
}

private const val SIRET_LENGTH = 14
private const val QUANTITY_MAX_DIGITS = 6

/**
 * Champ de saisie du formulaire.
 *
 * @param keyboardType clavier présenté à l'utilisateur — numérique pour le SIRET et les quantités,
 *   décimal pour les montants, e-mail pour l'adresse du client.
 * @param inputFilter normalise la frappe avant de la remonter (chiffres seuls, longueur maximale…).
 *   Appliqué à la saisie, pas à la validation : celle-ci reste la seule autorité sur la conformité.
 */
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
    inputFilter: (String) -> String = { it },
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(inputFilter(it)) },
            isError = error != null,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LedgerHubColors.InputBackground,
                unfocusedContainerColor = LedgerHubColors.InputBackground,
                disabledContainerColor = LedgerHubColors.InputBackground,
                focusedBorderColor = LedgerHubColors.Accent,
                unfocusedBorderColor = LedgerHubColors.InputBorder,
                focusedTextColor = LedgerHubColors.PrimaryText,
                unfocusedTextColor = LedgerHubColors.PrimaryText,
                cursorColor = LedgerHubColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth().semantics { testTag = tag },
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
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.semantics { testTag = InvoiceFormTags.LOADING_INDICATOR },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(tr(StringKey.FORM_SENDING))
    }
}

/** Bannière de statut persistante et testable (au lieu d'un Snackbar éphémère non déterministe en test). */
@Composable
private fun StatusBanner(text: String, tag: String, containerColor: Color, contentColor: Color) {
    Surface(color = containerColor, contentColor = contentColor, shape = RoundedCornerShape(12.dp)) {
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
 * Formate des centimes en chaîne décimale simple (ex: 1250 -> "12.50"), sans dépendre de
 * NumberFormat (JVM-only). `internal` pour rester réutilisé par [InvoicePaperCanvas],
 * seconde représentation du même [InvoiceFormUiState].
 */
internal fun formatCents(cents: Long): String {
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "$whole.$fraction"
}
