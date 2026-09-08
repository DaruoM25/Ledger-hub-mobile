package com.ledgerhub.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PasswordValidatorTest {

    @Test
    fun password_shorterThanEightChars_isRejected() {
        assertFalse(PasswordValidator.isValid("Pass1!"))
        val result = PasswordValidator.validate("Pass1!")
        assertIs<PasswordValidationResult.Invalid>(result)
        assertEquals(PasswordValidator.ERROR_MESSAGE, result.reason)
    }

    @Test
    fun password_missingUppercase_isRejected() {
        assertFalse(PasswordValidator.isValid("securepass2026!"))
        val result = PasswordValidator.validate("securepass2026!")
        assertIs<PasswordValidationResult.Invalid>(result)
        assertEquals(PasswordValidator.ERROR_MESSAGE, result.reason)
    }

    @Test
    fun password_missingDigit_isRejected() {
        assertFalse(PasswordValidator.isValid("SecurePassword!"))
        val result = PasswordValidator.validate("SecurePassword!")
        assertIs<PasswordValidationResult.Invalid>(result)
        assertEquals(PasswordValidator.ERROR_MESSAGE, result.reason)
    }

    @Test
    fun password_missingSpecialCharacter_isRejected() {
        assertFalse(PasswordValidator.isValid("SecurePass2026"))
        val result = PasswordValidator.validate("SecurePass2026")
        assertIs<PasswordValidationResult.Invalid>(result)
        assertEquals(PasswordValidator.ERROR_MESSAGE, result.reason)
    }

    @Test
    fun robustPassword_satisfyingAllCriteria_isValid() {
        assertTrue(PasswordValidator.isValid("SecurePass2026!"))
        val result = PasswordValidator.validate("SecurePass2026!")
        assertIs<PasswordValidationResult.Valid>(result)
    }
}
