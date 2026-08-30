package com.ledgerhub.domain.ereporting

/**
 * Contrôle de la période de référence d'une déclaration e-Reporting.
 *
 * Format attendu : `AAAA-MM` avec un mois de `01` à `12`. Une déclaration ne peut par ailleurs
 * pas porter sur un exercice antérieur à l'entrée en vigueur de la réforme (2026).
 */
object EReportingValidator {

    /** Premier exercice couvert par l'obligation d'e-Reporting. */
    const val REFORM_YEAR = 2026

    private val PERIOD_REGEX = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")

    /**
     * @return `true` si [period] respecte le format `AAAA-MM` et vise l'année [REFORM_YEAR] ou une
     *   année postérieure.
     */
    fun validatePeriod(period: String): Boolean {
        if (!PERIOD_REGEX.matches(period)) return false
        val year = period.substring(0, 4).toIntOrNull() ?: return false
        return year >= REFORM_YEAR
    }
}
