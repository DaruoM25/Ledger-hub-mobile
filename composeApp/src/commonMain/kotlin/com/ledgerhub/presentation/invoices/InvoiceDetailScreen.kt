package com.ledgerhub.presentation.invoices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.VatBreakdown
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.formatIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.components.FacturXBadge
import com.ledgerhub.presentation.invoices.components.InvoicePreviewDialog
import com.ledgerhub.presentation.invoices.components.StatusTag
import com.ledgerhub.presentation.invoices.format

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object InvoiceDetailScreenTags {
    const val SCREEN = "invoices_detail_screen"
    const val LOADING = "invoices_detail_loading"
    const val ERROR = "invoices_detail_error"
    const val RETRY_BUTTON = "invoices_detail_retry_button"
    const val NOT_FOUND = "invoices_detail_not_found"
    const val STATUS_TAG = "invoices_detail_status_tag"
    const val FACTURX_BADGE = "invoices_detail_facturx_badge"
    const val TOTAL_HT = "invoices_detail_total_ht"
    const val TOTAL_VAT = "invoices_detail_total_vat"
    const val TOTAL_TTC = "invoices_detail_total_ttc"
    const val EDIT_BUTTON = "invoices_detail_edit_button"
    const val LOCKED_HINT = "invoices_detail_locked_hint"
    const val CREDIT_NOTE_MENTION = "invoices_detail_credit_note_mention"
    const val PREVIEW_BUTTON = "invoices_detail_preview_button"
    const val EXPORT_INVOICE_XML_BUTTON = "invoices_detail_export_invoice_xml"
    const val EXPORT_CREDIT_NOTE_XML_BUTTON = "invoices_detail_export_credit_note_xml"
    const val LIFECYCLE_SECTION = "invoices_detail_lifecycle"
    const val AUDIT_TRAIL = "invoices_detail_audit_trail"
    const val AUDIT_TRAIL_EMPTY = "invoices_detail_audit_trail_empty"
    const val TRANSITION_DIALOG = "invoices_detail_transition_dialog"
    const val TRANSITION_REASON = "invoices_detail_transition_reason"
    const val TRANSITION_CONFIRM = "invoices_detail_transition_confirm"
    const val TRANSITION_ERROR = "invoices_detail_transition_error"

    fun transitionButton(target: InvoiceStatus) = "invoices_detail_transition_" + target.name
    fun auditEntry(id: String) = "invoices_detail_audit_entry_" + id
    const val CREDIT_NOTE_BUTTON = "invoices_detail_credit_note_button"
    const val LOCKED_BANNER = "invoices_detail_locked_banner"
    fun vatRow(rate: VatRate) = "invoices_detail_vat_row_${rate.name}"
}

/** Composable stateful — observe [InvoiceDetailViewModel], délègue les actions au parent. */
@Composable
fun InvoiceDetailScreen(
    viewModel: InvoiceDetailViewModel,
    onEditClick: (Invoice) -> Unit = {},
    onCreateCreditNoteClick: (Invoice) -> Unit = {},
    onExportInvoiceXml: (Invoice) -> Unit = {},
    onExportCreditNoteXml: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    InvoiceDetailView(
        uiState = uiState,
        onRetry = viewModel::retry,
        onEditClick = onEditClick,
        onCreateCreditNoteClick = onCreateCreditNoteClick,
        onExportInvoiceXml = onExportInvoiceXml,
        onExportCreditNoteXml = onExportCreditNoteXml,
        onStartTransition = viewModel::startTransition,
        onTransitionReasonChanged = viewModel::updateTransitionReason,
        onConfirmTransition = viewModel::confirmTransition,
        onCancelTransition = viewModel::cancelTransition,
    )
}

