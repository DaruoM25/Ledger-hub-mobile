package com.ledgerhub.presentation.invoiceform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.NatureOperation
import com.ledgerhub.domain.invoice.TransactionMode
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.compliance.CompliancePanel
import com.ledgerhub.presentation.components.ClientPicker
import com.ledgerhub.presentation.components.ClientPickerTags
import com.ledgerhub.presentation.components.QuickClientDialog
import com.ledgerhub.presentation.components.filterAmount
import com.ledgerhub.presentation.components.filterQuantity
import com.ledgerhub.presentation.components.filterSiret
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.formatIsoDate
import com.ledgerhub.presentation.i18n.isoDateToMillis
import com.ledgerhub.presentation.i18n.millisToIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.format
import com.ledgerhub.presentation.invoices.formatCentsGrouped
import com.ledgerhub.presentation.theme.LedgerHubTheme

/** Icône calendrier vectorielle Material 3 (24x24 dp). */
private val CalendarVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CalendarToday",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
        ) {
            moveTo(19f, 3f)
            lineTo(18f, 3f)
            lineTo(18f, 1f)
            lineTo(16f, 1f)
            lineTo(16f, 3f)
            lineTo(8f, 3f)
            lineTo(8f, 1f)
            lineTo(6f, 1f)
            lineTo(6f, 3f)
            lineTo(5f, 3f)
            curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            lineTo(19f, 21f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            lineTo(21f, 5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(19f, 19f)
            lineTo(5f, 19f)
            lineTo(5f, 8f)
            lineTo(19f, 8f)
            lineTo(19f, 19f)
            close()
        }
    }.build()
}

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest / Robolectric). */
object InvoiceFormTags {
    const val SCREEN = "invoice_form_screen"
    const val INVOICE_NUMBER = "invoice_form_number"
    const val ISSUE_DATE = "invoice_form_issue_date"
    const val DUE_DATE = "invoice_form_due_date"
    /**
     * Champ raison sociale — devenu le champ de recherche du sélecteur client (US-11).
     * Alias de [ClientPickerTags.CLIENT_SEARCH_INPUT] : les tests antérieurs continuent de
     * désigner le même nœud.
     */
    const val CLIENT_NAME = ClientPickerTags.CLIENT_SEARCH_INPUT

    const val CLIENT_SIRET = "invoice_form_client_siret"
    const val CLIENT_EMAIL = "invoice_form_client_email"
    const val ADD_LINE_BUTTON = "invoice_form_add_line_button"
    const val TOTAL_HT = "invoice_form_total_ht"
    const val TOTAL_VAT = "invoice_form_total_vat"
    const val TOTAL_TTC = "invoice_form_total_ttc"
    const val FACTURX_TOGGLE = "invoice_form_facturx_toggle"
    const val SAVE_DRAFT_BUTTON = "invoice_form_save_draft_button"
    const val SUBMIT_BUTTON = "invoice_form_submit_button"
    const val LOADING_INDICATOR = "invoice_form_loading_indicator"
    const val SUCCESS_MESSAGE = "invoice_form_success_message"
    const val ERROR_MESSAGE = "invoice_form_error_message"

    // ── Tags Réforme 2026 (US-27) ──────────────────────────────────────────
    const val TRANSACTION_MODE_SELECTOR = "invoice_form_transaction_mode_selector"
    const val TRANSACTION_MODE_B2B = "invoice_form_transaction_mode_b2b"
    const val TRANSACTION_MODE_EREPORTING = "invoice_form_transaction_mode_ereporting"
    const val NATURE_OPERATION_SELECTOR = "invoice_form_nature_operation_selector"
    fun natureOperationTag(nature: NatureOperation) = "invoice_form_nature_operation_${nature.name}"
    const val OPTION_TVA_DEBIT_SWITCH = "invoice_form_option_tva_debit_switch"
    const val DIFFERENT_DELIVERY_ADDRESS_CHECKBOX = "invoice_form_delivery_address_checkbox"
    const val DELIVERY_ADDRESS_SECTION = "invoice_form_delivery_address_section"
    const val DELIVERY_STREET = "invoice_form_delivery_street"
    const val DELIVERY_ZIP = "invoice_form_delivery_zip"
    const val DELIVERY_CITY = "invoice_form_delivery_city"
    const val DELIVERY_COUNTRY = "invoice_form_delivery_country"

