package com.ledgerhub.domain.directory

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 — clé de contrôle de Luhn ([LuhnChecksum]). Pur, sans I/O ni framework.
 */
class LuhnChecksumTest {

    // ── Algorithme brut ─────────────────────────────────────────────────────

    @Test
    fun isLuhnValid_acceptsKnownValidSequences() {
        assertTrue(LuhnChecksum.isLuhnValid("732829320"))   // SIREN Renault
        assertTrue(LuhnChecksum.isLuhnValid("552081317"))   // SIREN Danone
        assertTrue(LuhnChecksum.isLuhnValid("73282932000074")) // SIRET Renault siège
    }

    @Test
    fun isLuhnValid_rejectsTamperedCheckDigit() {
        assertFalse(LuhnChecksum.isLuhnValid("732829321"))
        assertFalse(LuhnChecksum.isLuhnValid("123456789"))
        assertFalse(LuhnChecksum.isLuhnValid("73282932000075"))
    }

    @Test
    fun isLuhnValid_rejectsNonDigitsAndEmpty() {
        assertFalse(LuhnChecksum.isLuhnValid(""))
        assertFalse(LuhnChecksum.isLuhnValid("73282932X"))
        assertFalse(LuhnChecksum.isLuhnValid(" 732829320 "))
    }

    // ── SIREN ───────────────────────────────────────────────────────────────

    @Test
    fun isValidSiren_requiresNineDigitsAndLuhn() {
        assertTrue(LuhnChecksum.isValidSiren("732829320"))
        assertTrue(LuhnChecksum.isValidSiren("443061841"))
        assertFalse(LuhnChecksum.isValidSiren("12345678"))    // 8 chiffres
        assertFalse(LuhnChecksum.isValidSiren("7328293200"))  // 10 chiffres
        assertFalse(LuhnChecksum.isValidSiren("123456789"))   // Luhn KO
    }

    // ── SIRET ───────────────────────────────────────────────────────────────

    @Test
    fun isValidSiret_requiresFourteenDigitsAndLuhn() {
        assertTrue(LuhnChecksum.isValidSiret("73282932000074"))
        assertFalse(LuhnChecksum.isValidSiret("7328293200007"))    // 13 chiffres
        assertFalse(LuhnChecksum.isValidSiret("73282932000075"))   // Luhn KO
    }

    // ── Dérogation La Poste ─────────────────────────────────────────────────

    @Test
    fun laPosteSiren_isAcceptedAsSiren() {
        assertTrue(LuhnChecksum.isValidSiren(LuhnChecksum.LA_POSTE_SIREN))
    }

    @Test
    fun laPosteSiret_isAcceptedEvenWhenStandardLuhnFails() {
        val laPosteSiret = "35600000000041"
        // Ce SIRET ne satisfait PAS la clé de Luhn standard…
        assertFalse(LuhnChecksum.isLuhnValid(laPosteSiret))
        // …mais reste un identifiant valide via la dérogation historique.
        assertTrue(LuhnChecksum.isValidSiret(laPosteSiret))
    }
}
