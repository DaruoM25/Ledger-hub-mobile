package com.ledgerhub.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème sombre premium global de LedgerHub Mobile — mappe les design tokens de l'app Web
 * ([LedgerHubColors], repris de Tailwind slate/blue) sur un [androidx.compose.material3.ColorScheme]
 * Material 3. Appliqué une seule fois à la racine (voir `App.kt`) : tous les écrans en héritent,
 * exactement comme le Web sert un seul thème sombre.
 *
 * Aucune couleur n'est codée en dur dans les écrans — ils lisent `MaterialTheme.colorScheme`,
 * ce qui garde le graphe du Dashboard et les cartes cohérents si la palette évolue.
 */
private val LedgerHubDarkColorScheme = darkColorScheme(
    primary = LedgerHubColors.Accent,
    onPrimary = Color.White,
    primaryContainer = LedgerHubColors.StatusPaidBg,
    onPrimaryContainer = LedgerHubColors.StatusPaidFg,
    secondary = LedgerHubColors.SecondaryText,
    onSecondary = Color.White,
    tertiary = LedgerHubColors.StatusPendingFg,
    tertiaryContainer = LedgerHubColors.StatusPendingBg,
    onTertiaryContainer = LedgerHubColors.StatusPendingFg,
    background = LedgerHubColors.Background,
    onBackground = LedgerHubColors.PrimaryText,
    surface = LedgerHubColors.Surface,
    onSurface = LedgerHubColors.PrimaryText,
    surfaceVariant = LedgerHubColors.InputBackground,
    onSurfaceVariant = LedgerHubColors.SecondaryText,
    outline = LedgerHubColors.Border,
    outlineVariant = LedgerHubColors.Border,
    error = LedgerHubColors.ErrorText,
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFCA5A5),
)

/** Typo M3 par défaut — placeholder centralisé pour d'éventuels ajustements de familles/poids. */
private val LedgerHubTypography = Typography()

@Composable
fun LedgerHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LedgerHubDarkColorScheme,
        typography = LedgerHubTypography,
        content = content,
    )
}

/** Tonalité d'un badge de statut — découple l'UI des enums métier (InvoiceStatus, QuoteStatus). */
enum class InvoiceStatusTone { PAID, PENDING, DRAFT }

/**
 * Couleurs (fond, texte) d'un badge de statut — **source unique** pour tout l'app.
 * Emerald = payé/encaissé, Amber = en attente/en retard, Slate = brouillon/neutre.
 */
fun statusColors(tone: InvoiceStatusTone): Pair<Color, Color> = when (tone) {
    InvoiceStatusTone.PAID -> LedgerHubColors.StatusPaidBg to LedgerHubColors.StatusPaidFg
    InvoiceStatusTone.PENDING -> LedgerHubColors.StatusPendingBg to LedgerHubColors.StatusPendingFg
    InvoiceStatusTone.DRAFT -> LedgerHubColors.StatusDraftBg to LedgerHubColors.StatusDraftFg
}