    /** Sélecteur de mode de saisie (US-15) — voir [InvoiceFormMode]. */
    const val MODE_SELECTOR = "invoice_form_mode_selector"

    /**
     * Pénalités de retard B2B (US-16). Ces deux tags sont **partagés par les deux modes de
     * saisie** : `InvoiceFormScreen` n'en compose jamais qu'un à la fois (voir le `when (mode)`),
     * donc aucun nœud n'est ambigu, et une même suite de tests couvre les deux représentations.
     */
    const val B2B_PENALTIES_CHECKBOX = "invoice_b2b_penalties_checkbox"

    /** Pied de page légal : mention L.441-10 ou formule de courtoisie, jamais vide. */
    const val LEGAL_FOOTER = "invoice_legal_footer"

    /**
     * Tag d'un segment du sélecteur de mode.
     *
     * `when` exhaustif et non interpolation : le segment « Mode Page Blanche » porte le tag
     * **imposé par le cahier des charges US-23** (`invoice_mode_canvas_btn`), et un troisième mode
     * devrait déclarer le sien pour que le code compile. Le segment « Mode Formulaire » garde, lui,
     * la valeur héritée de l'US-15 : aucune suite n'avait de raison d'en changer.
     */
    fun modeSegmentTag(mode: InvoiceFormMode) = when (mode) {
        InvoiceFormMode.CLASSIC -> "invoice_form_mode_segment_CLASSIC"
        InvoiceFormMode.BLANK_PAGE -> InvoiceCanvasTags.MODE_BUTTON
    }

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
    viewModel: InvoiceFormViewModel = remember { InvoiceFormViewModel() },
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.submissionStatus is SubmissionStatus.Success) {
        LaunchedEffect(Unit) {
            delay(1200L)
            onNavigateBack()
        }
    }

    // Le mode est un état DE VUE, jamais un champ de l'InvoiceFormUiState : basculer change la
    // représentation, pas la facture. rememberSaveable pour survivre à une rotation d'écran —
    // repartir en mode Formulaire après avoir tourné le téléphone serait vécu comme une perte.
    var mode by rememberSaveable { mutableStateOf(InvoiceFormMode.CLASSIC) }

    Column(modifier = Modifier.fillMaxWidth()) {
        InvoiceFormModeSelector(selected = mode, onModeSelected = { mode = it })
        when (mode) {
            InvoiceFormMode.CLASSIC -> InvoiceFormContent(
                uiState = uiState,
                onIntent = viewModel::processIntent,
                onNavigateBack = onNavigateBack,
            )
            InvoiceFormMode.BLANK_PAGE -> InvoicePaperCanvas(uiState = uiState, onIntent = viewModel::processIntent)
        }
    }
}

