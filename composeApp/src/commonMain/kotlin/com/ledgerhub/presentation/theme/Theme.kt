package com.ledgerhub.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.theme.ResolvedTheme
import com.ledgerhub.domain.theme.ThemeMode

/**
 * Thème global de LedgerHub Mobile — mappe les design tokens de l'app Web ([LedgerHubPalette],
 * repris de Tailwind slate/blue) sur un [ColorScheme] Material 3. Appliqué une seule fois à la
 * racine (voir `App.kt`) : tous les écrans en héritent.
 *
 * Depuis l'US-25 il est **paramétré** par un [ThemeMode] : le sombre premium historique, un clair
 * en miroir strict, ou le suivi du réglage système. Deux `ColorScheme` mais **un seul mapping**
 * ([toColorScheme]) : sombre et clair ne peuvent pas diverger dans les rôles Material qu'ils
 * remplissent, seulement dans les teintes qu'ils y versent.
 *
 * Aucune couleur n'est codée en dur dans les écrans — ils lisent `MaterialTheme.colorScheme` ou
 * `LedgerHubTheme.palette`, ce qui rend la bascule totale plutôt que partielle.
 */
@Composable
fun LedgerHubTheme(
    mode: ThemeMode = ThemeMode.Default,
    content: @Composable () -> Unit,
) {
    // Seul point de l'app qui interroge l'apparence du système. Le domaine, lui, reçoit ce booléen
    // en paramètre (ThemeMode.resolve) : sa règle reste testable sans Compose ni émulateur.
    val resolved = mode.resolve(systemIsDark = isSystemInDarkTheme())
    val palette = when (resolved) {
        ResolvedTheme.DARK -> LedgerHubPalette.Dark
        ResolvedTheme.LIGHT -> LedgerHubPalette.Light
    }

    CompositionLocalProvider(
        LocalLedgerHubPalette provides palette,
        LocalResolvedTheme provides resolved,
    ) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = LedgerHubTypography,
            content = content,
        )
    }
}

/**
 * Accès aux design tokens LedgerHub depuis n'importe quel composable sous [LedgerHubTheme] —
 * ce que `MaterialTheme.colorScheme` est aux rôles Material, `LedgerHubTheme.palette` l'est aux
 * tokens propres au produit (badges de statut, fond de champ, indigo des devis…).
 *
 * Fonction et objet homonymes : c'est le patron de `MaterialTheme` lui-même en Material 3, deux
 * espaces de noms distincts en Kotlin.
 */
object LedgerHubTheme {
    val palette: LedgerHubPalette
        @Composable @ReadOnlyComposable get() = LocalLedgerHubPalette.current

    /** Apparence effective — `SYSTEM` y est déjà résolu en clair ou sombre. */
    val resolved: ResolvedTheme
        @Composable @ReadOnlyComposable get() = LocalResolvedTheme.current
}

/**
 * Mapping unique des tokens LedgerHub sur les rôles Material 3.
 *
 * `darkColorScheme`/`lightColorScheme` diffèrent par leurs valeurs par défaut — celles des rôles
 * qu'on ne surcharge pas (`scrim`, `inverseSurface`, `surfaceContainer*`…). Choisir la bonne base
 * évite qu'un composant Material non stylé par nous (menu déroulant, infobulle, Snackbar) ne
 * ressorte sombre au milieu d'un écran clair.
 */
private fun LedgerHubPalette.toColorScheme(): ColorScheme {
    val base = if (this == LedgerHubPalette.Light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = StatusPaidBg,
        onPrimaryContainer = StatusPaidFg,
        secondary = SecondaryText,
        onSecondary = OnAccent,
        tertiary = StatusPendingFg,
        tertiaryContainer = StatusPendingBg,
        onTertiaryContainer = StatusPendingFg,
        background = Background,
        onBackground = PrimaryText,
        surface = Surface,
        onSurface = PrimaryText,
        surfaceVariant = InputBackground,
        onSurfaceVariant = SecondaryText,
        outline = Border,
        outlineVariant = Border,
        error = ErrorText,
        onError = Color.White,
        errorContainer = if (this == LedgerHubPalette.Light) Color(0xFFFEE2E2) else Color(0xFF7F1D1D),
        onErrorContainer = if (this == LedgerHubPalette.Light) Color(0xFF991B1B) else Color(0xFFFCA5A5),
    )
}

/** Typo M3 par défaut — placeholder centralisé pour d'éventuels ajustements de familles/poids. */
private val LedgerHubTypography = Typography()

/** Tonalité d'un badge de statut — découple l'UI des enums métier (InvoiceStatus, QuoteStatus). */
enum class InvoiceStatusTone { PAID, PENDING, DRAFT }

/**
 * Couleurs (fond, texte) d'un badge de statut — **source unique** pour tout l'app.
 * Emerald = payé/encaissé, Amber = en attente/en retard, Slate = brouillon/neutre.
 *
 * Devenue `@Composable` en US-25 : les teintes dépendent désormais du thème actif, une fonction
 * pure ne pouvait plus les rendre.
 */
@Composable
@ReadOnlyComposable
fun statusColors(tone: InvoiceStatusTone): Pair<Color, Color> {
    val palette = LocalLedgerHubPalette.current
    return when (tone) {
        InvoiceStatusTone.PAID -> palette.StatusPaidBg to palette.StatusPaidFg
        InvoiceStatusTone.PENDING -> palette.StatusPendingBg to palette.StatusPendingFg
        InvoiceStatusTone.DRAFT -> palette.StatusDraftBg to palette.StatusDraftFg
    }
}
