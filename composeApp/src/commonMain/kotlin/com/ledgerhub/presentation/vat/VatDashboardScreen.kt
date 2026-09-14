package com.ledgerhub.presentation.vat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.parseAmountToCents
import com.ledgerhub.domain.vat.VatCa3Line
import com.ledgerhub.domain.vat.VatMetrics
import com.ledgerhub.domain.vat.VatPeriodFilter
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

@Composable
fun VatDashboardScreen(
    viewModel: VatDashboardViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    VatDashboardContent(
        uiState = uiState,
        onIntent = viewModel::processIntent,
        modifier = modifier,
    )
}

@Composable
internal fun VatDashboardContent(
    uiState: VatDashboardUiState,
    onIntent: (VatDashboardIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .semantics { testTag = VatDashboardTags.SCREEN },
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // En-tête Titre & Sous-titre
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = tr(StringKey.VAT_DASHBOARD_TITLE),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = tr(StringKey.VAT_DASHBOARD_SUBTITLE),
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerHubTheme.palette.SecondaryText,
            )
        }

        // Sélecteur de période fiscale
        VatPeriodSelector(
            selected = uiState.selectedPeriod,
            onSelect = { onIntent(VatDashboardIntent.ChangePeriod(it)) },
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics { testTag = VatDashboardTags.LOADING },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = LedgerHubTheme.palette.Accent)
            }
        } else {
            // KPI Grid
            VatKpiGrid(metrics = uiState.metrics)

            // Section Simulation TVA Déductible
            VatDeductibleInputSection(
                initialValue = uiState.deductibleVatInput,
                onApply = { amount -> onIntent(VatDashboardIntent.UpdateDeductibleVat(amount)) },
            )

            // Section État Préparatoire Déclaration 3310-CA3
            VatCa3TableSection(ca3Lines = uiState.metrics.ca3Lines)
        }
    }
}

@Composable
private fun VatPeriodSelector(
    selected: VatPeriodFilter,
    onSelect: (VatPeriodFilter) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .semantics { testTag = VatDashboardTags.PERIOD_SELECTOR },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VatPeriodFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val labelKey = when (filter) {
                VatPeriodFilter.ALL -> StringKey.VAT_PERIOD_ALL
                VatPeriodFilter.CURRENT_MONTH -> StringKey.VAT_PERIOD_MONTH
                VatPeriodFilter.CURRENT_QUARTER -> StringKey.VAT_PERIOD_QUARTER
                VatPeriodFilter.CURRENT_YEAR -> StringKey.VAT_PERIOD_YEAR
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        text = tr(labelKey),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LedgerHubTheme.palette.Accent.copy(alpha = 0.2f),
                    selectedLabelColor = LedgerHubTheme.palette.Accent,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = LedgerHubTheme.palette.Border,
                    selectedBorderColor = LedgerHubTheme.palette.Accent,
                ),
                modifier = Modifier.semantics { testTag = VatDashboardTags.periodChip(filter.name) },
            )
        }
    }
}

