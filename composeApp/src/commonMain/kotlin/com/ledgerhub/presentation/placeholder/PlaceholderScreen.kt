package com.ledgerhub.presentation.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.tr

object PlaceholderTags {
    const val CLIENTS = "clients_screen"
    const val SETTINGS = "settings_screen"
}

/**
 * Écran « à venir » — destinations du shell de navigation pas encore implémentées (Clients,
 * Paramètres). Volontairement minimal : reprend le thème sombre global, aucune logique métier.
 */
@Composable
fun PlaceholderScreen(title: String, glyph: String, testTag: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).semantics { this.testTag = testTag },
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = 40.sp)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            tr(StringKey.PLACEHOLDER_COMING_SOON),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ClientsScreen() = PlaceholderScreen(tr(StringKey.NAV_CLIENTS), "👥", PlaceholderTags.CLIENTS)

@Composable
fun SettingsScreen() = PlaceholderScreen(tr(StringKey.NAV_SETTINGS), "⚙️", PlaceholderTags.SETTINGS)
