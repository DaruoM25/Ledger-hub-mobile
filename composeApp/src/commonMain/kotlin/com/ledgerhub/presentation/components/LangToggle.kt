package com.ledgerhub.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Sélecteur de langue compact (36 dp) : deux segments `FR` | `EN`.
 * L'option active est enveloppée d'un pill bleu [LedgerHubTheme.palette.Accent].
 */
@Composable
fun LangToggle(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = LedgerHubTheme.palette.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LedgerHubTheme.palette.Border),
        modifier = modifier
            .height(36.dp)
            .semantics { testTag = LangToggleTags.ROOT },
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LangSegment(
                label = AppLanguage.FR.shortLabel,
                tag = LangToggleTags.FR,
                selected = current == AppLanguage.FR,
                onClick = { onSelect(AppLanguage.FR) },
            )
            LangSegment(
                label = AppLanguage.EN.shortLabel,
                tag = LangToggleTags.EN,
                selected = current == AppLanguage.EN,
                onClick = { onSelect(AppLanguage.EN) },
            )
        }
    }
}

/**
 * Demi-bouton du sélecteur compact.
 */
@Composable
private fun LangSegment(
    label: String,
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) LedgerHubTheme.palette.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { testTag = tag },
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else LedgerHubTheme.palette.SecondaryText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

