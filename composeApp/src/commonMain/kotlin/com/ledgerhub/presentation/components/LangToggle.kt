package com.ledgerhub.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.theme.LedgerHubColors

object LangToggleTags {
    const val ROOT = "lang_toggle"
    const val FR = "lang_toggle_fr"
    const val EN = "lang_toggle_en"
}

/**
 * Sélecteur de langue « tactique » : deux demi-boutons `🇫🇷 FR` | `🇬🇧 EN`. L'actif est peint en
 * [LedgerHubColors.Accent], l'inactif reste discret sur le thème slate-950. Un clic sur un segment
 * bascule immédiatement la langue (l'état est hissé dans `App.kt`).
 */
@Composable
fun LangToggle(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = LedgerHubColors.Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LedgerHubColors.Border),
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

@Composable
private fun LangSegment(label: String, tag: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.White else LedgerHubColors.SecondaryText,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(if (selected) LedgerHubColors.Accent else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { testTag = tag },
    )
}
