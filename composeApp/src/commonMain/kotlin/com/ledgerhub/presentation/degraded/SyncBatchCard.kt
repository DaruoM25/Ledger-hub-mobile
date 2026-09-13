package com.ledgerhub.presentation.degraded

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.tr

/**
 * Carte de régularisation par lot de la file de synchronisation en mode dégradé (US-29).
 * Respecte le thème sombre (Dark Mode) M3 sans aplat blanc.
 */
@Composable
fun SyncBatchCard(
    state: SyncQueueUiState,
    onSyncBatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.pendingCount == 0L && state.lastSyncResult == null) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1B16),
            contentColor = Color(0xFFFDE68A),
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFD97706)),
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = DegradedModeTags.SYNC_BATCH_CARD },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⏳", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = tr(StringKey.SYNC_BATCH_TITLE),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFDE68A),
                    )
                }

                Surface(
                    color = Color(0xFF78350F),
                    contentColor = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.semantics { testTag = DegradedModeTags.SYNC_BATCH_COUNT },
                ) {
                    Text(
                        text = "${state.pendingCount} en attente",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = tr(StringKey.SYNC_BATCH_SUBTITLE),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD1D5DB),
            )

            if (state.lastSyncResult != null) {
                Surface(
                    color = Color(0xFF064E3B),
                    contentColor = Color(0xFF6EE7B7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().semantics { testTag = DegradedModeTags.SYNC_BATCH_SUCCESS },
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("✓")
                        Text(
                            text = "${tr(StringKey.SYNC_BATCH_SUCCESS)} (${state.lastSyncResult.successCount} transmise(s))",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            if (state.isSyncing) {
                Row(
                    modifier = Modifier.fillMaxWidth().semantics { testTag = DegradedModeTags.SYNC_BATCH_PROGRESS },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color(0xFFF59E0B),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = tr(StringKey.SYNC_BATCH_PROGRESS),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B),
                    )
                }
            } else if (state.pendingCount > 0L) {
                Button(
                    onClick = onSyncBatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD97706),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = DegradedModeTags.SYNC_BATCH_BUTTON },
                ) {
                    Text(
                        text = "🚀  ${tr(StringKey.SYNC_BATCH_ACTION)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
