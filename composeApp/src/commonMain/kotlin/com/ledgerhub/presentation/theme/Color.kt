package com.ledgerhub.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette sombre "slate" — reprend à l'identique les design tokens de l'app Web de référence
 * (Tailwind slate/blue). Consommée globalement via [LedgerHubTheme] : tout l'écran de l'app
 * (Dashboard, Formulaire, shell de navigation) est désormais sombre, comme le Web.
 * [LoginScreen][com.ledgerhub.presentation.auth.LoginScreen] garde encore son thème M3 local
 * imbriqué (identique visuellement) tant que le flux d'authentification n'est pas re-câblé.
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
