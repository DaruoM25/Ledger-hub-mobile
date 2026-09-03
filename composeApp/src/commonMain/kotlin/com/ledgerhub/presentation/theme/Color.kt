package com.ledgerhub.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette sombre "slate" — reprend à l'identique les design tokens de l'app Web de référence
 * (Tailwind slate/blue).
 *
 * Depuis l'US-25, ce n'est plus la seule apparence de l'app mais **la déclinaison sombre** d'une
 * paire : les écrans ne lisent plus cet objet directement, ils lisent `LedgerHubTheme.palette`,
 * que [LedgerHubTheme] alimente avec [LedgerHubPalette.Dark] (bâtie sur ces constantes) ou
 * [LedgerHubPalette.Light] (voir [LedgerHubLightColors]).
 *
 * [LoginScreen][com.ledgerhub.presentation.auth.LoginScreen] garde encore son thème M3 local
 * imbriqué (identique visuellement) tant que le flux d'authentification n'est pas re-câblé — il
 * reste donc sombre quel que soit le thème choisi (dette consignée à l'audit de l'US-25).
 */
object LedgerHubColors {
    /** Tailwind `bg-slate-950` — fond général de l'app. */
    val Background = Color(0xFF030712)

    /** Tailwind `bg-slate-900` — cartes, sidebar, panneaux. */
    val Surface = Color(0xFF0F172A)

    /** Fond des champs de saisie — Tailwind `bg-slate-950` (distingué de [Background] par [Border]). */
    val InputBackground = Color(0xFF020617)

    /** Bordure fine des champs et des cartes — Tailwind `border-slate-800`. */
    val InputBorder = Color(0xFF1E293B)

    /** Alias sémantique de [InputBorder] pour les contours de cartes/séparateurs. */
    val Border = Color(0xFF1E293B)

    /** Couleur d'accent (bouton principal, liens, courbe du graphe) — Tailwind `bg-blue-600`. */
    val Accent = Color(0xFF2563EB)

    /** Texte principal sur fond sombre. */
    val PrimaryText = Color(0xFFF8FAFC)

    /** Texte secondaire (labels, sous-titres) — Tailwind `text-slate-400`. */
    val SecondaryText = Color(0xFF94A3B8)

    /** Variation positive ("+12,4 % vs mois dernier") — Tailwind `text-emerald-400`. */
    val PositiveText = Color(0xFF34D399)

    /** Erreurs / champs invalides — Tailwind `text-red-400`. */
    val ErrorText = Color(0xFFF87171)

    // ── Badges de statut (fond + texte) — voir [statusColors] ─────────────────────
    /** Payé — Tailwind emerald. */
    val StatusPaidBg = Color(0xFF064E3B)
    val StatusPaidFg = Color(0xFF6EE7B7)

    /** En attente / En retard — Tailwind amber. */
    val StatusPendingBg = Color(0xFF78350F)
    val StatusPendingFg = Color(0xFFFCD34D)

    /** Brouillon / neutre — Tailwind slate. */
    val StatusDraftBg = Color(0xFF1E293B)
    val StatusDraftFg = Color(0xFF94A3B8)

    // ── Activité commerciale des devis (US-12) ────────────────────────────────────
    /**
     * Indigo profond du KPI « Devis en attente » — Tailwind `indigo-900` / `indigo-400`.
     * Volontairement distinct de l'[Accent] bleu des factures, du vert des encaissements
     * ([StatusPaidBg]) et du violet des avoirs (`CreditNoteColors`).
     */
    val QuotePendingBg = Color(0xFF312E81)
    val QuotePendingFg = Color(0xFF818CF8)
}

/**
 * Palette claire — miroir strict de [LedgerHubColors], token pour token (US-25).
 *
 * ## Règle de déclinaison
 *
 * Les surfaces et les textes sont **inversés sur l'échelle Tailwind slate** : `slate-950` devient
 * `slate-50`, `slate-900` devient `white`, `slate-400` devient `slate-600`. L'accent, lui, ne
 * bouge pas — `blue-600` est l'identité de la marque, pas une teinte d'ambiance, et le décliner
 * ferait de la bascule un changement de marque.
 *
 * Les badges de statut sont **inversés** (fond pâle, texte saturé) plutôt qu'éclaircis : c'est la
 * convention Tailwind du Web de référence, et un `emerald-900` posé sur du blanc serait illisible.
 * Même logique pour les couleurs sémantiques, dont la version claire est plus **sombre** que la
 * sombre (`emerald-400` → `emerald-600`) : sur fond clair, le contraste se gagne en descendant
 * l'échelle, pas en la remontant.
 */
object LedgerHubLightColors {
    /** Tailwind `bg-slate-50` — fond général de l'app (miroir de `slate-950`). */
    val Background = Color(0xFFF8FAFC)

    /** Blanc pur — cartes, sidebar, panneaux (miroir de `slate-900`). */
    val Surface = Color(0xFFFFFFFF)

    /** Fond des champs de saisie — Tailwind `slate-100`, distingué de [Surface] par [Border]. */
    val InputBackground = Color(0xFFF1F5F9)

    /** Bordure fine des champs et des cartes — Tailwind `slate-300`. */
    val InputBorder = Color(0xFFCBD5E1)

    /** Alias sémantique de [InputBorder] pour les contours de cartes/séparateurs. */
    val Border = Color(0xFFCBD5E1)

    /** Accent — `blue-600`, **identique au thème sombre** : couleur de marque, pas d'ambiance. */
    val Accent = Color(0xFF2563EB)

    /** Texte principal sur fond clair — Tailwind `slate-900`. */
    val PrimaryText = Color(0xFF0F172A)

    /** Texte secondaire (labels, sous-titres) — Tailwind `slate-600`. */
    val SecondaryText = Color(0xFF475569)

    /** Variation positive — Tailwind `emerald-600` (descendu pour contraster sur du clair). */
    val PositiveText = Color(0xFF059669)

    /** Erreurs / champs invalides — Tailwind `red-600`. */
    val ErrorText = Color(0xFFDC2626)

    // ── Badges de statut (fond + texte) — voir [statusColors] ─────────────────────
    /** Payé — Tailwind emerald 100/800. */
    val StatusPaidBg = Color(0xFFD1FAE5)
    val StatusPaidFg = Color(0xFF065F46)

    /** En attente / En retard — Tailwind amber 100/800. */
    val StatusPendingBg = Color(0xFFFEF3C7)
    val StatusPendingFg = Color(0xFF92400E)

    /** Brouillon / neutre — Tailwind slate 200/600. */
    val StatusDraftBg = Color(0xFFE2E8F0)
    val StatusDraftFg = Color(0xFF475569)

    /** Devis en attente — Tailwind indigo 100/700, distinct de l'accent bleu comme en sombre. */
    val QuotePendingBg = Color(0xFFE0E7FF)
    val QuotePendingFg = Color(0xFF4338CA)
}
