package com.ledgerhub.presentation.i18n

import com.ledgerhub.domain.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests QA — formatage de date dépendant de la langue (aucune dépendance kotlinx-datetime). */
class DateFormatTest {

    @Test
    fun frenchFormat_isDayMonthYearWithSlashes() {
        assertEquals("24/06/2026", formatIsoDate("2026-06-24", AppLanguage.FR))
        assertEquals("01/01/2026", formatIsoDate("2026-01-01", AppLanguage.FR))
    }

    @Test
    fun englishFormat_isAbbreviatedMonthDayCommaYear() {
        assertEquals("Jun 24, 2026", formatIsoDate("2026-06-24", AppLanguage.EN))
        assertEquals("Jan 1, 2026", formatIsoDate("2026-01-01", AppLanguage.EN))
        assertEquals("Dec 31, 2025", formatIsoDate("2025-12-31", AppLanguage.EN))
    }

    @Test
    fun englishFormat_stripsLeadingZeroFromDay() {
        assertEquals("Jun 4, 2026", formatIsoDate("2026-06-04", AppLanguage.EN))
    }

    @Test
    fun malformedInput_isReturnedUnchanged() {
        assertEquals("pas-une-date", formatIsoDate("pas-une-date", AppLanguage.EN))
        assertEquals("2026/06/24", formatIsoDate("2026/06/24", AppLanguage.FR))
        assertEquals("2026-13-01", formatIsoDate("2026-13-01", AppLanguage.EN)) // mois hors plage
    }
}
