package com.ledgerhub.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ledgerhub.domain.theme.ResolvedTheme

/**
 * Jeu complet des design tokens de LedgerHub, en **deux déclinaisons** : sombre (l'apparence
 * historique de l'app) et claire (US-25).
 *
 * ## Pourquoi une classe et non deux objets
 *
 * Jusqu'à l'US-25, les écrans lisaient `LedgerHubColors.Accent` : une constante, donc une couleur
 * figée à la compilation, qu'aucune bascule de thème ne pouvait atteindre. Mapper les tokens sur
 * un `ColorScheme` Material 3 ne suffisait pas — 140 lectures directes réparties dans dix écrans
 * seraient restées sombres sur fond clair.
 *
 * Les tokens deviennent donc les champs d'une valeur, publiée dans l'arbre par
 * [LocalLedgerHubPalette] et lue via `LedgerHubTheme.palette`. Les **noms sont conservés à
 * l'identique** ([Accent], [SecondaryText]…) : la bascule des sites d'appel est un changement de
 * préfixe, pas une réécriture, donc aucune inversion de token n'a pu s'y glisser.
 *
 * Les valeurs sombres ne sont pas retapées : [Dark] référence [LedgerHubColors], seule source des
 * teintes en place depuis la v1. Le thème existant est donc inchangé au bit près.
 */
data class LedgerHubPalette(
    val Background: Color,
    val Surface: Color,
    val InputBackground: Color,
    val InputBorder: Color,
    val Border: Color,
    val Accent: Color,
    val PrimaryText: Color,
    val SecondaryText: Color,
    val PositiveText: Color,
    val ErrorText: Color,
    val StatusPaidBg: Color,
    val StatusPaidFg: Color,
    val StatusPendingBg: Color,
    val StatusPendingFg: Color,
    val StatusDraftBg: Color,
    val StatusDraftFg: Color,
    val QuotePendingBg: Color,
    val QuotePendingFg: Color,
    /** Contraste du texte posé sur [Accent] — blanc dans les deux thèmes, l'accent restant `blue-600`. */
    val OnAccent: Color,
) {
    companion object {
        /** Déclinaison sombre — les tokens Tailwind slate/blue de la v1, inchangés. */
        val Dark: LedgerHubPalette = LedgerHubPalette(
            Background = LedgerHubColors.Background,
            Surface = LedgerHubColors.Surface,
            InputBackground = LedgerHubColors.InputBackground,
            InputBorder = LedgerHubColors.InputBorder,
            Border = LedgerHubColors.Border,
            Accent = LedgerHubColors.Accent,
            PrimaryText = LedgerHubColors.PrimaryText,
            SecondaryText = LedgerHubColors.SecondaryText,
            PositiveText = LedgerHubColors.PositiveText,
            ErrorText = LedgerHubColors.ErrorText,
            StatusPaidBg = LedgerHubColors.StatusPaidBg,
            StatusPaidFg = LedgerHubColors.StatusPaidFg,
            StatusPendingBg = LedgerHubColors.StatusPendingBg,
            StatusPendingFg = LedgerHubColors.StatusPendingFg,
            StatusDraftBg = LedgerHubColors.StatusDraftBg,
            StatusDraftFg = LedgerHubColors.StatusDraftFg,
            QuotePendingBg = LedgerHubColors.QuotePendingBg,
            QuotePendingFg = LedgerHubColors.QuotePendingFg,
            OnAccent = Color.White,
        )

        /** Déclinaison claire — voir [LedgerHubLightColors] pour le détail des correspondances. */
        val Light: LedgerHubPalette = LedgerHubPalette(
            Background = LedgerHubLightColors.Background,
            Surface = LedgerHubLightColors.Surface,
            InputBackground = LedgerHubLightColors.InputBackground,
            InputBorder = LedgerHubLightColors.InputBorder,
            Border = LedgerHubLightColors.Border,
            Accent = LedgerHubLightColors.Accent,
            PrimaryText = LedgerHubLightColors.PrimaryText,
            SecondaryText = LedgerHubLightColors.SecondaryText,
            PositiveText = LedgerHubLightColors.PositiveText,
            ErrorText = LedgerHubLightColors.ErrorText,
            StatusPaidBg = LedgerHubLightColors.StatusPaidBg,
            StatusPaidFg = LedgerHubLightColors.StatusPaidFg,
            StatusPendingBg = LedgerHubLightColors.StatusPendingBg,
            StatusPendingFg = LedgerHubLightColors.StatusPendingFg,
            StatusDraftBg = LedgerHubLightColors.StatusDraftBg,
            StatusDraftFg = LedgerHubLightColors.StatusDraftFg,
            QuotePendingBg = LedgerHubLightColors.QuotePendingBg,
            QuotePendingFg = LedgerHubLightColors.QuotePendingFg,
            OnAccent = Color.White,
        )
    }
}

/**
 * Palette active, publiée par [LedgerHubTheme] à la racine et lue via `LedgerHubTheme.palette`.
 *
 * `staticCompositionLocalOf` et non `compositionLocalOf` — à l'inverse de `LocalAppLanguage` :
 * une bascule de thème repeint **tout** l'écran, il n'y a donc rien à gagner à suivre finement
 * les lecteurs, et le suivi coûterait sur chaque recomposition ordinaire.
 *
 * Défaut [LedgerHubPalette.Dark] : un composant sorti de son thème (aperçu isolé, test unitaire de
 * composant) garde l'apparence historique de l'app plutôt que de tomber en noir sur noir.
 */
val LocalLedgerHubPalette = staticCompositionLocalOf { LedgerHubPalette.Dark }

/**
 * Apparence **effective** en vigueur dans cette portion de l'arbre — publiée par [LedgerHubTheme]
 * en même temps que [LocalLedgerHubPalette].
 *
 * Nécessaire là où la palette ne suffit pas à répondre « clair ou sombre ? » : le bouton de
 * bascule, qui doit annoncer la destination de l'appui, et les thèmes imbriqués (`CreditNoteTheme`)
 * qui déclinent leur propre gamme. Comparer la palette courante à `LedgerHubPalette.Light` y
 * répondrait aussi, mais par un effet de bord — cette information mérite d'être dite, pas déduite.
 */
val LocalResolvedTheme = staticCompositionLocalOf { ResolvedTheme.DARK }
