package com.ledgerhub.presentation.integrations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.integrations.IntegrationModule
import com.ledgerhub.domain.integrations.IntegrationStatus
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

/**
 * Tags de test — contrat partagé entre l'UI (commonMain) et les trois niveaux de tests.
 * Les valeurs du conteneur et des quatre cartes sont **figées par le cahier des charges US-20** :
 * les modifier casserait les suites QA. Elles sont verrouillées par `IntegrationsHubTagsTest` (N1).
 */
object IntegrationsHubTags {
    const val CONTAINER = "integrations_hub_container"

    /**
     * Tag de la carte d'un module. `when` exhaustif plutôt qu'une interpolation sur l'identifiant :
     * la forme littérale est ce que le cahier des charges fige, et un cinquième module devra
     * déclarer son tag pour que le code compile.
     */
    fun card(module: IntegrationModule): String = when (module) {
        IntegrationModule.STRIPE_PAYMENTS -> "integration_card_stripe"
        IntegrationModule.SLACK_NOTIFICATIONS -> "integration_card_slack"
        IntegrationModule.FEC_EXPORT -> "integration_card_fec"
        IntegrationModule.BANK_SYNC -> "integration_card_bank_sync"
    }

    // ── Tags internes, hors cahier des charges ────────────────────────────────
    // Sans eux, aucun test ne peut viser un badge indépendamment du texte de sa carte : la carte
    // est un nœud sémantique fusionné (voir IntegrationCard), les tests l'atteignent donc via
    // `useUnmergedTree = true`. Même précédent que `CommandPaletteTags.SCRIM` (US-19).

    /** Pastille de statut d'un module. */
    fun badge(module: IntegrationModule): String = "integration_badge_" + module.id

    /** Bandeau expliquant qu'un module touché est verrouillé. */
    const val NOTICE = "integrations_hub_notice"

    /** Déclencheur posé dans le shell (en-tête compact et sidebar). */
    const val TRIGGER = "integrations_hub_trigger"
}

/**
 * Largeur minimale d'une carte.
 *
 * **Ce n'est pas un réglage esthétique.** `LazyVerticalGrid` virtualise : une carte hors du
 * viewport n'existe pas dans l'arbre sémantique. À 170 dp, un Pixel 5 (393 dp) affiche deux
 * colonnes, donc les quatre modules tiennent en 2 × 2 sans défilement — sans quoi la capture QA
 * officielle en manquerait la moitié, et les tests devraient faire défiler pour les trouver.
 * Une sidebar ou une tablette en tire trois à quatre colonnes.
 */
private val CardMinWidth = 170.dp

/** Cible tactile confortable — la carte entière est cliquable, pas seulement son titre. */
private val CardMinHeight = 150.dp

/** Fond des cartes : la teinte du thème, **atténuée**. Un module verrouillé se voit avant de se lire. */
private const val LockedCardAlpha = 0.7f

// ── Teintes des badges ────────────────────────────────────────────────────────
// Reprises du thème (indigo des devis, ambre des statuts en attente) : le hub n'introduit pas une
// palette de plus. Elles sont peintes à **pleine opacité**, contrairement au reste de la carte —
// c'est tout l'objet du badge que d'être lu en premier.
//
// Propriétés `@Composable` et non constantes de fichier : depuis l'US-25 la palette dépend du thème
// actif, une valeur figée à l'initialisation de la classe resterait sombre en thème clair.
private val BetaBadgeBg: Color @Composable @ReadOnlyComposable get() = LedgerHubTheme.palette.QuotePendingBg
private val BetaBadgeFg: Color @Composable @ReadOnlyComposable get() = LedgerHubTheme.palette.QuotePendingFg
private val ComingSoonBadgeBg: Color @Composable @ReadOnlyComposable get() = LedgerHubTheme.palette.StatusPendingBg
private val ComingSoonBadgeFg: Color @Composable @ReadOnlyComposable get() = LedgerHubTheme.palette.StatusPendingFg

@Composable
fun IntegrationsHubScreen(viewModel: IntegrationsHubViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    IntegrationsHubContent(uiState = uiState, onIntent = viewModel::processIntent)
}