@Composable
internal fun InvoiceDetailView(
    uiState: InvoiceDetailUiState,
    onRetry: () -> Unit = {},
    onEditClick: (Invoice) -> Unit = {},
    onCreateCreditNoteClick: (Invoice) -> Unit = {},
    onExportInvoiceXml: (Invoice) -> Unit = {},
    onExportCreditNoteXml: (String) -> Unit = {},
    onStartTransition: (InvoiceStatus) -> Unit = {},
    onTransitionReasonChanged: (String) -> Unit = {},
    onConfirmTransition: () -> Unit = {},
    onCancelTransition: () -> Unit = {},
) {
    // Ouverture de l'aperçu A4 : état d'affichage pur, sans effet sur le domaine ni sur le
    // chargement. Le remonter au ViewModel n'ajouterait qu'un aller-retour d'intention pour une
    // feuille qui ne fait que relire la facture déjà en état.
    var previewedInvoice by remember { mutableStateOf<Invoice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = InvoiceDetailScreenTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val invoice = uiState.invoice
        when {
            uiState.isLoading -> LoadingState()
            uiState.errorMessage != null -> ErrorState(uiState.errorMessage, onRetry)
            uiState.notFound -> NotFoundState()
            invoice != null -> InvoiceBody(
                uiState = uiState,
                invoice = invoice,
                onEditClick = onEditClick,
                onCreateCreditNoteClick = onCreateCreditNoteClick,
                onExportInvoiceXml = onExportInvoiceXml,
                onExportCreditNoteXml = onExportCreditNoteXml,
                onStartTransition = onStartTransition,
                onPreviewClick = { previewedInvoice = it },
            )
        }
    }

    previewedInvoice?.let { invoiceToPreview ->
        InvoicePreviewDialog(
            invoice = invoiceToPreview,
            onDismiss = { previewedInvoice = null },
        )
    }

    uiState.pendingTransition?.let { target ->
        TransitionReasonDialog(
            uiState = uiState,
            target = target,
            onReasonChanged = onTransitionReasonChanged,
            onConfirm = onConfirmTransition,
            onDismiss = onCancelTransition,
        )
    }
}

