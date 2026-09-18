package com.ledgerhub.presentation.invoices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.components.InvoiceCard

import androidx.compose.foundation.layout.navigationBarsPadding
import com.ledgerhub.presentation.degraded.SyncBatchCard
import com.ledgerhub.presentation.degraded.SyncQueueUiState
import com.ledgerhub.presentation.degraded.SyncQueueViewModel

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object InvoiceListTags {
    const val SCREEN = "invoice_list_screen"
    const val LOADING = "invoice_list_loading"
    const val ERROR = "invoice_list_error"
    const val RETRY_BUTTON = "invoice_list_retry_button"
    const val EMPTY = "invoice_list_empty"
    const val LIST = "invoice_list_items"
    const val CREATE_BUTTON = "invoice_list_create_button"
    fun filterChip(filter: InvoiceStatusFilter) = "invoice_list_filter_${filter.name}"

    /** US-10 : action « Créer un avoir » proposée sous une facture finalisée non encore créditée. */
    fun creditNoteAction(number: String) = "invoice_list_credit_note_action_$number"
}

/** Composable stateful — observe [InvoiceListViewModel] et délègue la navigation au parent. */
@Composable
fun InvoiceListScreen(
    viewModel: InvoiceListViewModel,
    onInvoiceClick: (String) -> Unit,
    onCreateInvoice: () -> Unit = {},
    onCreateCreditNote: (Invoice) -> Unit = {},
    syncQueueViewModel: SyncQueueViewModel? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncQueueState = syncQueueViewModel?.uiState?.collectAsState()?.value
    InvoiceListView(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onInvoiceClick = onInvoiceClick,
        onCreateInvoice = onCreateInvoice,
        onCreateCreditNote = onCreateCreditNote,
        syncQueueUiState = syncQueueState,
        onSyncBatch = {
            syncQueueViewModel?.processBatch(onComplete = {
                viewModel.processIntent(InvoiceListIntent.Load)
            })
        },
    )
}

@Composable
internal fun InvoiceListView(
    uiState: InvoiceListUiState,
    onIntent: (InvoiceListIntent) -> Unit,
    onInvoiceClick: (String) -> Unit,
    onCreateInvoice: () -> Unit = {},
    onCreateCreditNote: (Invoice) -> Unit = {},
    selectedInvoiceNumber: String? = null,
    syncQueueUiState: SyncQueueUiState? = null,
    onSyncBatch: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .semantics { testTag = InvoiceListTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr(StringKey.NAV_INVOICES),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onCreateInvoice,
                modifier = Modifier.semantics { testTag = InvoiceListTags.CREATE_BUTTON },
            ) {
                Text("＋  ${tr(StringKey.ACTION_CREATE_INVOICE)}")
            }
        }

        if (syncQueueUiState != null) {
            SyncBatchCard(
                state = syncQueueUiState,
                onSyncBatch = onSyncBatch,
            )
        }

        FilterRow(
            selected = uiState.statusFilter,
            counts = uiState.counts,
            onSelected = { onIntent(InvoiceListIntent.FilterSelected(it)) },
        )

        when (val content = uiState.content) {
            InvoiceListContent.Loading -> LoadingState()
            is InvoiceListContent.Error -> ErrorState { onIntent(InvoiceListIntent.Retry) }
            InvoiceListContent.Empty -> EmptyState()
            is InvoiceListContent.Success ->
                InvoiceList(
                    invoices = content.invoices,
                    onInvoiceClick = onInvoiceClick,
                    onCreateCreditNote = onCreateCreditNote,
                    creditNotesByInvoice = uiState.creditNotesByInvoice,
                    selectedInvoiceNumber = selectedInvoiceNumber,
                )
        }
    }
}

@Composable
private fun FilterRow(
    selected: InvoiceStatusFilter,
    counts: Map<InvoiceStatusFilter, Int>,
    onSelected: (InvoiceStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InvoiceStatusFilter.entries.forEach { filter ->
            FilterPill(
                label = "${tr(filter.labelKey())} (${counts[filter] ?: 0})",
                isSelected = filter == selected,
                tag = InvoiceListTags.filterChip(filter),
                onClick = { onSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    val container =
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val onContainer =
        if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        color = container,
        contentColor = onContainer,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { testTag = tag },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceListTags.LOADING },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator()
        Text(tr(StringKey.LIST_LOADING))
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    val message = tr(StringKey.TOAST_INVOICES_LOAD_FAILED)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                testTag = InvoiceListTags.ERROR
                contentDescription = message
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = onRetry,
            modifier = Modifier.semantics { testTag = InvoiceListTags.RETRY_BUTTON },
        ) {
            Text(tr(StringKey.LIST_RETRY))
        }
    }
}

@Composable
private fun EmptyState() {
    Text(
        tr(StringKey.LIST_EMPTY),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = InvoiceListTags.EMPTY },
    )
}

@Composable
private fun InvoiceList(
    invoices: List<Invoice>,
    onInvoiceClick: (String) -> Unit,
    onCreateCreditNote: (Invoice) -> Unit,
    creditNotesByInvoice: Map<String, String>,
    selectedInvoiceNumber: String? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = InvoiceListTags.LIST },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(invoices, key = { it.number }) { invoice ->
            val alreadyCredited = creditNotesByInvoice[invoice.number]
            val isSelected = invoice.number == selectedInvoiceNumber
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InvoiceCard(
                    invoice = invoice,
                    onClick = { onInvoiceClick(invoice.number) },
                    creditNoteNumber = alreadyCredited,
                    isSelected = isSelected,
                )
                // Émission d'un avoir directement depuis la liste (US-10) : seulement sur une
                // facture finalisée qui ne porte pas déjà un avoir.
                if (invoice.isCancellableByCreditNote && alreadyCredited == null) {
                    TextButton(
                        onClick = { onCreateCreditNote(invoice) },
                        modifier = Modifier.semantics {
                            testTag = InvoiceListTags.creditNoteAction(invoice.number)
                        },
                    ) {
                        Text("Créer un avoir")
                    }
                }
            }
        }
    }
}
