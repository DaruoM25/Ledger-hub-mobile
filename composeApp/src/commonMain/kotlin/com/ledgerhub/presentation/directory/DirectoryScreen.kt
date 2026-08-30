package com.ledgerhub.presentation.directory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.IdentifierKind
import com.ledgerhub.domain.directory.RoutingMode

/** Tags de test — contrat partagé entre l'UI (commonMain) et les tests. */
object DirectoryTags {
    const val SCREEN = "directory_screen"
    const val SEARCH_FIELD = "directory_search_field"
    const val LUHN_INDICATOR = "directory_luhn_indicator"
    const val SEARCH_BUTTON = "directory_search_button"
    const val LOADING = "directory_loading"
    const val RESULT_CARD = "directory_result_card"
    const val RESULT_COMPANY = "directory_result_company"
    const val RESULT_VAT = "directory_result_vat"
    const val ROUTING_BADGE = "directory_routing_badge"
    const val PDP_IDENTIFIER = "directory_pdp_identifier"
    const val NOT_FOUND = "directory_not_found"
    const val ERROR = "directory_error"
}

private val PpfColor = Color(0xFF2563EB)
private val PdpColor = Color(0xFF7C3AED)
private val LuhnOkColor = Color(0xFF16A34A)
private val LuhnKoColor = Color(0xFFDC2626)

@Composable
fun DirectoryScreen(viewModel: DirectoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    DirectoryContent(uiState = uiState, onIntent = viewModel::processIntent)
}

@Composable
internal fun DirectoryContent(
    uiState: DirectoryUiState,
    onIntent: (DirectoryIntent) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .semantics { testTag = DirectoryTags.SCREEN }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Annuaire DGFIP",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Résolution SIREN/SIRET, routage PPF/PDP et numéro de TVA certifié.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onIntent(DirectoryIntent.QueryChanged(it)) },
            label = { Text("SIREN (9 chiffres) ou SIRET (14 chiffres)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = uiState.luhnValid == false,
            modifier = Modifier.fillMaxWidth().semantics { testTag = DirectoryTags.SEARCH_FIELD },
        )

        LuhnIndicator(kind = uiState.identifierKind, luhnValid = uiState.luhnValid)

        Button(
            onClick = { onIntent(DirectoryIntent.Search) },
            enabled = uiState.isSearchEnabled,
            modifier = Modifier.fillMaxWidth().semantics { testTag = DirectoryTags.SEARCH_BUTTON },
        ) {
            Text("Rechercher dans l'annuaire")
        }

        when {
            uiState.isSearching -> Row(
                modifier = Modifier.semantics { testTag = DirectoryTags.LOADING },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                Text("Interrogation de l'annuaire…")
            }

            uiState.resolved != null -> DirectoryResultCard(uiState.resolved)

            uiState.notFound -> Banner(
                text = "Aucune entreprise ne correspond à cet identifiant dans l'annuaire.",
                tag = DirectoryTags.NOT_FOUND,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        uiState.errorMessage?.let { message ->
            Banner(
                text = message,
                tag = DirectoryTags.ERROR,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun LuhnIndicator(kind: IdentifierKind, luhnValid: Boolean?) {
    val (symbol, label, color) = when {
        luhnValid == true && kind == IdentifierKind.SIREN -> Triple("✓", "Clé de Luhn valide — SIREN", LuhnOkColor)
        luhnValid == true && kind == IdentifierKind.SIRET -> Triple("✓", "Clé de Luhn valide — SIRET", LuhnOkColor)
        luhnValid == false -> Triple("✗", "Clé de Luhn invalide", LuhnKoColor)
        else -> Triple("•", "Saisissez 9 chiffres (SIREN) ou 14 chiffres (SIRET)", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(
        modifier = Modifier.semantics {
            testTag = DirectoryTags.LUHN_INDICATOR
            contentDescription = label
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol, color = color, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun DirectoryResultCard(entry: DirectoryEntry) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { testTag = DirectoryTags.RESULT_CARD },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    entry.companyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { testTag = DirectoryTags.RESULT_COMPANY },
                )
                RoutingBadge(entry.routingMode)
            }

            Text(
                "SIREN ${entry.siren}" + (entry.siret?.let { " · SIRET $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = if (entry.isVatSubject) {
                    "TVA certifiée : ${entry.vatNumber ?: "—"}"
                } else {
                    "Non assujettie à la TVA (franchise en base)"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (entry.isVatSubject) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.semantics { testTag = DirectoryTags.RESULT_VAT },
            )

            entry.pdpIdentifier?.let { pdp ->
                Text(
                    "PDP destinataire : $pdp",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { testTag = DirectoryTags.PDP_IDENTIFIER },
                )
            }

            Text(
                "Statut : ${statusLabel(entry.status)} · synchronisé le ${entry.lastSyncAt}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RoutingBadge(mode: RoutingMode) {
    val color = if (mode == RoutingMode.PPF) PpfColor else PdpColor
    val label = if (mode == RoutingMode.PPF) "Routage PPF" else "Routage PDP"
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.semantics {
            testTag = DirectoryTags.ROUTING_BADGE
            contentDescription = label
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Banner(text: String, tag: String, containerColor: Color, contentColor: Color) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().semantics {
            testTag = tag
            contentDescription = text
        },
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun statusLabel(status: DirectoryStatus): String = when (status) {
    DirectoryStatus.ACTIVE -> "Active"
    DirectoryStatus.INACTIVE -> "Inactive"
    DirectoryStatus.UNKNOWN -> "Inconnu"
}
