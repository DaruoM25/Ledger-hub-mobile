package com.ledgerhub.presentation.i18n

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

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

/**
 * Convertit un timestamp en millisecondes UTC (fourni par DatePicker M3) en chaîne ISO `YYYY-MM-DD`.
 */
fun millisToIsoDate(millis: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(millis)
    val date = instant.toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
    return date.toString()
}

/**
 * Convertit une chaîne ISO `YYYY-MM-DD` en millisecondes UTC pour initialiser un DatePicker M3.
 */
fun isoDateToMillis(iso: String): Long? {
    val match = ISO_DATE.matchEntire(iso.trim()) ?: return null
    return try {
        val date = kotlinx.datetime.LocalDate.parse(match.value)
        val instant = date.atStartOfDayIn(kotlinx.datetime.TimeZone.UTC)
        instant.toEpochMilliseconds()
    } catch (_: Exception) {
        null
    }
}

/**
 * Renvoie la date du jour en chaîne ISO `YYYY-MM-DD` selon le fuseau horaire système.
 */
fun todayIsoDate(): String {
    val now = kotlinx.datetime.Clock.System.now()
    val localDate = now.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    return localDate.toString()
}

