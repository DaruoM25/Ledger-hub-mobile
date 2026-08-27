package com.ledgerhub.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.dashboard.RecentDocument
import com.ledgerhub.presentation.invoicedetail.badgeColor as invoiceStatusBadgeColor
import com.ledgerhub.presentation.invoicedetail.label as invoiceStatusLabel
import com.ledgerhub.presentation.quotes.badgeColor as quoteStatusBadgeColor
import com.ledgerhub.presentation.quotes.label as quoteStatusLabel

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest). */
object DashboardTags {
    const val SCREEN = "dashboard_screen"
    const val LOADING_INDICATOR = "dashboard_loading_indicator"
    const val LOAD_ERROR = "dashboard_load_error"
    const val COLLECTED_CARD = "dashboard_kpi_collected"
    const val PENDING_CARD = "dashboard_kpi_pending"
    const val OVERDUE_CARD = "dashboard_kpi_overdue"
    const val REVENUE_CHART = "dashboard_revenue_chart"
    const val RECENT_ACTIVITY_LIST = "dashboard_recent_activity_list"
    const val RECENT_ACTIVITY_EMPTY = "dashboard_recent_activity_empty"

    fun recentDocumentTag(number: String) = "dashboard_recent_document_$number"
    fun recentDocumentStatusBadgeTag(number: String) = "dashboard_recent_document_status_$number"
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = remember { DashboardViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    DashboardContent(uiState = uiState)
}

@Composable
internal fun DashboardContent(uiState: DashboardUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = DashboardTags.SCREEN }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Tableau de bord", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        when {
            // Le premier chargement (aucune donnée encore reçue) affiche un indicateur plein écran ;
            // un rechargement ultérieur avec des données déjà présentes ne masque pas tout l'écran.
            uiState.isLoading && uiState.analytics == null -> LoadingRow()

            uiState.loadErrorMessage != null -> Text(
                text = uiState.loadErrorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    testTag = DashboardTags.LOAD_ERROR
                    contentDescription = uiState.loadErrorMessage
                },
            )

            else -> {
                KpiCardsRow(uiState)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Chiffre d'affaires (6 derniers mois)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    RevenueBarChart(data = uiState.monthlyRevenue)
                }

                RecentActivitySection(documents = uiState.recentDocuments)
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.semantics { testTag = DashboardTags.LOADING_INDICATOR },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text("Chargement du tableau de bord…")
    }
}

@Composable
private fun KpiCardsRow(uiState: DashboardUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KpiCard(
            modifier = Modifier.weight(1f),
            tag = DashboardTags.COLLECTED_CARD,
            title = "Encaissé",
            amountCents = uiState.collectedRevenueCents,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            tag = DashboardTags.PENDING_CARD,
            title = "En attente",
            amountCents = uiState.pendingRevenueCents,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        KpiCard(
            modifier = Modifier.weight(1f),
            tag = DashboardTags.OVERDUE_CARD,
            title = "En retard",
            amountCents = uiState.overdueRevenueCents,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier,
    tag: String,
    title: String,
    amountCents: Long,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        modifier = modifier.semantics { testTag = tag },
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${formatCents(amountCents)} €",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecentActivitySection(documents: List<RecentDocument>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Activité récente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (documents.isEmpty()) {
            Text(
                "Aucun document pour le moment",
                modifier = Modifier.semantics { testTag = DashboardTags.RECENT_ACTIVITY_EMPTY },
            )
        } else {
            Column(
                modifier = Modifier.semantics { testTag = DashboardTags.RECENT_ACTIVITY_LIST },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                documents.forEach { document -> RecentDocumentRow(document) }
            }
        }
    }
}

@Composable
private fun RecentDocumentRow(document: RecentDocument) {
    val (kindLabel, statusText, statusColor) = when (document) {
        is RecentDocument.InvoiceDocument -> Triple(
            "Facture",
            document.invoice.status.invoiceStatusLabel(),
            document.invoice.status.invoiceStatusBadgeColor(),
        )
        is RecentDocument.QuoteDocument -> Triple(
            "Devis",
            document.quote.status.quoteStatusLabel(),
            document.quote.status.quoteStatusBadgeColor(),
        )
    }

    Card(modifier = Modifier.fillMaxWidth().semantics { testTag = DashboardTags.recentDocumentTag(document.number) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("$kindLabel ${document.number}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(document.issueDate, style = MaterialTheme.typography.bodySmall)
            }
            StatusBadge(
                tag = DashboardTags.recentDocumentStatusBadgeTag(document.number),
                text = statusText,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun StatusBadge(tag: String, text: String, color: Color) {
    Surface(
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = text
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
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
