package com.ledgerhub.presentation.ereporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingStatus
import com.ledgerhub.domain.ereporting.EReportingType
import kotlin.math.abs
import kotlin.math.roundToLong

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests. */
object EReportingTags {
    const val SCREEN = "ereporting_screen"
    const val BADGE = "ereporting_badge"
    const val LIST = "ereporting_list"
    const val LOADING = "ereporting_loading"
    const val EMPTY = "ereporting_empty"
    const val SNACKBAR = "ereporting_snackbar"

    fun card(id: String) = "ereporting_card_$id"
    fun transmitButton(id: String) = "ereporting_transmit_$id"
    fun ack(id: String) = "ereporting_ack_$id"
}

/** En-tête réglementaire affiché en permanence en haut de l'écran. */
const val EREPORTING_HEADER = "Conformité DGFIP 2026 — e-Reporting"

@Composable
fun EReportingScreen(viewModel: EReportingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    EReportingContent(uiState = uiState, onIntent = viewModel::processIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EReportingContent(
    uiState: EReportingUiState,
    onIntent: (EReportingIntent) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.ackMessage) {
        val message = uiState.ackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(EReportingIntent.MessageShown)
    }
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onIntent(EReportingIntent.MessageShown)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().semantics { testTag = EReportingTags.SCREEN },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.semantics { testTag = EReportingTags.SNACKBAR },
            ) { data ->
                Snackbar(
                    modifier = Modifier.semantics { contentDescription = data.visuals.message },
                ) {
                    Text(data.visuals.message)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConformityBadge()

            when {
                uiState.isLoading -> Text(
                    "Chargement des déclarations…",
                    modifier = Modifier.semantics { testTag = EReportingTags.LOADING },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                uiState.isEmpty -> Text(
                    "Aucune déclaration e-Reporting pour le moment.",
                    modifier = Modifier.semantics { testTag = EReportingTags.EMPTY },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTag = EReportingTags.LIST },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            isTransmitting = uiState.transmittingId == report.id,
                            onTransmit = { onIntent(EReportingIntent.Transmit(report.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConformityBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.semantics {
            testTag = EReportingTags.BADGE
            contentDescription = EREPORTING_HEADER
        },
    ) {
        Text(
            text = EREPORTING_HEADER,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ReportCard(
    report: EReportingReport,
    isTransmitting: Boolean,
    onTransmit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = EReportingTags.card(report.id) },
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    report.period,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    statusLabel(report.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                typeLabel(report.type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "HT ${formatEuro(report.totalHt)}  ·  TVA ${formatEuro(report.totalVat)}  ·  TTC ${formatEuro(report.totalTtc)}",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                "${report.transactionCount} transaction(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            report.ackNumber?.let { ack ->
                Text(
                    "Accusé PPF : $ack",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { testTag = EReportingTags.ack(report.id) },
                )
            }

            if (report.status == EReportingStatus.DRAFT) {
                Button(
                    onClick = onTransmit,
                    enabled = !isTransmitting,
                    modifier = Modifier.semantics { testTag = EReportingTags.transmitButton(report.id) },
                ) {
                    Text(if (isTransmitting) "Transmission…" else "Transmettre PPF")
                }
            }
        }
    }
}

private fun statusLabel(status: EReportingStatus): String = when (status) {
    EReportingStatus.DRAFT -> "Brouillon"
    EReportingStatus.ACKNOWLEDGED -> "Acquittée"
}

private fun typeLabel(type: EReportingType): String = when (type) {
    EReportingType.B2C -> "Ventes B2C"
    EReportingType.INTERNATIONAL -> "Opérations internationales"
    EReportingType.PAYMENTS -> "Encaissements"
}

/** Formatage monétaire local, sans dépendance plateforme : « 1234,50 € ». */
private fun formatEuro(amount: Double): String {
    val totalCents = (amount * 100.0).roundToLong()
    val sign = if (totalCents < 0) "-" else ""
    val absCents = abs(totalCents)
    val euros = absCents / 100
    val cents = (absCents % 100).toString().padStart(2, '0')
    return "$sign$euros,$cents €"
}
