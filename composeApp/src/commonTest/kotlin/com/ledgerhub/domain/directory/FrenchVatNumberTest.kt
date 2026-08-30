package com.ledgerhub.domain.directory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 — numéro de TVA intracommunautaire français, formule Modulo 97 ([FrenchVatNumber]).
 */
class FrenchVatNumberTest {

    @Test
    fun computeKey_matchesModulo97Formula_forKnownSiren() {
        // 732829320 % 97 = 43 ; (12 + 3*43) % 97 = 141 % 97 = 44
        assertEquals("44", FrenchVatNumber.computeKey("732829320"))
    }

    @Test
    fun computeKey_isAlwaysTwoDigits_zeroPadded() {
        val key = FrenchVatNumber.computeKey("552081317")
        assertEquals(2, key.length)
        assertTrue(key.all { it in '0'..'9' })
    }

    @Test
    fun format_prefixesFrThenKeyThenSiren() {
        assertEquals("FR44732829320", FrenchVatNumber.format("732829320"))
    }

    @Test
    fun isValid_acceptsANumberBuiltByFormat() {
        for (siren in listOf("732829320", "552081317", "443061841", "410037121")) {
            assertTrue(FrenchVatNumber.isValid(FrenchVatNumber.format(siren)), "attendu valide pour $siren")
        }
    }

    @Test
    fun isValid_rejectsTamperedKey() {
        // Clé correcte = 44 → on falsifie en 45.
        assertFalse(FrenchVatNumber.isValid("FR45732829320"))
    }

    @Test
    fun isValid_rejectsStructuralErrors() {
        assertFalse(FrenchVatNumber.isValid("FR4473282932"))    // 8 chiffres de SIREN
        assertFalse(FrenchVatNumber.isValid("FR447328293200"))  // 10 chiffres
        assertFalse(FrenchVatNumber.isValid("DE44732829320"))   // pays incorrect
        assertFalse(FrenchVatNumber.isValid("FRAB732829320"))   // clé non numérique
        assertFalse(FrenchVatNumber.isValid(""))
    }

    @Test
    fun isValid_isCaseInsensitive_andTrims() {
        assertTrue(FrenchVatNumber.isValid("  fr44732829320  "))
    }
}
