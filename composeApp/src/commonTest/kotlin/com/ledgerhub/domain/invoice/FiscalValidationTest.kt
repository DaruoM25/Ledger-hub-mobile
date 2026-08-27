package com.ledgerhub.domain.invoice

import kotlin.test.Test
import kotlin.test.assertIs

class FiscalValidationTest {

    @Test
    fun validateSiren_nineDigits_isValid() {
        assertIs<ValidationResult.Valid>(FiscalValidation.validateSiren("123456789"))
    }

    @Test
    fun validateSiren_tooShort_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiren("12345678"))
    }

    @Test
    fun validateSiren_tooLong_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiren("1234567890"))
    }

    @Test
    fun validateSiren_containsLetters_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiren("12345678A"))
    }

    @Test
    fun validateSiret_fourteenDigits_isValid() {
        assertIs<ValidationResult.Valid>(FiscalValidation.validateSiret("12345678900012"))
    }

    @Test
    fun validateSiret_wrongLength_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiret("123456789"))
    }

    @Test
    fun validateSiret_containsSpaces_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiret("1234 5678 9000 12"))
    }
}
