package com.ledgerhub.domain.auth

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-26) — les propriétés attendues d'une empreinte de mot de passe, éprouvées sans base
 * ni écran : déterminisme sous un même sel, divergence sous deux sels, et refus de tout ce qui
 * n'est pas le mot de passe d'origine.
 */
class PasswordHashTest {

    private val salt = "0123456789abcdef0123456789abcdef"

    @Test
    fun theSamePasswordAndSalt_alwaysYieldTheSameDigest() {
        assertEquals(PasswordHash.hash("motdepasse", salt), PasswordHash.hash("motdepasse", salt))
    }

    @Test
    fun theDigest_isAnHexadecimalSha256() {
        val digest = PasswordHash.hash("motdepasse", salt)
        assertEquals(64, digest.length)
        assertTrue(digest.all { it in '0'..'9' || it in 'a'..'f' }, "condensat non hexadécimal : $digest")
    }

    /** Le sel est ce qui interdit les tables précalculées : deux comptes, deux empreintes. */
    @Test
    fun theSamePasswordUnderTwoSalts_yieldsTwoDigests() {
        val other = "fedcba9876543210fedcba9876543210"
        assertNotEquals(PasswordHash.hash("motdepasse", salt), PasswordHash.hash("motdepasse", other))
    }

    /** Rien du mot de passe ne doit se lire dans ce qui est stocké. */
    @Test
    fun theDigest_containsNothingOfThePassword() {
        val digest = PasswordHash.hash("motdepasse", salt)
        assertFalse(digest.contains("motdepasse"))
    }

    @Test
    fun matches_acceptsTheOriginalPassword_andRejectsAnyOther() {
        val digest = PasswordHash.hash("motdepasse", salt)

        assertTrue(PasswordHash.matches("motdepasse", salt, digest))
        assertFalse(PasswordHash.matches("Motdepasse", salt, digest))
        assertFalse(PasswordHash.matches("motdepass", salt, digest))
        assertFalse(PasswordHash.matches("", salt, digest))
    }

    /** Une empreinte tronquée en base ne doit pas ouvrir la porte par comparaison de préfixe. */
    @Test
    fun matches_rejectsATruncatedDigest() {
        val digest = PasswordHash.hash("motdepasse", salt)
        assertFalse(PasswordHash.matches("motdepasse", salt, digest.dropLast(1)))
    }

    @Test
    fun newSalt_isThirtyTwoHexCharacters_andVariesBetweenAccounts() {
        val first = PasswordHash.newSalt()
        val second = PasswordHash.newSalt()

        assertEquals(32, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' }, "sel non hexadécimal : $first")
        assertNotEquals(first, second)
    }

    /** Source injectable : un sel reproductible reste possible en test, jamais en production. */
    @Test
    fun newSalt_isReproducibleFromASeededSource() {
        assertEquals(PasswordHash.newSalt(Random(42)), PasswordHash.newSalt(Random(42)))
    }
}
