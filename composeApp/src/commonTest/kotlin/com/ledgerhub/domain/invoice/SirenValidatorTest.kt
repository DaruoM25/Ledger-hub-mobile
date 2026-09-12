package com.ledgerhub.domain.invoice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SirenValidatorTest {

    @Test
    fun eInvoicingMode_emptySiren_isInvalid() {
        val result = SirenValidator.validate("", TransactionMode.E_INVOICING)
        assertTrue(result is ValidationResult.Invalid)
        assertEquals("L'identifiant SIREN/SIRET est obligatoire en B2B France", (result as ValidationResult.Invalid).reason)
    }

    @Test
    fun eInvoicingMode_blankSiren_isInvalid() {
        val result = SirenValidator.validate("   ", TransactionMode.E_INVOICING)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun eInvoicingMode_eightDigits_isInvalid() {
        val result = SirenValidator.validate("12345678", TransactionMode.E_INVOICING)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun eInvoicingMode_tenDigits_isInvalid() {
        val result = SirenValidator.validate("1234567890", TransactionMode.E_INVOICING)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun eInvoicingMode_nineDigits_isValid() {
        val result = SirenValidator.validate("123456789", TransactionMode.E_INVOICING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eInvoicingMode_nineDigitsFormattedWithSpaces_isValid() {
        val result = SirenValidator.validate("123 456 789", TransactionMode.E_INVOICING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eInvoicingMode_fourteenDigits_isValid() {
        val result = SirenValidator.validate("12345678900014", TransactionMode.E_INVOICING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eInvoicingMode_fourteenDigitsFormatted_isValid() {
        val result = SirenValidator.validate("123 456 789 00014", TransactionMode.E_INVOICING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eInvoicingMode_nonDigits_isInvalid() {
        val result = SirenValidator.validate("12345678A", TransactionMode.E_INVOICING)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun eReportingMode_emptySiren_isValid() {
        val result = SirenValidator.validate("", TransactionMode.E_REPORTING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eReportingMode_blankSiren_isValid() {
        val result = SirenValidator.validate("   ", TransactionMode.E_REPORTING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eReportingMode_validNineDigits_isValid() {
        val result = SirenValidator.validate("987654321", TransactionMode.E_REPORTING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eReportingMode_validFourteenDigits_isValid() {
        val result = SirenValidator.validate("98765432100025", TransactionMode.E_REPORTING)
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun eReportingMode_invalidLengthWhenProvided_isInvalid() {
        val result = SirenValidator.validate("12345", TransactionMode.E_REPORTING)
        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun eReportingMode_nonDigitsWhenProvided_isInvalid() {
        val result = SirenValidator.validate("12345678X", TransactionMode.E_REPORTING)
        assertTrue(result is ValidationResult.Invalid)
    }
}
