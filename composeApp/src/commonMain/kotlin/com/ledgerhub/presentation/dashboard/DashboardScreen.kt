package com.ledgerhub.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.dashboard.QuoteFollowUpItem
import com.ledgerhub.domain.dashboard.RecentDocument
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.quote.QuoteStatus
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.i18n.formatIsoDate
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.invoices.formatMoney
import com.ledgerhub.presentation.theme.InvoiceStatusTone
import com.ledgerhub.presentation.theme.LedgerHubTheme
import com.ledgerhub.presentation.theme.statusColors

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests (commonTest / Robolectric). */
object DashboardTags {
    const val SCREEN = "dashboard_screen"
    const val LOADING_INDICATOR = "dashboard_loading_indicator"
    const val LOAD_ERROR = "dashboard_load_error"
    const val KPI_GRID = "dashboard_kpi_grid"
    const val COLLECTED_CARD = "dashboard_kpi_collected"
    const val PENDING_CARD = "dashboard_kpi_pending"
    const val ISSUED_CARD = "dashboard_kpi_issued"

    /** 4ᵉ KPI — montant cumulé des devis envoyés (US-12). */
    const val KPI_QUOTES_PENDING = "dashboard_kpi_quotes_pending"

    const val REVENUE_CHART = "dashboard_revenue_chart"
    const val RECENT_ACTIVITY_LIST = "dashboard_recent_activity_list"
    const val RECENT_ACTIVITY_EMPTY = "dashboard_recent_activity_empty"

    // ── Devis à relancer (US-12) ─────────────────────────────────────────────
    const val QUOTES_TO_FOLLOWUP_SECTION = "dashboard_quotes_to_followup_section"
    const val QUOTES_TO_FOLLOWUP_LIST = "dashboard_quotes_to_followup_list"
    const val QUOTES_TO_FOLLOWUP_EMPTY = "dashboard_quotes_to_followup_empty"

    fun recentDocumentTag(number: String) = "dashboard_recent_document_$number"
    fun recentDocumentStatusBadgeTag(number: String) = "dashboard_recent_document_status_$number"

    fun quoteFollowUpTag(number: String) = "dashboard_quote_followup_$number"
    fun quoteFollowUpDeadlineTag(number: String) = "dashboard_quote_followup_deadline_$number"
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                tr(StringKey.NAV_OVERVIEW),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                tr(StringKey.DASHBOARD_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
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
                KpiGrid(uiState)
                RevenueSection(uiState)
                RecentInvoicesSection(documents = uiState.recentDocuments)
                QuotesToFollowUpSection(items = uiState.quotesToFollowUp)
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.semantics { testTag = DashboardTags.LOADING_INDICATOR },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Text(tr(StringKey.DASHBOARD_LOADING))
    }
}

// ── KPI ──────────────────────────────────────────────────────────────────────────

@Composable
private fun KpiGrid(uiState: DashboardUiState) {
    val lang = LocalAppLanguage.current
    // Grille 2×2 (US-12) : quatre indicateurs tiennent à l'écran sans défilement, là où quatre
    // cartes pleine largeur repoussaient le graphique sous la ligne de flottaison.
    Column(
        modifier = Modifier.fillMaxWidth().semantics { testTag = DashboardTags.KPI_GRID },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpiCard(
                modifier = Modifier.weight(1f),
                tag = DashboardTags.COLLECTED_CARD,
                title = tr(StringKey.KPI_REVENUE_TITLE),
                value = formatMoney(uiState.collectedRevenueCents, lang),
                caption = tr(StringKey.KPI_REVENUE_CAPTION),
                glyph = "📈",
                accent = MaterialTheme.colorScheme.primaryContainer,
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                tag = DashboardTags.PENDING_CARD,
                title = tr(StringKey.KPI_PENDING_TITLE),
                value = formatMoney(uiState.pendingRevenueCents, lang),
                caption = tr(StringKey.KPI_PENDING_CAPTION),
                glyph = "⏳",
                accent = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KpiCard(
                modifier = Modifier.weight(1f),
                tag = DashboardTags.ISSUED_CARD,
                title = tr(StringKey.KPI_ISSUED_TITLE),
                value = uiState.issuedCount.toString(),
                caption = tr(StringKey.KPI_ISSUED_CAPTION),
                glyph = "🧾",
                accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                tag = DashboardTags.KPI_QUOTES_PENDING,
                title = tr(StringKey.KPI_QUOTES_PENDING_TITLE),
                value = formatMoney(uiState.pendingQuotesTotalCents, lang),
                caption = tr(StringKey.KPI_QUOTES_PENDING_CAPTION),
                glyph = "📝",
                accent = LedgerHubTheme.palette.QuotePendingBg,
                valueColor = LedgerHubTheme.palette.QuotePendingFg,
            )
        }
    }
}

