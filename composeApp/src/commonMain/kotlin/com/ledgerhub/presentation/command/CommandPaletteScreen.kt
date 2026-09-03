package com.ledgerhub.presentation.command

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ledgerhub.domain.command.CommandAction
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.tr

/**
 * Tags de test — contrat partagé entre l'UI (commonMain) et les trois niveaux de tests.
 * Les valeurs sont figées par le cahier des charges US-19.
 */
object CommandPaletteTags {
    const val TRIGGER = "command_palette_trigger"
    const val DIALOG = "command_palette_dialog"
    const val INPUT = "command_palette_input"
    const val EMPTY = "command_palette_empty"

    /**
     * Voile assombri. Tag interne — hors cahier des charges : sans lui, aucun test ne peut viser
     * l'exterieur de la modale, un clic sur la modale etant justement absorbe par elle.
     */
    const val SCRIM = "command_palette_scrim"

    fun action(action: CommandAction): String = when (action) {
        CommandAction.CREATE_INVOICE -> "command_palette_action_create_invoice"
        CommandAction.REMIND_OVERDUE -> "command_palette_action_remind_overdue"
        CommandAction.EXPORT_ACCOUNTING -> "command_palette_action_export_accounting"
    }
}

/** Étincelle du champ de recherche. Glyphe plutôt qu'icône : voir [SparkleGlyph]. */
private const val SPARKLE = "✨"

/** Raccourci annoncé sur le déclencheur — la convention universellement lue comme « palette ». */
private const val SHORTCUT_BADGE = "⌘K"

private val PaletteMaxWidth = 560.dp
private val ScrimColor = Color(0xCC0B1020)

/**
 * Déclencheur de la palette, posé dans l'en-tête global (US-19).
 *
 * Porte l'icône de recherche, le libellé **et** le badge du raccourci : sur un appareil sans
 * clavier le badge ne sert à rien, mais c'est aussi ce qui apprend le raccourci à l'utilisateur qui
 * en branchera un.
 *
 * ## Le mode [compact] (US-25)
 *
 * Réduit le déclencheur à sa seule loupe, comme le font déjà `ExportModalTrigger` et
 * `IntegrationsHubTrigger`. Ce n'est pas un choix esthétique : la barre étendue occupait à elle
 * seule le tiers de la largeur d'un Pixel 5, et l'arrivée d'une cinquième commande dans l'en-tête
 * (la bascule de thème) y a poussé le sélecteur de langue **hors de l'écran** — constaté sur
 * l'appareil, pas déduit. Le badge de raccourci n'a de valeur pédagogique que là où un clavier
 * peut être branché : la sidebar tablette, qui garde le déclencheur en toutes lettres.
 */
