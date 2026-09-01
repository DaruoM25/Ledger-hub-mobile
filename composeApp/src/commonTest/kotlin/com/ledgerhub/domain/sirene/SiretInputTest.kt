package com.ledgerhub.domain.sirene

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-21) — normalisation du SIRET saisi.
 *
 * C'est cette fonction qui décide du moment où l'application interroge le répertoire : elle se
 * teste caractère par caractère, sans coroutine, sans composition, sans écran.
 */
class SiretInputTest {

    private val validSiret = "90123456700013"

    @Test
    fun theExpectedLength_isFourteen() {
        assertEquals(14, SiretInput.LENGTH)
    }

    // ── Normalisation ───────────────────────────────────────────────────────

    @Test
    fun sanitize_keepsOnlyDigits() {
        assertEquals(validSiret, SiretInput.sanitize(validSiret))
        assertEquals("123", SiretInput.sanitize("1a2b3c"))
        assertEquals("", SiretInput.sanitize("SIRET"))
        assertEquals("", SiretInput.sanitize(""))
    }

    /** Un SIRET collé depuis un Kbis ou un courriel arrive presque toujours mis en forme. */
    @Test
    fun sanitize_acceptsAPastedFormattedSiret() {
        assertEquals(validSiret, SiretInput.sanitize("901 234 567 00013"))
        assertEquals(validSiret, SiretInput.sanitize("901.234.567.00013"))
        assertEquals(validSiret, SiretInput.sanitize("901-234-567-00013"))
        assertEquals(validSiret, SiretInput.sanitize("  $validSiret  "))
    }

    // ── Seuil de déclenchement ──────────────────────────────────────────────

    @Test
    fun isComplete_isTrue_onlyAtFourteenDigits() {
        assertFalse(SiretInput.isComplete(validSiret.dropLast(1)), "13 chiffres ne suffisent pas")
        assertTrue(SiretInput.isComplete(validSiret))
        assertFalse(SiretInput.isComplete(validSiret + "5"), "15 chiffres ne sont pas un SIRET")
    }

    @Test
    fun isComplete_countsDigitsAndNotCharacters() {
        // 14 chiffres, 17 caractères : c'est bien la saisie normalisée qui décide.
        assertTrue(SiretInput.isComplete("901 234 567 00013"))
        // 14 caractères dont des lettres : 11 chiffres seulement.
        assertFalse(SiretInput.isComplete("901234567ABC00"))
    }

    @Test
    fun isComplete_isFalse_onAnEmptyOrBlankInput() {
        assertFalse(SiretInput.isComplete(""))
        assertFalse(SiretInput.isComplete("   "))
    }

    /**
     * La clé de Luhn n'est **pas** un critère de déclenchement : un SIRET qui ne la respecte pas
     * lance quand même l'interrogation, et c'est au répertoire de trancher. Ce test fige cette
     * décision, sans quoi elle serait rétablie par mégarde au premier « durcissement » venu.
     */
    @Test
    fun aLuhnInvalidSiret_stillTriggersTheLookup() {
        assertTrue(SiretInput.isComplete("82032933100027"))
        assertTrue(SiretInput.isComplete("11111111111111"))
    }
}