/**
 * Sélecteur « Mode Formulaire / Mode Page Blanche » en tête d'écran (US-15). Choix unique
 * exclusif : un `SingleChoiceSegmentedButtonRow` M3, dont la sémantique de groupe radio est
 * annoncée telle quelle aux lecteurs d'écran — ce qu'un bouton bascule ne fait pas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceFormModeSelector(
    selected: InvoiceFormMode,
    onModeSelected: (InvoiceFormMode) -> Unit,
) {
    val modes = InvoiceFormMode.entries
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp, 16.dp, 20.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = tr(StringKey.FORM_MODE_SELECTOR_LABEL),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = InvoiceFormTags.MODE_SELECTOR },
        ) {
            modes.forEachIndexed { index, mode ->
                val label = tr(mode.labelKey)
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onModeSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    modifier = Modifier.semantics {
                        testTag = InvoiceFormTags.modeSegmentTag(mode)
                        contentDescription = label
                    },
                ) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
internal fun InvoiceFormContent(
    uiState: InvoiceFormUiState,
    onIntent: (InvoiceFormIntent) -> Unit = {},
    onNavigateBack: () -> Unit = {},
) {
    val enabled = uiState.isFormEnabled
    val lang = LocalAppLanguage.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
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

        // Commutateur de Transaction (US-27)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = InvoiceFormTags.TRANSACTION_MODE_SELECTOR },
        ) {
            val isB2b = uiState.transactionMode == TransactionMode.E_INVOICING
            SegmentedButton(
                selected = isB2b,
                onClick = { onIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_INVOICING)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.semantics { testTag = InvoiceFormTags.TRANSACTION_MODE_B2B },
            ) {
                Text(tr(StringKey.TRANSACTION_MODE_B2B))
            }
            SegmentedButton(
                selected = !isB2b,
                onClick = { onIntent(InvoiceFormIntent.TransactionModeChanged(TransactionMode.E_REPORTING)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.semantics { testTag = InvoiceFormTags.TRANSACTION_MODE_EREPORTING },
            ) {
                Text(tr(StringKey.TRANSACTION_MODE_EREPORTING))
            }
        }

        SectionCard(title = tr(StringKey.FORM_SECTION_CLIENT), glyph = "🏢") {
            // Sélecteur dynamique (US-11) : la frappe filtre l'annuaire, la sélection remplit
            // SIRET et email, une saisie inconnue propose la création rapide.
            ClientPicker(
                label = tr(StringKey.FIELD_CLIENT_NAME),
                query = uiState.clientQuery,
                suggestions = uiState.clientSuggestions,
                isExpanded = uiState.isClientDropdownExpanded,
                showAddNewClientButton = uiState.showAddNewClientButton,
                enabled = enabled,
                error = uiState.visibleErrors[InvoiceFormField.CLIENT_NAME]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_NAME),
                onQueryChanged = { onIntent(InvoiceFormIntent.OnClientQueryChanged(it)) },
                onClientSelected = { onIntent(InvoiceFormIntent.OnClientSelected(it)) },
                onAddNewClient = { onIntent(InvoiceFormIntent.OnOpenQuickClientDialog) },
            )
            val siretLabel = if (uiState.transactionMode == TransactionMode.E_INVOICING) {
                "${tr(StringKey.FIELD_CLIENT_SIRET)} *"
            } else {
                tr(StringKey.FIELD_CLIENT_SIRET)
            }
            FormField(
                label = siretLabel,
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
            DatePickerFormField(
                label = tr(StringKey.FIELD_ISSUE_DATE),
                isoValue = uiState.issueDate,
                tag = InvoiceFormTags.ISSUE_DATE,
                error = uiState.visibleErrors[InvoiceFormField.ISSUE_DATE]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.ISSUE_DATE),
                enabled = enabled,
                onDateSelected = { onIntent(InvoiceFormIntent.IssueDateChanged(it)) },
            )
            DatePickerFormField(
                label = tr(StringKey.FIELD_DUE_DATE),
                isoValue = uiState.dueDate,
                tag = InvoiceFormTags.DUE_DATE,
                error = uiState.visibleErrors[InvoiceFormField.DUE_DATE]?.let { tr(it.stringKey) },
                errorTag = InvoiceFormTags.errorTagFor(InvoiceFormField.DUE_DATE),
                enabled = enabled,
                onDateSelected = { onIntent(InvoiceFormIntent.DueDateChanged(it)) },
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

        SectionCard(title = tr(StringKey.FORM_SECTION_B2B), glyph = "\u2696\uFE0F") {
            B2bPenaltiesCheckbox(
                checked = uiState.applyB2bPenalties,
                enabled = enabled,
                onToggle = { onIntent(InvoiceFormIntent.ToggleB2bPenalties(it)) },
            )
            LegalFooterText(applyB2bPenalties = uiState.applyB2bPenalties)
        }

        // ── Fiscalité & Livraison Réforme 2026 (US-27) ─────────────────────────
        SectionCard(title = "Conformité Fiscale 2026", glyph = "⚖️") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr(StringKey.NATURE_OPERATION_LABEL),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().semantics { testTag = InvoiceFormTags.NATURE_OPERATION_SELECTOR },
                ) {
                    val natures = NatureOperation.entries
                    natures.forEachIndexed { index, nature ->
                        val label = when (nature) {
                            NatureOperation.LIVRAISON_BIENS -> tr(StringKey.NATURE_OPERATION_GOODS)
                            NatureOperation.PRESTATION_SERVICES -> tr(StringKey.NATURE_OPERATION_SERVICES)
                            NatureOperation.MIXTE -> tr(StringKey.NATURE_OPERATION_MIXED)
                        }
                        SegmentedButton(
                            selected = uiState.natureOperation == nature,
                            onClick = { onIntent(InvoiceFormIntent.NatureOperationChanged(nature)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = natures.size),
                            modifier = Modifier.semantics { testTag = InvoiceFormTags.natureOperationTag(nature) },
                        ) {
                            Text(label, maxLines = 1)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = tr(StringKey.OPTION_TVA_DEBIT_LABEL),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = tr(StringKey.OPTION_TVA_DEBIT_DESC),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.optionTvaDebit,
                    onCheckedChange = { onIntent(InvoiceFormIntent.ToggleOptionTvaDebit(it)) },
                    enabled = enabled,
                    modifier = Modifier.semantics { testTag = InvoiceFormTags.OPTION_TVA_DEBIT_SWITCH },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = uiState.hasDifferentDeliveryAddress,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onIntent(InvoiceFormIntent.ToggleDifferentDeliveryAddress(it)) },
                    )
                    .padding(vertical = 4.dp)
                    .semantics { testTag = InvoiceFormTags.DIFFERENT_DELIVERY_ADDRESS_CHECKBOX },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = uiState.hasDifferentDeliveryAddress,
                    onCheckedChange = null,
                    enabled = enabled,
                )
                Text(
                    text = tr(StringKey.DIFFERENT_DELIVERY_ADDRESS_CHECKBOX),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            AnimatedVisibility(
                visible = uiState.hasDifferentDeliveryAddress,
                modifier = Modifier.semantics { testTag = InvoiceFormTags.DELIVERY_ADDRESS_SECTION },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = tr(StringKey.DELIVERY_ADDRESS_SECTION_TITLE),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FormField(
                        label = tr(StringKey.FIELD_DELIVERY_STREET),
                        value = uiState.deliveryStreet,
                        tag = InvoiceFormTags.DELIVERY_STREET,
                        enabled = enabled,
                        onValueChange = { onIntent(InvoiceFormIntent.DeliveryStreetChanged(it)) },
                    )
                    FormField(
                        label = tr(StringKey.FIELD_DELIVERY_ZIP),
                        value = uiState.deliveryZip,
                        tag = InvoiceFormTags.DELIVERY_ZIP,
                        enabled = enabled,
                        onValueChange = { onIntent(InvoiceFormIntent.DeliveryZipChanged(it)) },
                        keyboardType = KeyboardType.Number,
                    )
                    FormField(
                        label = tr(StringKey.FIELD_DELIVERY_CITY),
                        value = uiState.deliveryCity,
                        tag = InvoiceFormTags.DELIVERY_CITY,
                        enabled = enabled,
                        onValueChange = { onIntent(InvoiceFormIntent.DeliveryCityChanged(it)) },
                    )
                    FormField(
                        label = tr(StringKey.FIELD_DELIVERY_COUNTRY),
                        value = uiState.deliveryCountry,
                        tag = InvoiceFormTags.DELIVERY_COUNTRY,
                        enabled = enabled,
                        onValueChange = { onIntent(InvoiceFormIntent.DeliveryCountryChanged(it)) },
                    )
                }
            }
        }

        // Panneau d'audit (US-24) — après les mentions légales et avant les actions : un contrôle
        // de conformité conclut la saisie, il ne l'ouvre pas. Absent du mode canvas à dessein : la
        // feuille A4 est un document, et y poser un panneau de contrôle casserait l'illusion
        // papier que l'US-15 puis l'US-23 ont construite.
        CompliancePanel(
            report = uiState.complianceReport,
            enabled = enabled,
            onScan = { onIntent(InvoiceFormIntent.ComplianceScanRequested) },
        )

        if (uiState.networkState == com.ledgerhub.domain.degraded.DegradedModeNetworkState.OUTAGE) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF3D2706),
                    contentColor = Color(0xFFFDE68A),
                ),
                border = BorderStroke(1.dp, Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = com.ledgerhub.presentation.degraded.DegradedModeTags.DEGRADED_BANNER },
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚠️", style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr(StringKey.DEGRADED_MODE_NOTICE),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFDE68A),
                    )
                }
            }
        }

        when (val status = uiState.submissionStatus) {
            SubmissionStatus.Loading -> LoadingIndicator()
            SubmissionStatus.Success -> StatusBanner(
                text = tr(StringKey.TOAST_INVOICE_SUCCESS),
                tag = InvoiceFormTags.SUCCESS_MESSAGE,
                containerColor = LedgerHubTheme.palette.StatusPaidBg,
                contentColor = LedgerHubTheme.palette.StatusPaidFg,
            )
            is SubmissionStatus.Error -> StatusBanner(
                text = "${tr(StringKey.TOAST_INVOICE_SUBMIT_FAILED_PREFIX)}${status.message}",
                tag = InvoiceFormTags.ERROR_MESSAGE,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            SubmissionStatus.Idle -> Unit
        }

        // Les deux actions restent cliquables tant qu'aucune écriture n'est en cours : sur un
        // formulaire incomplet, l'appui révèle toutes les erreurs au lieu de griser sans explication.
        OutlinedButton(
            onClick = { onIntent(InvoiceFormIntent.SaveDraft) },
            enabled = !uiState.isSubmitting,
            modifier = Modifier.fillMaxWidth().semantics { testTag = InvoiceFormTags.SAVE_DRAFT_BUTTON },
        ) {
            // 💾 et non 🖫 (U+1F5AB) : ce dernier est absent des polices Android et rend un tofu.
            Text("💾  ${tr(StringKey.ACTION_SAVE_DRAFT)}")
        }

        val isDegraded = uiState.networkState == com.ledgerhub.domain.degraded.DegradedModeNetworkState.OUTAGE
        val submitButtonLabel = if (isDegraded) {
            tr(StringKey.DEGRADED_MODE_SUBMIT_ACTION)
        } else if (uiState.transactionMode == TransactionMode.E_INVOICING) {
            tr(StringKey.ACTION_SUBMIT_INVOICE_B2B)
        } else {
            tr(StringKey.ACTION_SUBMIT_INVOICE_EREPORTING)
        }
        val submitButtonTag = if (isDegraded) {
            com.ledgerhub.presentation.degraded.DegradedModeTags.DEGRADED_SUBMIT_BUTTON
        } else {
            InvoiceFormTags.SUBMIT_BUTTON
        }
        val submitButtonColor = if (isDegraded) Color(0xFFD97706) else MaterialTheme.colorScheme.primary

        Button(
            onClick = { onIntent(InvoiceFormIntent.ValidateAndIssue) },
            enabled = !uiState.isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = submitButtonColor),
            modifier = Modifier.fillMaxWidth().semantics { testTag = submitButtonTag },
        ) {
            Text(if (isDegraded) "⚠️  $submitButtonLabel" else "☁  $submitButtonLabel")
        }

        Text(
            tr(StringKey.FORM_ARCHIVE_NOTICE),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            isSireneResolving = quickClientDraft.isSireneResolving,
            onFieldChanged = { name, siret, email ->
                onIntent(InvoiceFormIntent.OnQuickClientFieldChanged(name, siret, email))
            },
            onSave = {
                onIntent(
                    InvoiceFormIntent.OnSaveQuickClient(
                        name = quickClientDraft.name,
                        siret = quickClientDraft.siret,
                        email = quickClientDraft.email,
                    ),
                )
            },
            onDismiss = { onIntent(InvoiceFormIntent.OnDismissQuickClientDialog) },
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
        color = LedgerHubTheme.palette.StatusPaidBg.copy(alpha = 0.4f),
        contentColor = LedgerHubTheme.palette.StatusPaidFg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.StatusPaidFg.copy(alpha = 0.4f)),
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
    error: String? = null,
    errorTag: String = "",
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
                focusedContainerColor = LedgerHubTheme.palette.InputBackground,
                unfocusedContainerColor = LedgerHubTheme.palette.InputBackground,
                disabledContainerColor = LedgerHubTheme.palette.InputBackground,
                focusedBorderColor = LedgerHubTheme.palette.Accent,
                unfocusedBorderColor = LedgerHubTheme.palette.InputBorder,
                focusedTextColor = LedgerHubTheme.palette.PrimaryText,
                unfocusedTextColor = LedgerHubTheme.palette.PrimaryText,
                cursorColor = LedgerHubTheme.palette.Accent,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerFormField(
    label: String,
    isoValue: String,
    tag: String,
    error: String? = null,
    errorTag: String = "",
    enabled: Boolean,
    onDateSelected: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val lang = LocalAppLanguage.current
    val displayValue = if (isoValue.isNotBlank()) formatIsoDate(isoValue, lang) else ""

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showDialog = true },
        ) {
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                isError = error != null,
                trailingIcon = {
                    IconButton(
                        onClick = { if (enabled) showDialog = true },
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = CalendarVectorIcon,
                            contentDescription = "Sélectionner la date",
                            tint = if (enabled) LedgerHubTheme.palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LedgerHubTheme.palette.InputBackground,
                    unfocusedContainerColor = LedgerHubTheme.palette.InputBackground,
                    disabledContainerColor = LedgerHubTheme.palette.InputBackground,
                    focusedBorderColor = LedgerHubTheme.palette.Accent,
                    unfocusedBorderColor = LedgerHubTheme.palette.InputBorder,
                    focusedTextColor = LedgerHubTheme.palette.PrimaryText,
                    unfocusedTextColor = LedgerHubTheme.palette.PrimaryText,
                    cursorColor = LedgerHubTheme.palette.Accent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = tag },
                singleLine = true,
            )
            // Overlay transparent garantissant le tap sur l'ensemble de la zone du champ
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = enabled) { showDialog = true },
            )
        }
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

    if (showDialog) {
        val initialMillis = isoDateToMillis(isoValue)
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val selectedIso = millisToIsoDate(selectedMillis)
                            onDateSelected(selectedIso)
                        }
                        showDialog = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Annuler")
                }
            },
        ) {
            DatePicker(state = datePickerState)
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
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(tr(StringKey.FORM_PROCESSING))
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
 * Case « Appliquer les pénalités de retard légales (B2B) » (US-16).
 *
 * Le `toggleable` porte la ligne entière plutôt que la seule case : le libellé d'une mention
 * légale est long, et l'obliger à viser un carré de 20 dp serait une cible tactile hostile. La
 * `Checkbox` reçoit donc `onCheckedChange = null` — sans quoi elle exposerait un second nœud
 * cochable concurrent de celui de la ligne.
 *
 * Désactivée dès que le formulaire l'est ([InvoiceFormUiState.isFormEnabled]) : sur une facture
 * déposée, les mentions légales sont figées au même titre que les montants.
 */
@Composable
internal fun B2bPenaltiesCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val label = tr(StringKey.B2B_PENALTIES_CHECKBOX)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onToggle,
            )
            .semantics {
                testTag = InvoiceFormTags.B2B_PENALTIES_CHECKBOX
                contentDescription = label
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * Pied de page légal de la facture (US-16) — mention de l'article L.441-10 du Code de commerce
 * quand les pénalités B2B s'appliquent, formule de courtoisie sinon.
 *
 * Le pied n'est **jamais vide** : les deux textes occupent le même emplacement, ce qui évite un
 * saut de mise en page à la bascule et donne au test N3a une alternance observable sur un nœud
 * unique. `contentDescription` recopie le texte pour le rendre assertable et lisible par TalkBack.
 */
@Composable
internal fun LegalFooterText(
    applyB2bPenalties: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val text = tr(
        if (applyB2bPenalties) StringKey.B2B_LEGAL_MENTION else StringKey.B2B_COURTESY_MENTION,
    )
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier.fillMaxWidth().semantics {
            testTag = InvoiceFormTags.LEGAL_FOOTER
            contentDescription = text
        },
    )
}
