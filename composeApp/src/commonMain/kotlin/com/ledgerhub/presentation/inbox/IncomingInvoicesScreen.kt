package com.ledgerhub.presentation.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.inbox.ReceivedInvoice
import com.ledgerhub.domain.inbox.ReceivedInvoiceStatus
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

@Composable
fun IncomingInvoicesScreen(
    viewModel: IncomingInvoicesViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage, uiState.errorMessage) {
        val msg = uiState.infoMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.processIntent(IncomingInvoicesIntent.DismissMessage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = IncomingInvoicesTags.SCREEN }
    ) {
        IncomingInvoicesContent(
            uiState = uiState,
            onIntent = viewModel::processIntent,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics { testTag = IncomingInvoicesTags.SNACKBAR }
        )
    }

    if (uiState.selectedInvoiceForDetail != null) {
        InvoiceDetailDialog(
            invoice = uiState.selectedInvoiceForDetail!!,
            onApprove = { viewModel.processIntent(IncomingInvoicesIntent.ApproveInvoice(it)) },
            onReject = { id, reason -> viewModel.processIntent(IncomingInvoicesIntent.RejectInvoice(id, reason)) },
            onDismiss = { viewModel.processIntent(IncomingInvoicesIntent.CloseInvoiceDetail) }
        )
    }
}

@Composable
internal fun IncomingInvoicesContent(
    uiState: IncomingInvoicesUiState,
    onIntent: (IncomingInvoicesIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { testTag = IncomingInvoicesTags.BACK_BUTTON }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column {
                Text(
                    text = tr(StringKey.INBOX_SCREEN_TITLE),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = tr(StringKey.INBOX_SCREEN_SUBTITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerHubTheme.palette.SecondaryText
                )
            }
        }

        // Global Alert Banner if any duplicate alerts exist
        if (uiState.totalAlertsCount > 0) {
            DuplicateAlertBanner(alertsCount = uiState.totalAlertsCount)
        }

        // KPI Row
        InboxKpiGrid(
            receivedCount = uiState.totalReceivedCount,
            alertsCount = uiState.totalAlertsCount,
            approvedCount = uiState.totalApprovedCount
        )

        // Filter Tabs
        InboxTabs(
            selectedTab = uiState.selectedTab,
            onSelectTab = { onIntent(IncomingInvoicesIntent.SelectTab(it)) }
        )

        // Invoices List
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .semantics { testTag = IncomingInvoicesTags.LOADING },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LedgerHubTheme.palette.Accent)
            }
        } else if (uiState.filteredInvoices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { testTag = IncomingInvoicesTags.EMPTY_STATE },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(StringKey.INBOX_EMPTY_LIST),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerHubTheme.palette.SecondaryText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { testTag = IncomingInvoicesTags.INVOICE_LIST },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredInvoices, key = { it.id }) { invoice ->
                    ReceivedInvoiceCard(
                        invoice = invoice,
                        onClick = { onIntent(IncomingInvoicesIntent.OpenInvoiceDetail(invoice)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateAlertBanner(alertsCount: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFB71C1C).copy(alpha = 0.2f),
        border = BorderStroke(1.dp, Color(0xFFE53935)),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = IncomingInvoicesTags.DUPLICATE_BANNER }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = "$alertsCount ${tr(StringKey.INBOX_ALERT_DUPLICATE_TITLE)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF5350)
                )
                Text(
                    text = tr(StringKey.INBOX_ALERT_DUPLICATE_DESC),
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText
                )
            }
        }
    }
}

