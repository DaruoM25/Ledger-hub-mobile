package com.ledgerhub.domain.ereporting

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 1 — tests unitaires purs de [EReportingCalculator]. Aucun accès disque, aucun
 * framework : uniquement de l'arithmétique de centimes.
 */
class EReportingCalculatorTest {

    private val tolerance = 0.0001

    @Test
    fun tcUnit01_multipleVatRates_areSummedToTheCent() {
        val lines = listOf(
            EReportingTransactionLine("Prestations 20 %", amountHt = 100.00, vatRateBasisPoints = 2000),
            EReportingTransactionLine("Livres 10 %", amountHt = 50.00, vatRateBasisPoints = 1000),
            EReportingTransactionLine("Denrées 5,5 %", amountHt = 33.33, vatRateBasisPoints = 550),
        )

        val totals = EReportingCalculator.computeTotals(lines)

        assertEquals(183.33, totals.totalHt, tolerance)
        assertEquals(26.83, totals.totalVat, tolerance)
        assertEquals(210.16, totals.totalTtc, tolerance)
        assertEquals(3, totals.transactionCount)
    }

    @Test
    fun tcUnit02_emptyBasket_returnsZeroedTotals() {
        val totals = EReportingCalculator.computeTotals(emptyList())

        assertEquals(0.00, totals.totalHt, tolerance)
        assertEquals(0.00, totals.totalVat, tolerance)
        assertEquals(0.00, totals.totalTtc, tolerance)
        assertEquals(0, totals.transactionCount)
        assertEquals(EReportingTotals.EMPTY, totals)
    }

    @Test
    fun tcUnit03_floatingPointArtifacts_areNeutralisedByCentRounding() {
        // 0.1 + 0.2 == 0.30000000000000004 en IEEE 754 : l'arrondi au centime doit ramener à 0,30.
        val lines = listOf(
            EReportingTransactionLine("Somme piégée", amountHt = 0.1 + 0.2, vatRateBasisPoints = 2000),
        )

        val totals = EReportingCalculator.computeTotals(lines)

        assertEquals(0.30, totals.totalHt, tolerance)
        assertEquals(0.06, totals.totalVat, tolerance)
        assertEquals(0.36, totals.totalTtc, tolerance)
        assertEquals(1, totals.transactionCount)
    }
}