@Composable
fun CommandPaletteTrigger(
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
            .semantics(mergeDescendants = true) { testTag = CommandPaletteTags.TRIGGER },
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🔍", style = MaterialTheme.typography.labelLarge)
            if (!compact) {
                Text(
                    text = tr(StringKey.COMMAND_PALETTE_TRIGGER_LABEL),
                    style = MaterialTheme.typography.labelLarge,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = SHORTCUT_BADGE,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Palette de commandes — modale flottante centrée (US-19).
 *
 * ## Le « glassmorphism », et pourquoi il ne repose pas sur un flou
 *
 * `Modifier.blur` s'appuie sur `RenderEffect`, disponible à partir d'Android 12 (API 31), alors que
 * l'application descend à l'API 26. Sous Android 12 l'appel ne lève rien : il ne fait simplement
 * *rien*. Un rendu qui reposerait dessus serait donc plat, sans avertissement, sur une part
 * importante du parc.
 *
 * L'effet est donc obtenu par ce qui fonctionne partout : un voile sombre sur l'arrière-plan, une
 * surface translucide, une bordure supérieure lumineuse et une élévation. Le flou, s'il est un jour
 * ajouté, ne sera qu'un rehaut au-dessus de cette base — jamais ce qui la porte.
 *
 * La fermeture au clic extérieur et à la touche Retour est celle de [Dialog] : les redéclarer
 * exposerait à ce que les deux chemins divergent.
 */
@Composable
fun CommandPalette(
    uiState: CommandPaletteUiState,
    onIntent: (CommandPaletteIntent) -> Unit,
) {
    if (!uiState.isOpen) return
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = { onIntent(CommandPaletteIntent.Close) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
                // Le tap sur le voile ferme la palette.
                //
                // `pointerInput` et non `clickable`, pour la meme raison que sur la surface : un
                // `clickable` declare une semantique de clic, donc un noeud fusionne — et ce
                // voile enveloppant toute la modale, il absorbait l'integralite de son contenu.
                // La palette devenait un bouton plein ecran unique : ni ses actions ni son champ
                // de saisie n'etaient plus atteignables, au lecteur d'ecran comme aux tests.
                // La fermeture au retour arriere reste assuree par `Dialog`.
                .pointerInput(Unit) {
                    detectTapGestures { onIntent(CommandPaletteIntent.Close) }
                }
                .semantics { testTag = CommandPaletteTags.SCRIM },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 8.dp,
                shadowElevation = 24.dp,
                modifier = Modifier
                    .widthIn(max = PaletteMaxWidth)
                    .fillMaxWidth()
                    .padding(24.dp)
                    // Bordure en dégradé : c'est elle qui donne l'arête « verre » que le flou ne
                    // peut pas rendre en-deçà d'Android 12.
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.05f)),
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    // Absorbe le tap : sans cela, toucher la palette la fermerait, le voile
                    // recevant l'evenement juste derriere.
                    //
                    // `pointerInput` et non `clickable` : ce dernier declare une semantique de
                    // clic, ce qui fusionne le noeud avec toute sa descendance. La palette
                    // s'annoncerait alors comme un unique bouton, ses actions cessant d'etre
                    // atteignables une par une au lecteur d'ecran — et, accessoirement, par tag
                    // dans l'arbre fusionne. Un conteneur n'est pas un bouton.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { testTag = CommandPaletteTags.DIALOG },
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = tr(StringKey.COMMAND_PALETTE_TITLE),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QueryField(
                        query = uiState.query,
                        focusRequester = focusRequester,
                        onQueryChange = { onIntent(CommandPaletteIntent.UpdateQuery(it)) },
                    )
                    if (uiState.hasNoMatch) {
                        Text(
                            text = tr(StringKey.COMMAND_PALETTE_EMPTY),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { testTag = CommandPaletteTags.EMPTY },
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.results, key = { it.name }) { action ->
                                ActionRow(
                                    action = action,
                                    onClick = { onIntent(CommandPaletteIntent.ExecuteAction(action)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Le clavier s'ouvre avec la palette : elle est faite pour etre utilisee sans toucher l'ecran.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}

/**
 * Champ de recherche.
 *
 * [BasicTextField] plutôt qu'un `TextField` Material : ce dernier impose une hauteur minimale de
 * 56 dp et un fond propre qui écraseraient la ligne fine attendue dans une palette de commandes.
 */
@Composable
private fun QueryField(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SparkleGlyph()
        Box(modifier = Modifier.fillMaxWidth()) {
            if (query.isEmpty()) {
                Text(
                    text = tr(StringKey.COMMAND_PALETTE_PLACEHOLDER),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { testTag = CommandPaletteTags.INPUT },
            )
        }
    }
}

/**
 * Étincelle du champ de recherche.
 *
 * Glyphe et non `Icons.Filled.AutoAwesome` : cette icône vit dans `material-icons-extended`, absente
 * des dépendances. Tirer toute la bibliothèque pour un seul pictogramme serait disproportionné, et
 * le dépôt emploie déjà des glyphes dans sa navigation.
 */
@Composable
private fun SparkleGlyph() {
    Text(text = SPARKLE, style = MaterialTheme.typography.titleMedium)
}

/**
 * Une action proposée.
 *
 * Nœud sémantique **fusionné** : la ligne s'annonce d'un bloc à un lecteur d'écran et porte de ce
 * fait son texte propre, ce dont dépendent les assertions des niveaux 3.
 */
@Composable
private fun ActionRow(action: CommandAction, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Cible tactile confortable : la ligne entiere est cliquable, pas seulement son texte.
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { testTag = CommandPaletteTags.action(action) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("→", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                text = tr(action.labelKey),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