/**
 * Carte KPI compacte — glyphe **au-dessus** du titre, et non à côté : en demi-largeur, la
 * disposition en `Row` ne laissait plus assez de place au montant.
 */
@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    tag: String,
    title: String,
    value: String,
    caption: String,
    glyph: String,
    accent: Color,
    valueColor: Color = Color.Unspecified,
) {
    OutlinedSurfaceCard(modifier = modifier.semantics { testTag = tag }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Graphe CA ────────────────────────────────────────────────────────────────────

@Composable
private fun RevenueSection(uiState: DashboardUiState) {
    OutlinedSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Le montant ne se replie jamais : c'est le titre et le sous-titre qui cèdent la place.
            // Sans cela, un CA à cinq chiffres passait à la ligne et recouvrait le sous-titre.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
                    Text(
                        tr(StringKey.KPI_REVENUE_TITLE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tr(StringKey.REVENUE_SECTION_SUBTITLE),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formatMoney(uiState.monthlyRevenue.sumOf { it.amount.cents }, LocalAppLanguage.current),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            RevenueChart(data = uiState.monthlyRevenue)
        }
    }
}

// ── Factures récentes ────────────────────────────────────────────────────────────

@Composable
private fun RecentInvoicesSection(documents: List<RecentDocument>) {
    OutlinedSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                tr(StringKey.RECENT_INVOICES_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (documents.isEmpty()) {
                Text(
                    tr(StringKey.RECENT_EMPTY),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = DashboardTags.RECENT_ACTIVITY_EMPTY },
                )
            } else {
                RecentHeaderRow()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = DashboardTags.RECENT_ACTIVITY_LIST },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    documents.forEach { RecentDocumentRow(it) }
                }
            }
        }
    }
}

@Composable
private fun RecentHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        HeaderCell(tr(StringKey.COL_INVOICE_NO), 1.4f)
        HeaderCell(tr(StringKey.COL_CLIENT), 1.6f)
        HeaderCell(tr(StringKey.COL_DATE), 1.1f)
        HeaderCell(tr(StringKey.COL_TTC), 1f, alignEnd = true)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, alignEnd: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
    )
}

@Composable
private fun RecentDocumentRow(document: RecentDocument) {
    val lang = LocalAppLanguage.current
    val row: RowData = when (document) {
        is RecentDocument.InvoiceDocument -> RowData(
            client = document.invoice.recipient.name,
            ttcCents = document.invoice.totalTtc.cents,
            statusKey = document.invoice.status.statusKey(),
            tone = document.invoice.status.tone(),
        )
        is RecentDocument.QuoteDocument -> RowData(
            client = document.quote.recipient.name,
            ttcCents = document.quote.totalTtc.cents,
            statusKey = document.quote.status.statusKey(),
            tone = document.quote.status.tone(),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = DashboardTags.recentDocumentTag(document.number) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            document.number,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(row.client, modifier = Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
        Text(
            formatIsoDate(document.issueDate, lang),
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(row.ttcCents, lang),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            StatusBadge(
                tag = DashboardTags.recentDocumentStatusBadgeTag(document.number),
                text = tr(row.statusKey),
                tone = row.tone,
            )
        }
    }
}

private data class RowData(
    val client: String,
    val ttcCents: Long,
    val statusKey: StringKey,
    val tone: InvoiceStatusTone,
)

// ── Devis à relancer (US-12) ─────────────────────────────────────────────────────

/**
 * Devis envoyés dont la validité arrive à échéance, du plus urgent au moins urgent.
 * Placée juste sous « Factures récentes » : la relance commerciale se lit dans le prolongement
 * de l'activité facturée.
 */
@Composable
private fun QuotesToFollowUpSection(items: List<QuoteFollowUpItem>) {
    OutlinedSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = DashboardTags.QUOTES_TO_FOLLOWUP_SECTION },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tr(StringKey.QUOTES_FOLLOWUP_TITLE),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(StringKey.QUOTES_FOLLOWUP_SUBTITLE),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (items.isEmpty()) {
                Text(
                    tr(StringKey.QUOTES_FOLLOWUP_EMPTY),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { testTag = DashboardTags.QUOTES_TO_FOLLOWUP_EMPTY },
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    HeaderCell(tr(StringKey.COL_QUOTE_NO), 1.4f)
                    HeaderCell(tr(StringKey.COL_CLIENT), 1.6f)
                    HeaderCell(tr(StringKey.COL_VALIDITY), 1.3f)
                    HeaderCell(tr(StringKey.COL_TTC), 1f, alignEnd = true)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = DashboardTags.QUOTES_TO_FOLLOWUP_LIST },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items.forEach { QuoteFollowUpRow(it) }
                }
            }
        }
    }
}

