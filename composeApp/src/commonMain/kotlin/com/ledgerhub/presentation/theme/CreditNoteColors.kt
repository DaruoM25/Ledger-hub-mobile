package com.ledgerhub.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.theme.ResolvedTheme

/**
 * Palette dédiée à l'écran d'avoir (US-10) — dominante **violet / indigo / ardoise**, distincte du
 * bleu standard des factures ([LedgerHubPalette]). Appliquée localement au seul
 * [CreditNoteFormScreen][com.ledgerhub.presentation.creditnoteform.CreditNoteFormScreen] via
 * [CreditNoteTheme] — précédent : `AuthScreen` a lui aussi un thème M3 imbriqué.
 *
 * Depuis l'US-25, elle suit la bascule globale (voir [CreditNotePalette]) : un thème imbriqué resté
 * sombre aurait fait de l'écran d'avoir un trou noir au milieu d'une application claire.
 *
 * Tokens repris de Tailwind violet/indigo/slate.
 */
data class CreditNotePalette(
    val Background: Color,
    val Surface: Color,
    val HeaderBar: Color,
    val Accent: Color,
    val BadgeBg: Color,
    val BadgeFg: Color,
    val CardBorder: Color,
    val CartridgeBg: Color,
    val CartridgeBorder: Color,
    val CreditNegative: Color,
    val PrimaryText: Color,
    val SecondaryText: Color,
) {
    companion object {
        /** Déclinaison sombre — les tokens de l'US-10, inchangés. */
        val Dark: CreditNotePalette = CreditNotePalette(
            Background = Color(0xFF030712),      // slate-950
            Surface = Color(0xFF0F172A),         // slate-900
            HeaderBar = Color(0xFF312E81),       // indigo-900
            Accent = Color(0xFF7C3AED),          // violet-600
            BadgeBg = Color(0xFFEDE9FE),         // violet-100
            BadgeFg = Color(0xFF5B21B6),         // violet-800
            CardBorder = Color(0xFF4C1D95),      // violet-900
            CartridgeBg = Color(0xFF2E1065),     // violet-950
            CartridgeBorder = Color(0xFF8B5CF6), // violet-500
            CreditNegative = Color(0xFFFB7185),  // rose-400
            PrimaryText = Color(0xFFF8FAFC),
            SecondaryText = Color(0xFFC4B5FD),   // violet-300
        )

        /**
         * Déclinaison claire — même règle que [LedgerHubLightColors] : surfaces inversées sur
         * l'échelle, accent de marque conservé (`violet-600`), teintes sémantiques **descendues**
         * pour contraster sur du clair (`rose-400` → `rose-600`, `violet-300` → `violet-700`).
         *
         * Le badge « AVOIR EN BROUILLON » est le seul token inchangé : il était déjà clair
         * (`violet-100` sur `violet-800`), il l'est resté.
         */
        val Light: CreditNotePalette = CreditNotePalette(
            Background = Color(0xFFF5F3FF),      // violet-50
            Surface = Color(0xFFFFFFFF),
            HeaderBar = Color(0xFF4F46E5),       // indigo-600
            Accent = Color(0xFF7C3AED),          // violet-600 — identité, non déclinée
            BadgeBg = Color(0xFFEDE9FE),         // violet-100
            BadgeFg = Color(0xFF5B21B6),         // violet-800
            CardBorder = Color(0xFFDDD6FE),      // violet-200
            CartridgeBg = Color(0xFFEDE9FE),     // violet-100
            CartridgeBorder = Color(0xFF8B5CF6), // violet-500
            CreditNegative = Color(0xFFE11D48),  // rose-600
            PrimaryText = Color(0xFF1E1B4B),     // indigo-950
            SecondaryText = Color(0xFF6D28D9),   // violet-700
        )
    }
}

/** Palette violette active — voir [LocalLedgerHubPalette] pour le choix de `staticCompositionLocalOf`. */
val LocalCreditNotePalette = staticCompositionLocalOf { CreditNotePalette.Dark }

/**
 * Thème M3 local à l'écran d'avoir — dominante violet/indigo, déclinée selon l'apparence effective
 * héritée de [LedgerHubTheme].
 */
@Composable
fun CreditNoteTheme(content: @Composable () -> Unit) {
    val palette = when (LocalResolvedTheme.current) {
        ResolvedTheme.DARK -> CreditNotePalette.Dark
        ResolvedTheme.LIGHT -> CreditNotePalette.Light
    }
    CompositionLocalProvider(LocalCreditNotePalette provides palette) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}

/** Accès aux tokens violets depuis l'écran d'avoir — pendant de `LedgerHubTheme.palette`. */
object CreditNoteTheme {
    val palette: CreditNotePalette
        @Composable @ReadOnlyComposable get() = LocalCreditNotePalette.current
}

/** Mapping unique des tokens violets sur les rôles Material 3 — voir `LedgerHubPalette.toColorScheme`. */
private fun CreditNotePalette.toColorScheme(): ColorScheme {
    val base = if (this == CreditNotePalette.Light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = Accent,
        onPrimary = Color.White,
        primaryContainer = BadgeBg,
        onPrimaryContainer = BadgeFg,
        background = Background,
        onBackground = PrimaryText,
        surface = Surface,
        onSurface = PrimaryText,
        surfaceVariant = Surface,
        onSurfaceVariant = SecondaryText,
        outline = CardBorder,
        outlineVariant = CardBorder,
        error = CreditNegative,
        onError = Color.White,
        errorContainer = if (this == CreditNotePalette.Light) Color(0xFFFEE2E2) else Color(0xFF7F1D1D),
        onErrorContainer = if (this == CreditNotePalette.Light) Color(0xFF991B1B) else Color(0xFFFCA5A5),
    )
}
