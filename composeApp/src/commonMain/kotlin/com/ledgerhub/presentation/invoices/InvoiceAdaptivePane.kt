package com.ledgerhub.presentation.invoices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.data.audit.SqlDelightAuditRepository
import com.ledgerhub.data.creditnote.SqlDelightCreditNoteRepository
import com.ledgerhub.data.repository.LocalLedgerRepository
import com.ledgerhub.domain.invoice.ChangeInvoiceStatusUseCase
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.presentation.adaptive.LocalWindowSizeClass
import com.ledgerhub.presentation.adaptive.WindowWidthSizeClass

object InvoiceAdaptiveTags {
    const val DUAL_PANE_CONTAINER = "invoice_dual_pane_container"
    const val MASTER_PANE = "invoice_master_pane"
    const val DETAIL_PANE = "invoice_detail_pane"
    const val DETAIL_EMPTY_PLACEHOLDER = "invoice_detail_empty_placeholder"
}

/**
 * Composant Orchestrateur Adaptatif pour la gestion des factures.
 * - Sur grand écran (WindowWidthSizeClass.EXPANDED >= 840 dp) : Active le layout Dual-Pane Master-Detail (Liste ~40% / Détail ~60%).
 * - Sur écran compact et medium (< 840 dp) : Affiche la liste standard unifiée.
 */
@Composable
fun InvoiceAdaptivePane(
    invoiceListViewModel: InvoiceListViewModel,
    onInvoiceClick: (String) -> Unit,
    onCreateCreditNote: (Invoice) -> Unit,
    ledgerRepository: LocalLedgerRepository,
    creditNoteRepository: SqlDelightCreditNoteRepository,
    auditRepository: SqlDelightAuditRepository,
    changeInvoiceStatusUseCase: ChangeInvoiceStatusUseCase,
    onExportInvoiceXml: (Invoice) -> Unit,
    onExportCreditNoteXml: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowSizeClass = LocalWindowSizeClass.current
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.EXPANDED

    if (!isExpanded) {
        // Mode Standard Smartphone & Foldable Portrait (< 840 dp)
        InvoiceListScreen(
            viewModel = invoiceListViewModel,
            onInvoiceClick = onInvoiceClick,
            onCreateCreditNote = onCreateCreditNote,
        )
    } else {
        // Mode Dual-Pane Grand Écran / Tablette Paysage (>= 840 dp)
        val listUiState by invoiceListViewModel.uiState.collectAsState()
        val invoices = (listUiState.content as? InvoiceListContent.Success)?.invoices.orEmpty()
        
        var selectedInvoiceNumber by remember { mutableStateOf<String?>(null) }

        // Sélection par défaut de la première facture si disponible
        LaunchedEffect(invoices) {
            if (selectedInvoiceNumber == null && invoices.isNotEmpty()) {
                selectedInvoiceNumber = invoices.first().number
            }
        }

        Row(
            modifier = modifier
                .fillMaxSize()
                .semantics { testTag = InvoiceAdaptiveTags.DUAL_PANE_CONTAINER },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Panneau Gauche (Master : Liste des Factures ~40% de largeur)
            Card(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .semantics { testTag = InvoiceAdaptiveTags.MASTER_PANE },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                InvoiceListView(
                    uiState = listUiState,
                    onIntent = invoiceListViewModel::processIntent,
                    onInvoiceClick = { invoiceNumber ->
                        selectedInvoiceNumber = invoiceNumber
                    },
                    onCreateCreditNote = onCreateCreditNote,
                    selectedInvoiceNumber = selectedInvoiceNumber,
                )
            }

            // Panneau Droit (Detail : Vue détaillée & actions ~58% de largeur)
            Card(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .semantics { testTag = InvoiceAdaptiveTags.DETAIL_PANE },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                val currentNumber = selectedInvoiceNumber
                if (currentNumber == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { testTag = InvoiceAdaptiveTags.DETAIL_EMPTY_PLACEHOLDER },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Sélectionnez une facture pour afficher ses détails",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val detailViewModel = remember(currentNumber) {
                        InvoiceDetailViewModel(
                            invoiceNumber = currentNumber,
                            ledgerRepository = ledgerRepository,
                            creditNoteRepository = creditNoteRepository,
                            auditRepository = auditRepository,
                            changeInvoiceStatusUseCase = changeInvoiceStatusUseCase,
                        )
                    }
                    DisposableEffect(currentNumber) {
                        onDispose { detailViewModel.onCleared() }
                    }

                    InvoiceDetailScreen(
                        viewModel = detailViewModel,
                        onCreateCreditNoteClick = onCreateCreditNote,
                        onExportInvoiceXml = onExportInvoiceXml,
                        onExportCreditNoteXml = onExportCreditNoteXml,
                    )
                }
            }
        }
    }
}
