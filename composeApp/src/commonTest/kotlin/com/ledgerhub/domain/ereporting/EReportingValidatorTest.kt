package com.ledgerhub.domain.ereporting

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 — tests unitaires purs de [EReportingValidator.validatePeriod].
 */
class EReportingValidatorTest {

    @Test
    fun tcUnit04_validMonths_ofReformYear_areAccepted() {
        assertTrue(EReportingValidator.validatePeriod("2026-01"))
        assertTrue(EReportingValidator.validatePeriod("2026-12"))
        assertTrue(EReportingValidator.validatePeriod("2027-06"))
    }

    @Test
    fun tcUnit05_nonExistentMonths_areRejected() {
        assertFalse(EReportingValidator.validatePeriod("2026-00"))
        assertFalse(EReportingValidator.validatePeriod("2026-13"))
    }

    @Test
    fun tcUnit06_yearsBeforeTheReform_areRejected() {
        assertFalse(EReportingValidator.validatePeriod("2025-12"))
        assertFalse(EReportingValidator.validatePeriod("1999-01"))
    }

    @Test
    fun tcUnit07_malformedInput_isRejected() {
        assertFalse(EReportingValidator.validatePeriod("2026/01"))
        assertFalse(EReportingValidator.validatePeriod("texte"))
        assertFalse(EReportingValidator.validatePeriod(""))
        assertFalse(EReportingValidator.validatePeriod("2026-1"))
        assertFalse(EReportingValidator.validatePeriod("2026-01-15"))
    }
}