@Composable
private fun InvoiceBody(
    uiState: InvoiceDetailUiState,
    invoice: Invoice,
    onEditClick: (Invoice) -> Unit,
    onCreateCreditNoteClick: (Invoice) -> Unit,
    onExportInvoiceXml: (Invoice) -> Unit,
    onExportCreditNoteXml: (String) -> Unit,
    onStartTransition: (InvoiceStatus) -> Unit,
    onPreviewClick: (Invoice) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            invoice.number,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        StatusTag(invoice.status, InvoiceDetailScreenTags.STATUS_TAG)
    }
    FacturXBadge(tag = InvoiceDetailScreenTags.FACTURX_BADGE)

    // Référence croisée vers l'avoir qui annule cette facture (US-05).
    uiState.creditNoteNumber?.let { creditNoteNumber ->
        val mention = "${tr(StringKey.INVOICE_CREDITED_BY)} $creditNoteNumber"
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.semantics {
                testTag = InvoiceDetailScreenTags.CREDIT_NOTE_MENTION
                contentDescription = mention
            },
        ) {
            Text(
                mention,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    val lang = LocalAppLanguage.current

    Text("Émetteur : ${invoice.issuer.name} — SIREN ${invoice.issuer.siren}")
    Text("Destinataire : ${invoice.recipient.name} — SIREN ${invoice.recipient.siren}")
    Text("${tr(StringKey.DETAIL_ISSUE_DATE)} : ${formatIsoDate(invoice.issueDate, lang)}")

    HorizontalDivider()

    Text(
        "Ventilation TVA",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    uiState.vatBreakdown.forEach { line -> VatRow(line) }

    HorizontalDivider()

    TotalRow("Total HT", invoice.totalHt.format(lang), InvoiceDetailScreenTags.TOTAL_HT)
    TotalRow("TVA", invoice.totalVat.format(lang), InvoiceDetailScreenTags.TOTAL_VAT)
    TotalRow(
        label = "Total TTC",
        value = invoice.totalTtc.format(lang),
        tag = InvoiceDetailScreenTags.TOTAL_TTC,
        emphasize = true,
    )

    HorizontalDivider()

    if (uiState.isLocked) {
        LockedBanner()
    }

    // Verrouillage des actions selon le statut fiscal : les boutons restent visibles
    // (repère pédagogique) mais sont désactivés hors des cas métier autorisés.
    // Une facture émise porte en plus un cadenas et une atténuation, pour que l'inaccessibilité
    // se lise comme une règle fiscale et non comme un défaut de l'application.
    val locked = uiState.isFiscallyLocked
    val editLabel = tr(StringKey.ACTION_EDIT_INVOICE)
    val lockedHint = tr(StringKey.INVOICE_LOCKED_HINT)
    Button(
        onClick = { onEditClick(invoice) },
        enabled = uiState.canEdit,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (locked) LOCKED_ACTION_ALPHA else 1f)
            .semantics {
                testTag = InvoiceDetailScreenTags.EDIT_BUTTON
                if (locked) contentDescription = lockedHint
            },
    ) {
        Text(if (locked) "🔒  $editLabel" else editLabel)
    }
    if (locked && !uiState.isLocked) {
        Text(
            lockedHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.LOCKED_HINT },
        )
    }
    OutlinedButton(
        onClick = { onCreateCreditNoteClick(invoice) },
        enabled = uiState.canCancelByCreditNote,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceDetailScreenTags.CREDIT_NOTE_BUTTON },
    ) {
        Text(tr(StringKey.ACTION_CANCEL_BY_CREDIT_NOTE))
    }

    HorizontalDivider()

    // Aperçu A4 (US-14). Disponible quel que soit le statut, annulation comprise : consulter
    // le document tel qu'il a été émis ne le modifie pas.
    OutlinedButton(
        onClick = { onPreviewClick(invoice) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceDetailScreenTags.PREVIEW_BUTTON },
    ) {
        Text("📄  ${tr(StringKey.ACTION_PREVIEW_INVOICE)}")
    }

    // Export Factur-X (US-06). Toujours disponible : une facture annulée reste une pièce
    // fiscale exportable — c'est précisément son archivage qui compte.
    OutlinedButton(
        onClick = { onExportInvoiceXml(invoice) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceDetailScreenTags.EXPORT_INVOICE_XML_BUTTON },
    ) {
        Text("⬇  ${tr(StringKey.ACTION_EXPORT_INVOICE_XML)}")
    }

    // L'avoir n'a pas d'écran propre : son export se déclenche depuis la facture parente,
    // à l'endroit même où sa mention de liaison est affichée (arbitrage PO de l'US-06).
    uiState.creditNoteNumber?.let { creditNoteNumber ->
        OutlinedButton(
            onClick = { onExportCreditNoteXml(creditNoteNumber) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = InvoiceDetailScreenTags.EXPORT_CREDIT_NOTE_XML_BUTTON },
        ) {
            Text("⬇  ${tr(StringKey.ACTION_EXPORT_CREDIT_NOTE_XML)} $creditNoteNumber")
        }
    }

    LifecycleSection(uiState = uiState, onStartTransition = onStartTransition)
    AuditTrailSection(entries = uiState.auditTrail)
}

/**
 * Actions de cycle de vie DGFIP. Les boutons sont **derives de la machine d'etats** : rien n'est
 * code en dur ici, et une transition retiree du referentiel disparait d'elle-meme de l'ecran.
 */
@Composable
private fun LifecycleSection(
    uiState: InvoiceDetailUiState,
    onStartTransition: (InvoiceStatus) -> Unit,
) {
    val transitions = uiState.availableTransitions
    if (transitions.isEmpty()) return

    HorizontalDivider()
    Text(
        tr(StringKey.LIFECYCLE_TITLE),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.LIFECYCLE_SECTION },
    )
    transitions.forEach { target ->
        Button(
            onClick = { onStartTransition(target) },
            enabled = !uiState.isTransitioning,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { testTag = InvoiceDetailScreenTags.transitionButton(target) },
        ) {
            Text(tr(target.actionKey()))
        }
    }
    uiState.transitionError?.let { message ->
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics {
                testTag = InvoiceDetailScreenTags.TRANSITION_ERROR
                contentDescription = message
            },
        )
    }
}