/**
 * Hub d'intégrations (US-20) — vitrine façon boutique de modules.
 *
 * Aucun module n'est branché : l'écran ne promet donc rien qu'il ne tienne. Les cartes sont
 * **désaturées** (fond et titres atténués, contour discret) et portent chacune un badge à pleine
 * opacité qui dit leur degré d'ouverture. Toute l'information tient dans ce contraste : ce qui est
 * éteint est verrouillé, ce qui est coloré dit quand ça ne le sera plus.
 *
 * Grille adaptative plutôt que colonne : quatre modules en liste sur une tablette gaspilleraient
 * la largeur, et c'est la mise en page qui fait lire un catalogue comme un catalogue.
 */
@Composable
internal fun IntegrationsHubContent(
    uiState: IntegrationsHubUiState,
    onIntent: (IntegrationsHubIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTag = IntegrationsHubTags.CONTAINER }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = tr(StringKey.INTEGRATIONS_TITLE),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = tr(StringKey.INTEGRATIONS_SUBTITLE),
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerHubTheme.palette.SecondaryText,
        )

        uiState.noticeModule?.let { module ->
            LockedNotice(
                module = module,
                onDismiss = { onIntent(IntegrationsHubIntent.NoticeDismissed) },
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CardMinWidth),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.modules, key = { it.name }) { module ->
                IntegrationCard(
                    module = module,
                    onClick = { onIntent(IntegrationsHubIntent.ModuleSelected(module)) },
                )
            }
        }
    }
}

/**
 * Carte d'un module.
 *
 * Nœud sémantique **fusionné** : la carte s'annonce d'un bloc à un lecteur d'écran — un module est
 * une unité, pas quatre textes empilés. Ses enfants (dont le badge) restent atteignables par tag
 * dans l'arbre non fusionné, ce dont dépendent les assertions des niveaux 3.
 *
 * L'atténuation est portée par les **couleurs**, jamais par un `Modifier.alpha` sur la carte : ce
 * dernier délaverait aussi le badge, que le cahier des charges veut très visible.
 */
@Composable
private fun IntegrationCard(module: IntegrationModule, onClick: () -> Unit) {
    Surface(
        color = LedgerHubTheme.palette.Surface.copy(alpha = LockedCardAlpha),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CardMinHeight)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = IntegrationsHubTags.card(module) },
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = module.glyph, style = MaterialTheme.typography.titleLarge)
            Text(
                text = tr(module.titleKey),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = LedgerHubTheme.palette.PrimaryText.copy(alpha = LockedCardAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tr(module.descriptionKey),
                style = MaterialTheme.typography.bodySmall,
                color = LedgerHubTheme.palette.SecondaryText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            IntegrationBadge(module)
        }
    }
}

/** Pastille de statut — pleine opacité, teinte saturée : c'est ce qui doit se lire en premier. */
@Composable
private fun IntegrationBadge(module: IntegrationModule) {
    val background: Color
    val foreground: Color
    when (module.status) {
        IntegrationStatus.BETA -> {
            background = BetaBadgeBg
            foreground = BetaBadgeFg
        }

        IntegrationStatus.COMING_SOON -> {
            background = ComingSoonBadgeBg
            foreground = ComingSoonBadgeFg
        }
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier.semantics { testTag = IntegrationsHubTags.badge(module) },
    ) {
        Text(
            text = tr(module.status.badgeKey),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Bandeau affiché au toucher d'une carte : il nomme le module et dit qu'il est verrouillé.
 * Toucher le bandeau l'acquitte — le geste de rejet le plus court possible pour une information
 * qui n'appelle aucune décision.
 */
@Composable
private fun LockedNotice(module: IntegrationModule, onDismiss: () -> Unit) {
    Surface(
        color = LedgerHubTheme.palette.InputBackground,
        contentColor = LedgerHubTheme.palette.SecondaryText,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onDismiss)
            .semantics(mergeDescendants = true) { testTag = IntegrationsHubTags.NOTICE },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "🔒", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = tr(module.titleKey) + " — " + tr(StringKey.INTEGRATIONS_LOCKED_NOTICE),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Déclencheur du hub, posé dans le shell (US-20).
 *
 * [compact] réduit le bouton à son seul glyphe : l'en-tête d'un téléphone porte déjà le nom de
 * l'application, le déclencheur de la palette de commandes et le sélecteur de langue. Un libellé
 * de plus y pousserait le sélecteur hors de l'écran — ce que la sidebar, elle, a la place d'offrir.
 */
@Composable
fun IntegrationsHubTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = IntegrationsHubTags.TRIGGER },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🧩", style = MaterialTheme.typography.labelLarge)
            if (!compact) {
                Text(
                    text = tr(StringKey.INTEGRATIONS_TRIGGER_LABEL),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
