package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.invoice.Money
import kotlin.math.abs

/** Espace insécable (U+00A0) — séparateur de milliers, convention typographique française. */
val NON_BREAKING_SPACE: String = ' '.toString()

/**
 * Formate un montant en centimes (`Long`) selon la langue active. Devise EUR dans les deux cas —
 * seule la **présentation** change, miroir strict des règles validées sur le Web (Prompt 2).
 *
 * - **FR** : `1 234,56 €` — espace insécable milliers, virgule décimale, symbole € suffixé. Négatif : `-1 000,00 €`.
 * - **EN** : `€1,234.56` — symbole € préfixé, virgule milliers, point décimal, sans espace. Négatif : `-€1,000.00`.
 *
 * Arithmétique 100 % entière (`Long`) : `whole = |cents| / 100`, `frac = |cents| % 100`. Aucun
 * `Double`/`Float`, donc aucune dérive de flottant à l'affichage — `java.text.NumberFormat` et
 * `String.format` restent de toute façon indisponibles en commonMain KMP.
 */
fun formatMoney(cents: Long, language: AppLanguage): String {
    val negative = cents < 0
    val absCents = abs(cents)
    val whole = absCents / 100
    val fraction = (absCents % 100).toString().padStart(2, '0')
    val sign = if (negative) "-" else ""

    return when (language) {
        AppLanguage.FR -> {
            val grouped = whole.toString().reversed().chunked(3).joinToString(NON_BREAKING_SPACE).reversed()
            "$sign$grouped,$fraction$NON_BREAKING_SPACE€"
        }
        AppLanguage.EN -> {
            val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
            "$sign€$grouped.$fraction"
        }
    }
}

/** Formate un [Money] selon la langue active — voir [formatMoney]. */
fun Money.format(language: AppLanguage): String = formatMoney(cents, language)

/**
 * Format français historique (`1 234,56 €`) — conservé pour les appels et tests hérités.
 * Équivaut à `format(AppLanguage.FR)`.
 */
fun Money.formatEuros(): String = format(AppLanguage.FR)

/**
 * Groupement français des milliers d'un montant en centimes, sans symbole ni décimales suffixées :
 * `123456` -> `"1 234,56"`. Conservé pour l'aperçu WYSIWYG (`InvoicePaperCanvas`) et les tests.
 */
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
