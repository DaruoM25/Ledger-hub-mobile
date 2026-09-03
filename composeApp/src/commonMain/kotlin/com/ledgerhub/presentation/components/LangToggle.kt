package com.ledgerhub.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.theme.LedgerHubTheme

object LangToggleTags {
    const val ROOT = "lang_toggle"
    const val FR = "lang_toggle_fr"
    const val EN = "lang_toggle_en"
}

/**
 * Sélecteur de langue « tactique » : deux demi-boutons `🇫🇷 FR` | `🇬🇧 EN`. L'actif est peint en
 * [LedgerHubTheme.palette.Accent], l'inactif reste discret sur le thème slate-950. Un clic sur un segment
 * bascule immédiatement la langue (l'état est hissé dans `App.kt`).
 */
@Composable
fun LangToggle(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = LedgerHubTheme.palette.Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = modifier.semantics { testTag = LangToggleTags.ROOT },
    ) {
        Row {
            LangSegment(
                label = "${AppLanguage.FR.flag} ${AppLanguage.FR.shortLabel}",
                tag = LangToggleTags.FR,
                selected = current == AppLanguage.FR,
                onClick = { onSelect(AppLanguage.FR) },
            )
            LangSegment(
                label = "${AppLanguage.EN.flag} ${AppLanguage.EN.shortLabel}",
                tag = LangToggleTags.EN,
                selected = current == AppLanguage.EN,
                onClick = { onSelect(AppLanguage.EN) },
            )
        }
    }
}

/** Cible tactile minimale Material — voir le commentaire de [LangSegment]. */
private val MinimumTouchTarget = 48.dp

/**
 * Demi-bouton du sélecteur.
 *
 * La zone cliquable porte une taille minimale **requise** de 48 dp : un `sizeIn` ordinaire reste
 * borné par les contraintes du parent, et l'en-tête compact a montré (US-25, Pixel 5) qu'un parent
 * saturé comprime ses derniers enfants en silence plutôt que de le signaler. Le libellé, lui, reste
 * sur une seule ligne — un retour à la ligne sur « 🇫🇷 FR » ferait grandir tout l'en-tête.
 */
@Composable
private fun LangSegment(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) LedgerHubTheme.palette.Accent else Color.Transparent)
            .requiredSizeIn(minWidth = MinimumTouchTarget, minHeight = MinimumTouchTarget)
            // Padding réduit à sa portion congrue : c'est le plancher tactile de 48 dp qui donne
            // sa largeur au segment, pas cette marge. La conserver à 12 dp gonflait le sélecteur
            // de 25 dp sans rien ajouter à la cible — au prix du nom de l'application, rogné dans
            // l'en-tête d'un Pixel 5. Le texte est centré dans la cible, pas collé à son bord.
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .semantics { testTag = tag },
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else LedgerHubTheme.palette.SecondaryText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