@Composable
private fun VatKpiGrid(metrics: VatMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // KPI 1 : TVA Collectée Exigible
            VatKpiCard(
                title = tr(StringKey.VAT_KPI_COLLECTED_EXIGIBLE_TITLE),
                caption = tr(StringKey.VAT_KPI_COLLECTED_EXIGIBLE_CAPTION),
                amount = metrics.totalVatCollectedExigible,
                accentColor = Color(0xFF388E3C), // Vert fisc
                testTag = VatDashboardTags.KPI_COLLECTED_EXIGIBLE,
                modifier = Modifier.weight(1f),
            )

            // KPI 2 : TVA en attente d'encaissement
            VatKpiCard(
                title = tr(StringKey.VAT_KPI_PENDING_COLLECTION_TITLE),
                caption = tr(StringKey.VAT_KPI_PENDING_COLLECTION_CAPTION),
                amount = metrics.totalVatPendingCollection,
                accentColor = Color(0xFFFFA000), // Ambre attente
                testTag = VatDashboardTags.KPI_PENDING_COLLECTION,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // KPI 3 : TVA Déductible
            VatKpiCard(
                title = tr(StringKey.VAT_KPI_DEDUCTIBLE_TITLE),
                caption = tr(StringKey.VAT_KPI_DEDUCTIBLE_CAPTION),
                amount = metrics.totalVatDeductible,
                accentColor = Color(0xFF1976D2), // Bleu déductible
                testTag = VatDashboardTags.KPI_DEDUCTIBLE,
                modifier = Modifier.weight(1f),
            )

            // KPI 4 : Solde Net / Crédit
            val isCredit = metrics.isCredit
            val title = if (isCredit) tr(StringKey.VAT_KPI_CREDIT_TITLE) else tr(StringKey.VAT_KPI_NET_DUE_TITLE)
            val balanceColor = if (isCredit) Color(0xFF00BFA5) else Color(0xFFE53935)

            VatKpiCard(
                title = title,
                caption = tr(StringKey.VAT_KPI_NET_BALANCE_CAPTION),
                amount = metrics.absoluteBalance,
                accentColor = balanceColor,
                testTag = VatDashboardTags.KPI_NET_BALANCE,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VatKpiCard(
    title: String,
    caption: String,
    amount: Money,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.semantics { this.testTag = testTag },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = LedgerHubTheme.palette.SecondaryText,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatMoney(amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun VatDeductibleInputSection(
    initialValue: String,
    onApply: (Money) -> Unit,
) {
    var textValue by remember(initialValue) { mutableStateOf(initialValue) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                label = { Text(tr(StringKey.VAT_DEDUCTIBLE_SIMULATION_LABEL)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = VatDashboardTags.DEDUCTIBLE_INPUT },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LedgerHubTheme.palette.Accent,
                    unfocusedBorderColor = LedgerHubTheme.palette.Border,
                ),
            )

            Button(
                onClick = {
                    val cents = parseAmountToCents(textValue) ?: 0L
                    onApply(Money(cents))
                },
                colors = ButtonDefaults.buttonColors(containerColor = LedgerHubTheme.palette.Accent),
                modifier = Modifier.semantics { testTag = VatDashboardTags.REFRESH_BUTTON },
            ) {
                Text("Appliquer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VatCa3TableSection(ca3Lines: List<VatCa3Line>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = VatDashboardTags.CA3_SECTION },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tr(StringKey.VAT_CA3_SECTION_TITLE),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tr(StringKey.VAT_CA3_SECTION_SUBTITLE),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tableau CA3 En-tête
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    tr(StringKey.VAT_CA3_COL_CODE),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LedgerHubTheme.palette.SecondaryText,
                    modifier = Modifier.width(44.dp),
                )
                Text(
                    tr(StringKey.VAT_CA3_COL_RATE),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LedgerHubTheme.palette.SecondaryText,
                    modifier = Modifier.width(60.dp),
                )
                Text(
                    tr(StringKey.VAT_CA3_COL_BASE_HT),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LedgerHubTheme.palette.SecondaryText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    tr(StringKey.VAT_CA3_COL_TAX_DUE),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = LedgerHubTheme.palette.SecondaryText,
                    modifier = Modifier.width(80.dp),
                )
            }

            HorizontalDivider(color = LedgerHubTheme.palette.Border, thickness = 1.dp)

            // Lignes du tableau CA3
            var totalTaxDueCents = 0L
            ca3Lines.forEach { line ->
                totalTaxDueCents += line.taxDue.cents
                VatCa3LineRow(line = line)
            }

            HorizontalDivider(color = LedgerHubTheme.palette.Border, thickness = 1.dp)

            // Total TVA Brute Due
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr(StringKey.VAT_CA3_TOTAL_BRUT),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatMoney(Money(totalTaxDueCents)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LedgerHubTheme.palette.Accent,
                )
            }
        }
    }
}

@Composable
private fun VatCa3LineRow(line: VatCa3Line) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { testTag = VatDashboardTags.ca3Line(line.lineCode) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "L${line.lineCode}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = LedgerHubTheme.palette.Accent,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = line.vatRate.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp),
        )
        Text(
            text = formatMoney(line.baseHt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMoney(line.taxDue),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp),
        )
    }
}

private fun formatMoney(money: Money): String {
    val totalCents = money.cents
    val isNegative = totalCents < 0
    val absCents = if (isNegative) -totalCents else totalCents
    val euros = absCents / 100
    val cents = (absCents % 100).toString().padStart(2, '0')
    val prefix = if (isNegative) "-" else ""
    return "$prefix$euros,$cents €"
}
