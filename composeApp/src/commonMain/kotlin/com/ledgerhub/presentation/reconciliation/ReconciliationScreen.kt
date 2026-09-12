package com.ledgerhub.presentation.reconciliation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.format

/**
 * Tags de test — contrat partagé entre l'UI (commonMain) et les trois niveaux de tests.
 * Les valeurs sont figées par le cahier des charges US-18 : les modifier casserait les suites QA.
 */
object ReconciliationTags {
    const val SCREEN = "bank_reconciliation_screen"
    const val TRANSACTIONS_LIST = "bank_transactions_list"
    const val INVOICES_LIST = "unpaid_invoices_list"
    const val RECONCILE_BUTTON = "btn_reconcile_match"
    const val TAB_TRANSACTIONS = "bank_reconciliation_tab_transactions"
    const val TAB_INVOICES = "bank_reconciliation_tab_invoices"
    const val SELECTION_HINT = "bank_reconciliation_hint"
    const val ERROR = "bank_reconciliation_error"
    const val RECONCILIATION_EREPORTING_BANNER = "bank_reconciliation_ereporting_banner"

    fun transactionCard(id: String): String = "transaction_card_$id"
    fun invoiceCard(number: String): String = "invoice_card_$number"
}

/** Seuil Material 3 « expanded » — au-delà, les deux faces tiennent côte à côte. */
private val ExpandedWidthThreshold = 840.dp

private val SelectedAccent = Color(0xFF2E6BE6)
private val ReconciledAccent = Color(0xFF00A86B)
private val MismatchAccent = Color(0xFFE07A00)

@Composable
fun ReconciliationScreen(viewModel: ReconciliationViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    ReconciliationContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        onSelectTab = viewModel::showTab,
    )
}

/**
 * Écran de Rapprochement Bancaire (US-18).
 *
 * Agencement responsive au seuil de 840 dp, comme le shell de navigation et l'aperçu de facture :
 * deux colonnes côte à côte sur tablette ou téléphone en paysage, deux onglets en-deçà. Sur un
 * téléphone en portrait, deux colonnes rendraient chaque montant illisible — or le rapprochement
 * consiste précisément à comparer des montants.
 *
 * En agencement compact, **les deux sélections restent visibles** quel que soit l'onglet affiché :
 * l'utilisateur choisit une transaction, bascule sur les factures, et doit garder sous les yeux ce
 * qu'il est en train d'associer. C'est le rôle du bandeau de rappel sous les onglets.
 */
