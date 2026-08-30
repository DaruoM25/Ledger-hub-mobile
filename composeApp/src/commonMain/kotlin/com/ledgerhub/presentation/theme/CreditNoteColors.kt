package com.ledgerhub.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette dédiée à l'écran d'avoir (US-10) — dominante **violet / indigo / ardoise**, distincte du
 * bleu standard des factures ([LedgerHubColors]). Appliquée localement au seul
 * [CreditNoteFormScreen][com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen] via
 * [CreditNoteTheme] — précédent : `LoginScreen` a lui aussi un thème M3 imbriqué.
 *
 * Tokens repris de Tailwind violet/indigo/slate.
 */
object CreditNoteColors {
    /** Tailwind `slate-950` — fond général de l'écran. */
    val Background = Color(0xFF030712)

    /** Tailwind `slate-900` — cartes / panneaux. */
    val Surface = Color(0xFF0F172A)

    /** Barre supérieure — Tailwind `indigo-900`. */
    val HeaderBar = Color(0xFF312E81)

    /** Accent (bouton principal) — Tailwind `violet-600`. */
    val Accent = Color(0xFF7C3AED)

    /** Fond du badge « AVOIR EN BROUILLON » — Tailwind `violet-100`. */
    val BadgeBg = Color(0xFFEDE9FE)

    /** Texte du badge — Tailwind `violet-800`. */
    val BadgeFg = Color(0xFF5B21B6)

    /** Bordure des cartes — Tailwind `violet-200` assombri pour le fond sombre. */
    val CardBorder = Color(0xFF4C1D95)

    /** Fond du cartouche des totaux — Tailwind `violet-950`. */
    val CartridgeBg = Color(0xFF2E1065)

    /** Bordure du cartouche — Tailwind `violet-500`. */
    val CartridgeBorder = Color(0xFF8B5CF6)

    /** Montant crédité (négatif) — Tailwind `rose-400`. */
    val CreditNegative = Color(0xFFFB7185)

    /** Texte principal clair. */
    val PrimaryText = Color(0xFFF8FAFC)

    /** Texte secondaire — Tailwind `violet-300`. */
    val SecondaryText = Color(0xFFC4B5FD)
}

private val CreditNoteColorScheme = darkColorScheme(
    primary = CreditNoteColors.Accent,
    onPrimary = Color.White,
    primaryContainer = CreditNoteColors.BadgeBg,
    onPrimaryContainer = CreditNoteColors.BadgeFg,
    background = CreditNoteColors.Background,
    onBackground = CreditNoteColors.PrimaryText,
    surface = CreditNoteColors.Surface,
    onSurface = CreditNoteColors.PrimaryText,
    surfaceVariant = CreditNoteColors.Surface,
    onSurfaceVariant = CreditNoteColors.SecondaryText,
    outline = CreditNoteColors.CardBorder,
    outlineVariant = CreditNoteColors.CardBorder,
    error = CreditNoteColors.CreditNegative,
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFCA5A5),
)

/** Thème M3 local à l'écran d'avoir — dominante violet/indigo. */
@Composable
fun CreditNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CreditNoteColorScheme, content = content)
}