@Composable
private fun InboxKpiGrid(
    receivedCount: Int,
    alertsCount: Int,
    approvedCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InboxKpiCard(
            title = tr(StringKey.INBOX_KPI_RECEIVED_TITLE),
            value = "$receivedCount",
            caption = tr(StringKey.INBOX_KPI_RECEIVED_CAPTION),
            accentColor = Color(0xFF1976D2),
            testTag = IncomingInvoicesTags.KPI_RECEIVED,
            modifier = Modifier.weight(1f)
        )
        InboxKpiCard(
            title = tr(StringKey.INBOX_KPI_ALERTS_TITLE),
            value = "$alertsCount",
            caption = tr(StringKey.INBOX_KPI_ALERTS_CAPTION),
            accentColor = if (alertsCount > 0) Color(0xFFE53935) else Color(0xFF4CAF50),
            testTag = IncomingInvoicesTags.KPI_ALERTS,
            modifier = Modifier.weight(1f)
        )
        InboxKpiCard(
            title = tr(StringKey.INBOX_KPI_APPROVED_TITLE),
            value = "$approvedCount",
            caption = tr(StringKey.INBOX_KPI_APPROVED_CAPTION),
            accentColor = Color(0xFF388E3C),
            testTag = IncomingInvoicesTags.KPI_APPROVED,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InboxKpiCard(
    title: String,
    value: String,
    caption: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = modifier.semantics { this.testTag = testTag }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = LedgerHubTheme.palette.SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InboxTabs(
    selectedTab: IncomingInvoicesFilterTab,
    onSelectTab: (IncomingInvoicesFilterTab) -> Unit,
) {
    val tabs = listOf(
        IncomingInvoicesFilterTab.ALL to (tr(StringKey.INBOX_TAB_ALL) to IncomingInvoicesTags.TAB_ALL),
        IncomingInvoicesFilterTab.ALERTS_ONLY to (tr(StringKey.INBOX_TAB_ALERTS) to IncomingInvoicesTags.TAB_ALERTS),
        IncomingInvoicesFilterTab.APPROVED to (tr(StringKey.INBOX_TAB_APPROVED) to IncomingInvoicesTags.TAB_APPROVED),
    )

    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = LedgerHubTheme.palette.Accent,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[tabs.indexOfFirst { it.first == selectedTab }]),
                color = LedgerHubTheme.palette.Accent
            )
        }
    ) {
        tabs.forEach { (tab, labelAndTag) ->
            val (label, tag) = labelAndTag
            val selected = tab == selectedTab
            Tab(
                selected = selected,
                onClick = { onSelectTab(tab) },
                text = {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) LedgerHubTheme.palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.semantics { testTag = tag }
            )
        }
    }
}

@Composable
private fun ReceivedInvoiceCard(
    invoice: ReceivedInvoice,
    onClick: () -> Unit,
) {
    val isAlert = invoice.status == ReceivedInvoiceStatus.DUPLICATE_ALERT
    val borderColor = if (isAlert) Color(0xFFE53935) else LedgerHubTheme.palette.Border
    val statusColor = when (invoice.status) {
        ReceivedInvoiceStatus.RECEIVED -> Color(0xFF1976D2)
        ReceivedInvoiceStatus.APPROVED -> Color(0xFF388E3C)
        ReceivedInvoiceStatus.REJECTED -> Color(0xFF757575)
        ReceivedInvoiceStatus.DUPLICATE_ALERT -> Color(0xFFE53935)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { testTag = IncomingInvoicesTags.invoiceCard(invoice.id) }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = invoice.supplierName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = invoice.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Facture : ${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText
                )
                Text(
                    text = "${(invoice.totalTtc.cents / 100.0)} € TTC",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isAlert && invoice.duplicateReason != null) {
                Text(
                    text = "⚠️ ${invoice.duplicateReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun InvoiceDetailDialog(
    invoice: ReceivedInvoice,
    onApprove: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDuplicate = invoice.status == ReceivedInvoiceStatus.DUPLICATE_ALERT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDuplicate) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (isDuplicate) Color(0xFFE53935) else Color(0xFF388E3C),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Détail Facture Reçue : ${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDuplicate) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFB71C1C).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFE53935)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr(StringKey.INBOX_DUPLICATE_BLOCKED_BANNER) + "\n" + (invoice.duplicateReason ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Text("Fournisseur : ${invoice.supplierName} (SIREN: ${invoice.supplierSiren})")
                Text("Date d'émission : ${invoice.issueDate}")
                Text("Date d'échéance : ${invoice.dueDate}")
                Text("Montant HT : ${invoice.totalHt.cents / 100.0} €")
                Text("Montant TVA : ${invoice.totalVat.cents / 100.0} €")
                Text("Total TTC : ${invoice.totalTtc.cents / 100.0} €", fontWeight = FontWeight.Bold)
                Text("Hash SHA-256 : ${invoice.fileHash.take(16)}...", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = { onApprove(invoice.id) },
                enabled = !isDuplicate && invoice.status != ReceivedInvoiceStatus.APPROVED,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                modifier = Modifier.semantics { testTag = IncomingInvoicesTags.APPROVE_BUTTON }
            ) {
                Text(tr(StringKey.INBOX_ACTION_APPROVE))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { testTag = IncomingInvoicesTags.CLOSE_DIALOG_BUTTON }
            ) {
                Text("Fermer")
            }
        },
        modifier = Modifier.semantics { testTag = IncomingInvoicesTags.DETAIL_DIALOG }
    )
}
