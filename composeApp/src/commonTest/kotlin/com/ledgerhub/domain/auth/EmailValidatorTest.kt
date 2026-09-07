package com.ledgerhub.domain.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests unitaires du validateur d'email pur Kotlin (RFC 5322 simplifiée).
 */
class EmailValidatorTest {

    @Test
    fun validEmails_returnTrue() {
        val validList = listOf(
            "user@domain.fr",
            "contact@cabinet-expertise.com",
            "first.last@company.co.uk",
            "first+tag@service.io",
            "dev_user_123@sub.domain.org",
            "  trimmed.user@domain.com  ",
        )

        for (email in validList) {
            assertTrue(EmailValidator.isValid(email), "Doit être valide: $email")
        }
    }

    @Test
    fun invalidEmails_returnFalse() {
        val invalidList = listOf(
            "",
            "   ",
            "plainaddress",
            "@missinguser.com",
            "user@.com",
            "user@domain",
            "user@domain.",
            "user@domain.c", // TLD < 2 caractères
            "user name@domain.com",
            "user@dom ain.com",
            "user@@domain.com",
            "user@domain..com",
        )

        for (email in invalidList) {
            assertFalse(EmailValidator.isValid(email), "Doit être invalide: $email")
        }
    }
}