/** Libelle de l'action menant a ce statut : c'est le geste qui est nomme, pas l'etat d'arrivee. */
private fun InvoiceStatus.actionKey(): StringKey = when (this) {
    InvoiceStatus.DEPOSITED -> StringKey.ACTION_MARK_DEPOSITED
    InvoiceStatus.APPROVED -> StringKey.ACTION_MARK_APPROVED
    InvoiceStatus.PAID -> StringKey.ACTION_MARK_PAID
    InvoiceStatus.REJECTED -> StringKey.ACTION_MARK_REJECTED
    InvoiceStatus.REFUSED -> StringKey.ACTION_MARK_REFUSED
    InvoiceStatus.DRAFT -> StringKey.ACTION_REOPEN_DRAFT
    // CANCELLED n'est jamais propose a l'utilisateur (voir userActionableFrom).
    InvoiceStatus.CANCELLED -> StringKey.STATUS_CANCELLED
}

/** Chronologie de la Piste d'Audit Fiable, de la plus ancienne transition a la plus recente. */
@Composable
private fun AuditTrailSection(entries: List<AuditEntry>) {
    HorizontalDivider()
    Text(
        tr(StringKey.AUDIT_TRAIL_TITLE),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    if (entries.isEmpty()) {
        Text(
            tr(StringKey.AUDIT_TRAIL_EMPTY),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.AUDIT_TRAIL_EMPTY },
        )
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.AUDIT_TRAIL },
    ) {
        entries.forEach { entry ->
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.auditEntry(entry.id) },
            ) {
                val from = entry.fromStatus?.displayLabel() ?: "—"
                Text(
                    from + "  →  " + entry.toStatus.displayLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    entry.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.reason?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Saisie du motif. Obligatoire sur les transitions negatives, libre ailleurs. */
@Composable
private fun TransitionReasonDialog(
    uiState: InvoiceDetailUiState,
    target: InvoiceStatus,
    onReasonChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.TRANSITION_DIALOG },
        title = { Text(tr(target.actionKey())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.transitionReason,
                    onValueChange = onReasonChanged,
                    label = { Text(tr(StringKey.AUDIT_REASON_LABEL)) },
                    singleLine = true,
                    isError = uiState.pendingTransitionRequiresReason && uiState.transitionReason.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = InvoiceDetailScreenTags.TRANSITION_REASON },
                )
                if (uiState.pendingTransitionRequiresReason && uiState.transitionReason.isBlank()) {
                    Text(
                        tr(StringKey.AUDIT_REASON_REQUIRED),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = uiState.canConfirmTransition,
                modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.TRANSITION_CONFIRM },
            ) {
                Text(tr(StringKey.AUDIT_CONFIRM))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(StringKey.ACTION_CANCEL)) }
        },
    )
}

/** Atténuation appliquée à une action neutralisée par l'immutabilité fiscale. */
private const val LOCKED_ACTION_ALPHA = 0.4f

@Composable
private fun VatRow(line: VatBreakdown) {
    val lang = LocalAppLanguage.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceDetailScreenTags.vatRow(line.rate) },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Base ${line.rate.label}", style = MaterialTheme.typography.bodyMedium)
        Text(line.baseHt.format(lang), style = MaterialTheme.typography.bodyMedium)
        Text("TVA ${line.vatAmount.format(lang)}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TotalRow(label: String, value: String, tag: String, emphasize: Boolean = false) {
    val style =
        if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = tag },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = style, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
        Text(value, style = style, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun LockedBanner() {
    val readOnlyLabel = tr(StringKey.DETAIL_CANCELLED_READ_ONLY)
    Surface(
        color = Color(0xFFFFEBEE),
        contentColor = Color(0xFFB71C1C),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                testTag = InvoiceDetailScreenTags.LOCKED_BANNER
                contentDescription = readOnlyLabel
            },
    ) {
        Text(
            readOnlyLabel,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceDetailScreenTags.LOADING },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator()
        Text("Chargement de la facture…")
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                testTag = InvoiceDetailScreenTags.ERROR
                contentDescription = message
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onRetry,
            modifier = Modifier.semantics { testTag = InvoiceDetailScreenTags.RETRY_BUTTON },
        ) {
            Text("Réessayer")
        }
    }
}

@Composable
private fun NotFoundState() {
    Text(
        "Facture introuvable côté serveur.",
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceDetailScreenTags.NOT_FOUND },
    )
}
