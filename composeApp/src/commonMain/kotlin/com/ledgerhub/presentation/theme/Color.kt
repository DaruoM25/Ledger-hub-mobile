package com.ledgerhub.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette sombre "slate" — reprend l'identité visuelle du site web (Tailwind slate/blue).
 * Utilisée pour l'instant par l'écran de connexion uniquement (voir [LoginScreen][com.ledgerhub.presentation.auth.LoginScreen]) :
 * le reste de l'app garde le thème M3 clair par défaut tant que le pivot vers un thème sombre
 * global n'a pas été décidé pour l'ensemble des écrans déjà livrés.
 */
object LedgerHubColors {
    /** Tailwind `bg-slate-950`. */
    val Background = Color(0xFF030712)

    /** Tailwind `bg-slate-900/50`. */
    val Surface = Color(0xFF0F172A)

    /** Fond des champs de saisie — Tailwind `bg-slate-950` (même teinte que le fond, distingué par [InputBorder]). */
    val InputBackground = Color(0xFF020617)

    /** Bordure des champs de saisie — Tailwind `border-slate-800`. */
    val InputBorder = Color(0xFF1E293B)

    /** Couleur d'accent (bouton principal, liens) — Tailwind `bg-blue-600`. */
    val Accent = Color(0xFF2563EB)

    /** Texte secondaire (labels, sous-titres) — Tailwind `text-slate-400`. */
    val SecondaryText = Color(0xFF94A3B8)
}