@Composable
internal fun ReconciliationContent(
    uiState: ReconciliationUiState,
    onIntent: (ReconciliationIntent) -> Unit,
    onSelectTab: (ReconciliationTab) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = ReconciliationTags.SCREEN },
    ) {
        val expanded = maxWidth >= ExpandedWidthThreshold

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = tr(StringKey.RECONCILIATION_TITLE),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { testTag = ReconciliationTags.ERROR },
                )
            }

            if (uiState.showEreportingBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .semantics { testTag = ReconciliationTags.RECONCILIATION_EREPORTING_BANNER },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "☁",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = tr(StringKey.RECONCILIATION_EREPORTING_READY_BANNER),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                if (expanded) {
                    TwoColumnLayout(uiState, onIntent)
                } else {
                    TabbedLayout(uiState, onIntent, onSelectTab)
                }
            }
        }

        // Le bouton n'existe qu'une fois les deux cotes designes : proposer d'associer avant
        // d'avoir quoi associer laisserait l'utilisateur deviner ce qui manque.
        AnimatedVisibility(
            visible = uiState.canReconcile,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { onIntent(ReconciliationIntent.PerformMatch) },
                modifier = Modifier.semantics { testTag = ReconciliationTags.RECONCILE_BUTTON },
            ) {
                Text(tr(StringKey.RECONCILIATION_ACTION_MATCH), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TwoColumnLayout(
    uiState: ReconciliationUiState,
    onIntent: (ReconciliationIntent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ColumnHeading(tr(StringKey.RECONCILIATION_TRANSACTIONS_COLUMN))
            TransactionList(uiState, onIntent, Modifier.weight(1f))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ColumnHeading(tr(StringKey.RECONCILIATION_INVOICES_COLUMN))
            InvoiceList(uiState, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabbedLayout(
    uiState: ReconciliationUiState,
    onIntent: (ReconciliationIntent) -> Unit,
    onSelectTab: (ReconciliationTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabChip(
                label = tr(StringKey.RECONCILIATION_TAB_TRANSACTIONS),
                selected = uiState.compactTab == ReconciliationTab.TRANSACTIONS,
                // Indicateur persistant : le point rappelle qu'une selection est en cours de ce
                // cote, y compris depuis l'autre onglet.
                hasSelection = uiState.selectedTransactionId != null,
                tag = ReconciliationTags.TAB_TRANSACTIONS,
                onClick = { onSelectTab(ReconciliationTab.TRANSACTIONS) },
                modifier = Modifier.weight(1f),
            )
            TabChip(
                label = tr(StringKey.RECONCILIATION_TAB_INVOICES),
                selected = uiState.compactTab == ReconciliationTab.INVOICES,
                hasSelection = uiState.selectedInvoiceNumber != null,
                tag = ReconciliationTags.TAB_INVOICES,
                onClick = { onSelectTab(ReconciliationTab.INVOICES) },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = tr(StringKey.RECONCILIATION_SELECTION_HINT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics { testTag = ReconciliationTags.SELECTION_HINT },
        )

        Box(modifier = Modifier.weight(1f)) {
            when (uiState.compactTab) {
                ReconciliationTab.TRANSACTIONS -> TransactionList(uiState, onIntent, Modifier.fillMaxSize())
                ReconciliationTab.INVOICES -> InvoiceList(uiState, onIntent, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    hasSelection: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) SelectedAccent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) SelectedAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = tag },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            if (hasSelection) {
                Box(modifier = Modifier.size(8.dp).background(SelectedAccent, RoundedCornerShape(4.dp)))
            }
        }
    }
}

@Composable
private fun ColumnHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun TransactionList(
    uiState: ReconciliationUiState,
    onIntent: (ReconciliationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.transactions.isEmpty()) {
        EmptyState(tr(StringKey.RECONCILIATION_TRANSACTIONS_EMPTY), modifier)
        return
    }
    LazyColumn(
        modifier = modifier.semantics { testTag = ReconciliationTags.TRANSACTIONS_LIST },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.transactions, key = { it.id }) { transaction ->
            TransactionCard(
                transaction = transaction,
                selected = transaction.id == uiState.selectedTransactionId,
                reconciled = uiState.isTransactionReconciled(transaction.id),
                onClick = { onIntent(ReconciliationIntent.SelectTransaction(transaction.id)) },
            )
        }
    }
}

@Composable
private fun InvoiceList(
    uiState: ReconciliationUiState,
    onIntent: (ReconciliationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.invoices.isEmpty()) {
        EmptyState(tr(StringKey.RECONCILIATION_INVOICES_EMPTY), modifier)
        return
    }
    LazyColumn(
        modifier = modifier.semantics { testTag = ReconciliationTags.INVOICES_LIST },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(uiState.invoices, key = { it.number }) { invoice ->
            InvoiceCard(
                invoice = invoice,
                selected = invoice.number == uiState.selectedInvoiceNumber,
                reconciled = uiState.isInvoiceReconciled(invoice.number),
                // L'ecart n'est affiche que sur la facture en cours d'association : hors
                // selection, il n'aurait aucun terme de comparaison.
                delta = uiState.pendingDelta.takeIf { invoice.number == uiState.selectedInvoiceNumber },
                onClick = { onIntent(ReconciliationIntent.SelectInvoice(invoice.number)) },
            )
        }
    }
}

/**
 * Carte d'écriture bancaire.
 *
 * Nœud sémantique **fusionné** : la carte s'annonce d'un bloc à un lecteur d'écran — libellé,
 * date, montant, badge — au lieu d'égrener quatre fragments, et porte de ce fait son texte propre.
 */
@Composable
private fun TransactionCard(
    transaction: BankTransaction,
    selected: Boolean,
    reconciled: Boolean,
    onClick: () -> Unit,
) {
    SelectableCard(
        selected = selected,
        tag = ReconciliationTags.transactionCard(transaction.id),
        onClick = onClick,
    ) {
        Text(transaction.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = transaction.valueDateIso,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = transaction.amount.format(LocalAppLanguage.current),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        if (reconciled) Badge(tr(StringKey.RECONCILIATION_BADGE_RECONCILED), ReconciledAccent)
    }
}

@Composable
private fun InvoiceCard(
    invoice: Invoice,
    selected: Boolean,
    reconciled: Boolean,
    delta: Money?,
    onClick: () -> Unit,
) {
    SelectableCard(
        selected = selected,
        tag = ReconciliationTags.invoiceCard(invoice.number),
        onClick = onClick,
    ) {
        Text(invoice.number, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = invoice.recipient.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = invoice.totalTtc.format(LocalAppLanguage.current),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        if (reconciled) Badge(tr(StringKey.RECONCILIATION_BADGE_RECONCILED), ReconciledAccent)
        if (delta != null && delta != Money.ZERO) {
            Badge(tr(StringKey.RECONCILIATION_BADGE_AMOUNT_MISMATCH), MismatchAccent)
            Text(
                text = "${tr(StringKey.RECONCILIATION_DELTA_LABEL)} : ${delta.format(LocalAppLanguage.current)}",
                style = MaterialTheme.typography.labelSmall,
                color = MismatchAccent,
            )
        }
    }
}

@Composable
private fun SelectableCard(
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = if (selected) SelectedAccent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Cible tactile confortable : la carte entiere est cliquable, pas seulement son titre.
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = tag },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Badge(text: String, accent: Color) {
    Surface(color = accent.copy(alpha = 0.14f), contentColor = accent, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
