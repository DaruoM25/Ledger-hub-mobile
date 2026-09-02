package com.ledgerhub.domain.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-22) — bornes d'une période d'export.
 *
 * Deux erreurs coûtent cher dans un export comptable, et aucune ne se voit à l'écran : une borne
 * exclue qui perd les factures du dernier jour, et des dates inversées qui produisent un fichier
 * vide sans rien signaler. Elles sont éprouvées ici.
 */
class ExportPeriodTest {

    private val exercise = ExportPeriod(from = "2026-01-01", to = "2026-12-31")

    // ── Validation ──────────────────────────────────────────────────────────

    @Test
    fun aWellFormedPeriod_isAccepted() {
        assertNull(exercise.validate())
        assertTrue(exercise.isValid)
    }

    @Test
    fun aMalformedStartDate_isRejectedOnItsOwnAccount() {
        assertEquals(
            ExportPeriodError.FROM_INVALID,
            ExportPeriod(from = "01/01/2026", to = "2026-12-31").validate(),
        )
        assertEquals(
            ExportPeriodError.FROM_INVALID,
            ExportPeriod(from = "", to = "2026-12-31").validate(),
        )
    }

    @Test
    fun aMalformedEndDate_isRejectedOnItsOwnAccount() {
        assertEquals(
            ExportPeriodError.TO_INVALID,
            ExportPeriod(from = "2026-01-01", to = "2026-13").validate(),
        )
    }

    /**
     * Un mois 13 ou un jour 00 passent l'expression régulière mais ne désignent aucune date.
     *
     * La dernière assertion **constate une limite assumée** : le contrôle ne connaît ni la longueur
     * des mois ni les années bissextiles, donc `2026-02-29` est accepté. Aller plus loin
     * demanderait un calendrier complet pour écarter une saisie qui, au pire, produit un export
     * couvrant un jour de trop — sans perdre la moindre facture.
     */
    @Test
    fun structurallyImpossibleDates_areRejected() {
        assertFalse(ExportPeriod.isIsoDate("2026-13-01"))
        assertFalse(ExportPeriod.isIsoDate("2026-00-10"))
        assertFalse(ExportPeriod.isIsoDate("2026-02-00"))
        assertTrue(ExportPeriod.isIsoDate("2026-02-29"))
    }

    @Test
    fun anInvertedRange_isRejected() {
        assertEquals(
            ExportPeriodError.RANGE_INVERTED,
            ExportPeriod(from = "2026-12-31", to = "2026-01-01").validate(),
        )
    }

    /** Un export d'une seule journée est légitime — un contrôle ponctuel, une clôture. */
    @Test
    fun aSingleDayPeriod_isAccepted() {
        assertNull(ExportPeriod(from = "2026-03-04", to = "2026-03-04").validate())
    }

    /** La forme est vérifiée **avant** la comparaison : deux chaînes libres se compareraient sans sens. */
    @Test
    fun theFormatIsCheckedBeforeTheOrder() {
        assertEquals(
            ExportPeriodError.FROM_INVALID,
            ExportPeriod(from = "hier", to = "2026-01-01").validate(),
        )
    }

    // ── Appartenance ────────────────────────────────────────────────────────

    @Test
    fun bothBoundsAreIncluded() {
        assertTrue(exercise.contains("2026-01-01"))
        assertTrue(exercise.contains("2026-12-31"))
        assertTrue(exercise.contains("2026-06-15"))
    }

    @Test
    fun datesOutsideTheRange_areExcluded() {
        assertFalse(exercise.contains("2025-12-31"))
        assertFalse(exercise.contains("2027-01-01"))
    }

    /** Le domaine stocke des dates nues, mais un horodatage complet doit savoir se situer. */
    @Test
    fun aFullTimestamp_isTruncatedToItsDay() {
        assertTrue(exercise.contains("2026-06-15T09:12:00Z"))
    }

    @Test
    fun anUnreadableDate_belongsToNoPeriod() {
        assertFalse(exercise.contains(""))
        assertFalse(exercise.contains("15/06/2026"))
    }

    // ── Période proposée à l'ouverture ──────────────────────────────────────

    @Test
    fun theDefaultPeriod_runsFromJanuaryFirstToToday() {
        assertEquals(
            ExportPeriod(from = "2026-01-01", to = "2026-09-02"),
            ExportPeriod.yearToDate("2026-09-02"),
        )
    }

    /** L'horloge du domaine publie un instant ISO complet : la période n'en garde que le jour. */
    @Test
    fun theDefaultPeriod_acceptsAFullTimestampFromTheClock() {
        assertEquals(
            ExportPeriod(from = "2026-01-01", to = "2026-09-02"),
            ExportPeriod.yearToDate("2026-09-02T14:33:07.512Z"),
        )
    }

    /** Une horloge illisible ne doit pas produire une période fausse mais crédible. */
    @Test
    fun anUnreadableClock_yieldsAnEmptyAndInvalidPeriod() {
        val period = ExportPeriod.yearToDate("jamais")

        assertEquals(ExportPeriod(from = "", to = ""), period)
        assertFalse(period.isValid)
    }
}
