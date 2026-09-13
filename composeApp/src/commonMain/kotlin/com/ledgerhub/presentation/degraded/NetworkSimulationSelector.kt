package com.ledgerhub.presentation.degraded

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.degraded.DegradedModeNetworkState
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.tr

/**
 * Sélecteur de simulation réseau pour le Mode Dégradé (US-29).
 * Permet de basculer entre l'état nominal [DegradedModeNetworkState.OPERATIONAL]
 * et l'indisponibilité simulée du Portail Public de Facturation [DegradedModeNetworkState.OUTAGE].
 */
@Composable
fun NetworkSimulationSelector(
    networkState: DegradedModeNetworkState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val isOutage = networkState == DegradedModeNetworkState.OUTAGE
    val containerColor = if (isOutage) Color(0xFF3D2706) else Color(0xFF063E2E)
    val borderColor = if (isOutage) Color(0xFFD97706) else Color(0xFF059669)
    val textColor = if (isOutage) Color(0xFFFDE68A) else Color(0xFF6EE7B7)
    val glyph = if (isOutage) "⚠️" else "🟢"
    val label = if (compact) {
        if (isOutage) "Incident PPF" else "PPF OK"
    } else {
        if (isOutage) {
            tr(StringKey.NETWORK_STATUS_OUTAGE)
        } else {
            tr(StringKey.NETWORK_STATUS_OPERATIONAL)
        }
    }

    Surface(
        color = containerColor,
        contentColor = textColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .semantics {
                testTag = DegradedModeTags.NETWORK_TOGGLE
                contentDescription = label
            }
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = 4.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = glyph, style = MaterialTheme.typography.labelMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
            )
        }
    }
}