@Composable
private fun QuoteFollowUpRow(item: QuoteFollowUpItem) {
    val lang = LocalAppLanguage.current
    // Expiré = rouge, expire aujourd'hui ou demain = ambre, au-delà = neutre. Le délai est
    // l'information qui décide de l'action, il porte donc la couleur.
    val deadlineColor = when {
        item.isExpired -> LedgerHubTheme.palette.ErrorText
        item.daysRemaining <= URGENT_THRESHOLD_DAYS -> LedgerHubTheme.palette.StatusPendingFg
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val deadlineLabel = when {
        item.isExpired -> tr(StringKey.QUOTE_EXPIRED)
        item.daysRemaining == 0 -> tr(StringKey.QUOTE_DUE_TODAY)
        else -> "${tr(StringKey.QUOTE_DAYS_LEFT)}${item.daysRemaining}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = DashboardTags.quoteFollowUpTag(item.number) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.number,
            modifier = Modifier.weight(1.4f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            item.clientName,
            modifier = Modifier.weight(1.6f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                formatIsoDate(item.validityDate, lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                deadlineLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = deadlineColor,
                modifier = Modifier.semantics {
                    testTag = DashboardTags.quoteFollowUpDeadlineTag(item.number)
                    contentDescription = deadlineLabel
                },
            )
        }
        Text(
            formatMoney(item.totalTtc.cents, lang),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

/** En deçà de ce délai, la relance devient urgente (couleur d'alerte). */
private const val URGENT_THRESHOLD_DAYS = 3

@Composable
private fun StatusBadge(tag: String, text: String, tone: InvoiceStatusTone) {
    val (bg, fg) = statusColors(tone)
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = text
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// ── Primitives ───────────────────────────────────────────────────────────────────

/** Carte "panneau" du thème sombre : fond [MaterialTheme.colorScheme.surface], fine bordure, coins 16 dp. */
@Composable
private fun OutlinedSurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        content()
    }
}

private fun InvoiceStatus.tone(): InvoiceStatusTone = when (this) {
    InvoiceStatus.PAID -> InvoiceStatusTone.PAID
    InvoiceStatus.DEPOSITED, InvoiceStatus.APPROVED -> InvoiceStatusTone.PENDING
    InvoiceStatus.DRAFT, InvoiceStatus.REJECTED, InvoiceStatus.REFUSED, InvoiceStatus.CANCELLED ->
        InvoiceStatusTone.DRAFT
}

private fun QuoteStatus.tone(): InvoiceStatusTone = when (this) {
    QuoteStatus.ACCEPTED -> InvoiceStatusTone.PAID
    QuoteStatus.SENT -> InvoiceStatusTone.PENDING
    QuoteStatus.DRAFT, QuoteStatus.REJECTED -> InvoiceStatusTone.DRAFT
}

private fun InvoiceStatus.statusKey(): StringKey = when (this) {
    InvoiceStatus.DRAFT -> StringKey.STATUS_DRAFT
    InvoiceStatus.DEPOSITED -> StringKey.STATUS_DEPOSITED
    InvoiceStatus.APPROVED -> StringKey.STATUS_APPROVED
    InvoiceStatus.PAID -> StringKey.STATUS_PAID
    InvoiceStatus.REJECTED -> StringKey.STATUS_REJECTED
    InvoiceStatus.REFUSED -> StringKey.STATUS_REFUSED
    InvoiceStatus.CANCELLED -> StringKey.STATUS_CANCELLED
}

private fun QuoteStatus.statusKey(): StringKey = when (this) {
    QuoteStatus.DRAFT -> StringKey.STATUS_DRAFT
    QuoteStatus.SENT -> StringKey.STATUS_SENT
    QuoteStatus.ACCEPTED -> StringKey.STATUS_VALIDATED
    QuoteStatus.REJECTED -> StringKey.STATUS_CANCELLED
}
