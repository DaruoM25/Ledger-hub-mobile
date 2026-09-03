package com.ledgerhub.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.theme.ResolvedTheme
import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.presentation.i18n.tr
import com.ledgerhub.presentation.theme.LedgerHubTheme

object ThemeToggleTags {
    /** Tag normalisé imposé par le cahier des charges de l'US-25 — figé par `ThemeToggleTagsTest`. */
    const val ROOT = "theme_toggle_btn"
    const val ICON_SUN = "theme_toggle_sun"
    const val ICON_MOON = "theme_toggle_moon"
}

/** Cible tactile minimale Material — vérifiée sur appareil par `ThemeToggleInstrumentedTest`. */
private val MinimumTouchTarget = 48.dp

/**
 * Bascule Thème Sombre / Clair — pendant du sélecteur de langue, avec lequel elle forme une paire
 * dans l'en-tête : même [Surface], même `RoundedCornerShape(10.dp)`, même contour d'1 dp.
 *
 * **Un seul bouton, non deux segments**, à la différence de [LangToggle] : le cahier des charges
 * fige le tag au singulier (`theme_toggle_btn`), et l'en-tête d'un téléphone ne peut pas se
 * permettre une seconde commande à deux segments à côté du sélecteur FR/EN (leçon de largeur de
 * l'US-20 — c'est ce que vérifient les niveaux 3).
 *
 * L'icône annonce la **destination** de l'appui et non l'état courant : soleil en thème sombre
 * (« appuyer m'amène au clair »), lune en thème clair. Un bouton d'action se lit par ce qu'il
 * fait ; c'est aussi ce que dit son `contentDescription`, seule chose que TalkBack énonce puisque
 * le bouton n'a pas de libellé.
 *
 * @param mode préférence courante — c'est elle qui est basculée, y compris depuis `SYSTEM`.
 * @param resolved apparence effective, dont `SYSTEM` est déjà résolu. Passée plutôt que relue ici :
 *   le composant reste alors montable hors de [LedgerHubTheme] pour un test de composant isolé.
 */
@Composable
fun ThemeToggle(
    mode: ThemeMode,
    resolved: ResolvedTheme,
    onToggle: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val goesToLight = resolved == ResolvedTheme.DARK
    val description = tr(if (goesToLight) StringKey.THEME_TOGGLE_TO_LIGHT else StringKey.THEME_TOGGLE_TO_DARK)

    Surface(
        color = LedgerHubTheme.palette.Surface,
        contentColor = LedgerHubTheme.palette.SecondaryText,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        // Un seul noeud sémantique pour tout le bouton : TalkBack annonce « bouton » puis la
        // destination de l'appui, au lieu d'énumérer un conteneur et une icône. C'est aussi ce
        // noeud que mesurent et que touchent les niveaux 3.
        modifier = modifier.semantics(mergeDescendants = true) {
            testTag = ThemeToggleTags.ROOT
            this.contentDescription = description
            role = Role.Button
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(onClickLabel = description) {
                    onToggle(mode.toggled(systemIsDark = resolved == ResolvedTheme.DARK))
                }
                // Le contour ne fait que 40 dp de haut dans l'en-tête ; c'est la ZONE CLIQUABLE
                // qui doit atteindre 48 dp, et c'est elle que le test mesure.
                .sizeIn(minWidth = MinimumTouchTarget, minHeight = MinimumTouchTarget),
        ) {
            Icon(
                imageVector = if (goesToLight) LedgerHubSunIcon else LedgerHubMoonIcon,
                // Décorative : le bouton parent porte déjà la description, la répéter ferait
                // énoncer deux fois la même chose.
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .semantics {
                        testTag = if (goesToLight) ThemeToggleTags.ICON_SUN else ThemeToggleTags.ICON_MOON
                    },
            )
        }
    }
}
