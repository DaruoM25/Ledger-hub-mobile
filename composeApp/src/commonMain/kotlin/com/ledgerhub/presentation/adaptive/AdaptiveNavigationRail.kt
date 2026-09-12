package com.ledgerhub.presentation.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.ledgerhub.Destination
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.theme.ResolvedTheme
import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.presentation.components.LangToggle
import com.ledgerhub.presentation.components.ThemeToggle
import com.ledgerhub.presentation.i18n.tr

object AdaptiveNavigationTags {
    const val NAVIGATION_RAIL = "adaptive_navigation_rail"
    const val SIDEBAR = "ledger_sidebar"
    const val BOTTOM_BAR = "ledger_bottom_bar"
    const val FAB_CREATE_INVOICE = "rail_fab_create_invoice"
    const val TRIGGER_PALETTE = "rail_trigger_palette"
    const val TRIGGER_INTEGRATIONS = "rail_trigger_integrations"
    const val TRIGGER_EXPORT = "rail_trigger_export"
}

/**
 * Composant de navigation compact (Largeur ~80 dp) optimisé pour le palier MEDIUM (600 dp à 839 dp).
 * Conçu spécifiquement pour les terminaux pliables dépliés (Galaxy Z Fold) et petites tablettes en mode portrait.
 */
@Composable
internal fun AdaptiveNavigationRail(
    selected: Destination,
    language: AppLanguage,
    themeMode: ThemeMode,
    resolvedTheme: ResolvedTheme,
    onSelect: (Destination) -> Unit,
    onCreateInvoice: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleTheme: (ThemeMode) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenIntegrations: () -> Unit,
    onOpenExportModal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .semantics { testTag = AdaptiveNavigationTags.NAVIGATION_RAIL },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            ) {
                FloatingActionButton(
                    onClick = onCreateInvoice,
                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.semantics { testTag = AdaptiveNavigationTags.FAB_CREATE_INVOICE },
                ) {
                    Text("＋", style = MaterialTheme.typography.titleMedium)
                }

                IconButton(
                    onClick = onOpenCommandPalette,
                    modifier = Modifier.semantics {
                        testTag = AdaptiveNavigationTags.TRIGGER_PALETTE
                        contentDescription = "Palette de commandes"
                    },
                ) {
                    Text("🔍", style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Destination.entries.forEach { entry ->
                    val isSelected = entry == selected
                    NavigationRailItem(
                        selected = isSelected,
                        onClick = { onSelect(entry) },
                        icon = { Text(entry.glyph, style = MaterialTheme.typography.titleMedium) },
                        label = {
                            Text(
                                text = tr(entry.titleKey),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.semantics { testTag = "nav_${entry.name.lowercase()}" },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                IconButton(
                    onClick = onOpenIntegrations,
                    modifier = Modifier.semantics {
                        testTag = AdaptiveNavigationTags.TRIGGER_INTEGRATIONS
                        contentDescription = "Intégrations"
                    },
                ) {
                    Text("🔌", style = MaterialTheme.typography.titleSmall)
                }

                IconButton(
                    onClick = onOpenExportModal,
                    modifier = Modifier.semantics {
                        testTag = AdaptiveNavigationTags.TRIGGER_EXPORT
                        contentDescription = "Export FEC"
                    },
                ) {
                    Text("📦", style = MaterialTheme.typography.titleSmall)
                }

                ThemeToggle(mode = themeMode, resolved = resolvedTheme, onToggle = onToggleTheme)
                LangToggle(current = language, onSelect = onSelectLanguage)
            }
        }
    }
}
