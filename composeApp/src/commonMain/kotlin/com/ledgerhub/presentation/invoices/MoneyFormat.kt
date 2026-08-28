package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.invoice.Money
import kotlin.math.abs

/** Espace insécable (U+00A0) — séparateur de milliers, convention typographique française. */
val NON_BREAKING_SPACE: String = ' '.toString()

/**
 * Formate un [Money] (centimes) en montant monétaire français : séparateur de milliers par
 * espace insécable, virgule décimale, symbole € suffixé. Ex : `Money(123456)` -> `"1 234,56 €"`.
 *
 * commonMain KMP : ni `java.text.NumberFormat` ni `String.format("%,.2f", …)` ne sont
 * disponibles, d'où le groupement manuel des milliers. Centralisé ici pour remplacer les
 * `formatCents` privés dupliqués dans les écrans existants.
 */
fun Money.formatEuros(): String = "${formatCentsGrouped(cents)}$NON_BREAKING_SPACE€"

/** Cœur du formatage, testable sans dépendance Compose. */
fun formatCentsGrouped(cents: Long): String {
    val negative = cents < 0
    val absCents = abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    val groupedWhole = whole.toString()
        .reversed()
        .chunked(3)
        .joinToString(NON_BREAKING_SPACE)
        .reversed()
    val sign = if (negative) "-" else ""
    return "$sign$groupedWhole,$fraction"
}
