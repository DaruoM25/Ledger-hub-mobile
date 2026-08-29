package com.ledgerhub.presentation.i18n

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey

private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/**
 * Formate une date ISO `"YYYY-MM-DD"` selon la langue active. Parsing par découpage de chaîne —
 * aucune dépendance `kotlinx-datetime` (cf. décision v1). Une entrée non conforme est renvoyée
 * telle quelle (défensif : jamais d'exception à l'affichage).
 *
 * - **FR** : `24/06/2026` (`dd/MM/yyyy`)
 * - **EN** : `Jun 24, 2026` (`MMM d, yyyy`, mois abrégés issus du dictionnaire)
 */
fun formatIsoDate(iso: String, language: AppLanguage): String {
    val match = ISO_DATE.matchEntire(iso.trim()) ?: return iso
    val (year, month, day) = match.destructured
    val monthIndex = month.toInt()
    if (monthIndex !in 1..12) return iso
    val dayInt = day.toInt()

    return when (language) {
        AppLanguage.FR -> "$day/$month/$year"
        AppLanguage.EN -> {
            val abbr = AppTranslations.get(monthAbbrKey(monthIndex), AppLanguage.EN)
            "$abbr $dayInt, $year"
        }
    }
}

private fun monthAbbrKey(month: Int): StringKey = when (month) {
    1 -> StringKey.MONTH_ABBR_1
    2 -> StringKey.MONTH_ABBR_2
    3 -> StringKey.MONTH_ABBR_3
    4 -> StringKey.MONTH_ABBR_4
    5 -> StringKey.MONTH_ABBR_5
    6 -> StringKey.MONTH_ABBR_6
    7 -> StringKey.MONTH_ABBR_7
    8 -> StringKey.MONTH_ABBR_8
    9 -> StringKey.MONTH_ABBR_9
    10 -> StringKey.MONTH_ABBR_10
    11 -> StringKey.MONTH_ABBR_11
    else -> StringKey.MONTH_ABBR_12
}
