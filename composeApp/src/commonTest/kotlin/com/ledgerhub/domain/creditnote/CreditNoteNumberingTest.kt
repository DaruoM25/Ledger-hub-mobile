package com.ledgerhub.domain.creditnote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Numérotation séquentielle des avoirs (US-05). La continuité est une obligation fiscale : ces
 * cas verrouillent le format, la progression et le comportement sur séquence illisible.
 */
class CreditNoteNumberingTest {

    @Test
    fun format_padsTheSequenceToFourDigits() {
        assertEquals("AV-2026-0001", CreditNoteNumbering.format(2026, 1))
        assertEquals("AV-2026-0042", CreditNoteNumbering.format(2026, 42))
        assertEquals("AV-2026-9999", CreditNoteNumbering.format(2026, 9999))
    }

    @Test
    fun next_startsAtOne_whenTheYearHasNoCreditNoteYet() {
        assertEquals("AV-2026-0001", CreditNoteNumbering.next(2026, lastNumber = null))
    }

    @Test
    fun next_incrementsTheLastAttributedSequence() {
        assertEquals("AV-2026-0002", CreditNoteNumbering.next(2026, "AV-2026-0001"))
        assertEquals("AV-2026-0100", CreditNoteNumbering.next(2026, "AV-2026-0099"))
    }

    @Test
    fun next_restartsTheSequence_onEachNewYear() {
        // Le motif LIKE isole l'exercice : un avoir de 2025 n'influence pas la séquence 2026.
        assertEquals("AV-2027-0001", CreditNoteNumbering.next(2027, lastNumber = null))
        assertEquals("AV-2026-%", CreditNoteNumbering.likePatternForYear(2026))
    }

    @Test
    fun next_restartsAtOne_whenTheLastNumberIsUnreadable() {
        // Une numérotation qu'on ne sait pas relire ne doit pas bloquer l'émission : la clé
        // primaire refusera de toute façon un doublon.
        assertEquals("AV-2026-0001", CreditNoteNumbering.next(2026, "n'importe quoi"))
        assertEquals("AV-2026-0001", CreditNoteNumbering.next(2026, ""))
    }

    @Test
    fun sequenceOf_readsTheRank_orReturnsNull() {
        assertEquals(42, CreditNoteNumbering.sequenceOf("AV-2026-0042"))
        assertNull(CreditNoteNumbering.sequenceOf("AV-2026-42"))
        assertNull(CreditNoteNumbering.sequenceOf(null))
    }

    @Test
    fun isValid_acceptsOnlyTheExpectedPattern() {
        assertTrue(CreditNoteNumbering.isValid("AV-2026-0001"))
        assertFalse(CreditNoteNumbering.isValid("AV-2026-001"), "3 chiffres : format refusé")
        assertFalse(CreditNoteNumbering.isValid("FAC-2026-0001"), "préfixe facture : format refusé")
        assertFalse(CreditNoteNumbering.isValid(""))
    }

    @Test
    fun lexicographicOrder_matchesNumericOrder_whichIsWhatTheSqlQueryReliesOn() {
        // selectLastNumberForPrefix se contente d'un ORDER BY number DESC : cela n'est correct
        // que parce que le suffixe est à largeur fixe.
        val numbers = listOf(2, 10, 1, 100).map { CreditNoteNumbering.format(2026, it) }
        assertEquals(CreditNoteNumbering.format(2026, 100), numbers.max())
    }
}
