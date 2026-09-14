package com.ledgerhub.presentation.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.ledgerhub.domain.vault.DigitalArchive
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

@Composable
fun VaultArchiveScreen(
    viewModel: VaultArchiveViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.exportSuccessMessage, uiState.errorMessage) {
        val msg = uiState.exportSuccessMessage ?: uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.processIntent(VaultArchiveIntent.DismissSnackbar)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = VaultArchiveTags.SCREEN }
    ) {
        VaultArchiveContent(
            uiState = uiState,
            onIntent = viewModel::processIntent,
            onBack = onBack,
            modifier = Modifier.fillMaxSize()
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics { testTag = VaultArchiveTags.SNACKBAR }
        )
    }

    if (uiState.showReportDialog && uiState.verificationReport != null) {
        val report = uiState.verificationReport!!
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(VaultArchiveIntent.DismissDialog) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (report.isCompletelyValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (report.isCompletelyValid) Color(0xFF388E3C) else Color(0xFFE53935),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr(StringKey.VAULT_DIALOG_TITLE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (report.isCompletelyValid) tr(StringKey.VAULT_DIALOG_SUCCESS_MESSAGE) else tr(StringKey.VAULT_DIALOG_CORRUPTED_MESSAGE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Divider(color = LedgerHubTheme.palette.Border)
                    Text(
                        text = "• Documents vérifiés : ${report.verifiedDocuments} / ${report.totalDocuments}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerHubTheme.palette.SecondaryText
                    )
                    Text(
                        text = "• Chaîne d'audit : ${if (report.isAuditChainValid) "Intègre" else "Altérée / Invalide"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (report.isAuditChainValid) Color(0xFF388E3C) else Color(0xFFE53935)
                    )
                    if (report.corruptedDocuments.isNotEmpty()) {
                        Text(
                            text = "Factures corrompues : ${report.corruptedDocuments.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE53935),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.processIntent(VaultArchiveIntent.DismissDialog) },
                    modifier = Modifier.semantics { testTag = VaultArchiveTags.DIALOG_CLOSE }
                ) {
                    Text(tr(StringKey.VAULT_DIALOG_CLOSE_ACTION))
                }
            },
            modifier = Modifier.semantics { testTag = VaultArchiveTags.DIALOG }
        )
    }
}

@Composable
internal fun VaultArchiveContent(
    uiState: VaultArchiveUiState,
    onIntent: (VaultArchiveIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar & Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { testTag = VaultArchiveTags.BACK_BUTTON }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column {
                Text(
                    text = tr(StringKey.VAULT_SCREEN_TITLE),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = tr(StringKey.VAULT_SCREEN_SUBTITLE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerHubTheme.palette.SecondaryText
                )
            }
        }

        // Global Integrity Status Banner
        IntegrityStatusBadge(
            isValid = uiState.isIntegrityValid,
            isVerifying = uiState.isVerifying
        )

        // KPI Row Cards
        VaultKpiGrid(
            sealedCount = uiState.sealedDocumentsCount,
            totalSizeBytes = uiState.totalSizeInBytes,
            isValid = uiState.isIntegrityValid
        )

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onIntent(VaultArchiveIntent.VerifyIntegrity) },
                enabled = !uiState.isVerifying,
                colors = ButtonDefaults.buttonColors(containerColor = LedgerHubTheme.palette.Accent),
                modifier = Modifier
                    .weight(1.2f)
                    .semantics { testTag = VaultArchiveTags.VERIFY_BUTTON }
            ) {
                if (uiState.isVerifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = tr(StringKey.VAULT_ACTION_VERIFY_INTEGRITY),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { onIntent(VaultArchiveIntent.ExportAuditLog) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
                modifier = Modifier
                    .weight(1f)
                    .semantics { testTag = VaultArchiveTags.EXPORT_BUTTON }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = tr(StringKey.VAULT_ACTION_EXPORT_PROOF),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Divider(color = LedgerHubTheme.palette.Border)

        // Document List Section
        Text(
            text = tr(StringKey.VAULT_DOCUMENTS_SECTION_TITLE),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .semantics { testTag = VaultArchiveTags.LOADING },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LedgerHubTheme.palette.Accent)
            }
        } else if (uiState.archives.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics { testTag = VaultArchiveTags.EMPTY_STATE },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(StringKey.VAULT_EMPTY_ARCHIVES),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerHubTheme.palette.SecondaryText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { testTag = VaultArchiveTags.ARCHIVE_LIST },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.archives, key = { it.id }) { archive ->
                    ArchiveRowCard(archive = archive)
                }
            }
        }
    }
}

@Composable
private fun IntegrityStatusBadge(isValid: Boolean, isVerifying: Boolean) {
    val bg = if (isValid) Color(0xFF1B5E20).copy(alpha = 0.2f) else Color(0xFFB71C1C).copy(alpha = 0.2f)
    val strokeColor = if (isValid) Color(0xFF388E3C) else Color(0xFFE53935)
    val textColor = if (isValid) Color(0xFF4CAF50) else Color(0xFFEF5350)
    val label = if (isVerifying) {
        tr(StringKey.VAULT_VERIFYING_PROGRESS)
    } else if (isValid) {
        "Intégrité du Coffre-Fort : 100% Conforme"
    } else {
        "Alerte : Altération Détectée"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, strokeColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = strokeColor,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "Chaînage SHA-256 certifié DGFiP & Archivage 10 ans",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerHubTheme.palette.SecondaryText
                )
            }
        }
    }
}

@Composable
private fun VaultKpiGrid(
    sealedCount: Int,
    totalSizeBytes: Long,
    isValid: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // KPI 1: Sealed Documents
        VaultKpiCard(
            title = tr(StringKey.VAULT_KPI_SEALED_DOCS_TITLE),
            value = "$sealedCount",
            caption = tr(StringKey.VAULT_KPI_SEALED_DOCS_CAPTION),
            accentColor = Color(0xFF1976D2),
            testTag = VaultArchiveTags.KPI_SEALED,
            modifier = Modifier.weight(1f)
        )

        // KPI 2: Integrity Rate
        VaultKpiCard(
            title = tr(StringKey.VAULT_KPI_INTEGRITY_TITLE),
            value = if (isValid) "100%" else "FAIL",
            caption = tr(StringKey.VAULT_KPI_INTEGRITY_CAPTION),
            accentColor = if (isValid) Color(0xFF388E3C) else Color(0xFFE53935),
            testTag = VaultArchiveTags.KPI_INTEGRITY,
            modifier = Modifier.weight(1f)
        )

        // KPI 3: Storage Size
        val sizeFormatted = formatStorageSize(totalSizeBytes)
        VaultKpiCard(
            title = tr(StringKey.VAULT_KPI_TOTAL_SIZE_TITLE),
            value = sizeFormatted,
            caption = tr(StringKey.VAULT_KPI_TOTAL_SIZE_CAPTION),
            accentColor = Color(0xFFFFA000),
            testTag = VaultArchiveTags.KPI_STORAGE,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VaultKpiCard(
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
private fun ArchiveRowCard(archive: DigitalArchive) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = VaultArchiveTags.archiveRow(archive.invoiceNumber) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF388E3C),
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = archive.invoiceNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Scellé le : ${archive.sealedAt.take(19)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LedgerHubTheme.palette.SecondaryText
                    )
                    Text(
                        text = "SHA: ${archive.payloadHash.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = LedgerHubTheme.palette.Accent
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF388E3C).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF388E3C).copy(alpha = 0.5f))
            ) {
                Text(
                    text = archive.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun formatStorageSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${(bytes / (1024 * 1024.0)).toString().take(4)} MB"
    }
}
