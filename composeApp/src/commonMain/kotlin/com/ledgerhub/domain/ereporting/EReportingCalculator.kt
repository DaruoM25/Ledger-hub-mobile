package com.ledgerhub.domain.ereporting

import kotlin.math.abs

/**
 * Consolidation des totaux d'une déclaration e-Reporting.
 *
 * Tout le calcul se fait en centimes entiers (`Long`) : `BigDecimal` n'existe pas en commonMain
 * KMP, et l'administration fiscale rejette les déclarations dont les montants portent la trace
 * d'erreurs de flottant. Les `Double` d'entrée sont convertis en centimes par un arrondi
 * « round half up » explicite ; le résultat n'est reconverti en euros qu'une seule fois, à la fin.
 */
object EReportingCalculator {

    private const val CENTS_PER_EURO = 100.0
    private const val BASIS_POINTS_DIVISOR = 10_000L

    /**
     * @return les totaux HT / TVA / TTC arrondis au centime, et le nombre de lignes.
     *   [EReportingTotals.EMPTY] pour une liste vide.
     */
    fun computeTotals(lines: List<EReportingTransactionLine>): EReportingTotals {
        if (lines.isEmpty()) return EReportingTotals.EMPTY

        var htCents = 0L
        var vatCents = 0L
        for (line in lines) {
            val lineHtCents = toCents(line.amountHt)
            val lineVatCents = vatCentsFor(lineHtCents, line.vatRateBasisPoints)
            htCents += lineHtCents
            vatCents += lineVatCents
        }
        val ttcCents = htCents + vatCents

        return EReportingTotals(
            totalHt = htCents / CENTS_PER_EURO,
            totalVat = vatCents / CENTS_PER_EURO,
            totalTtc = ttcCents / CENTS_PER_EURO,
            transactionCount = lines.size,
        )
    }

    /** Arrondi « round half up » d'un montant en euros vers un entier de centimes. */
    private fun toCents(amountEuro: Double): Long {
        val scaled = amountEuro * CENTS_PER_EURO
        return if (scaled >= 0.0) {
            (scaled + 0.5).toLong()
        } else {
            -((abs(scaled) + 0.5).toLong())
        }
    }

    /** TVA d'un montant HT en centimes, arrondie au centime le plus proche (round half up). */
    private fun vatCentsFor(htCents: Long, rateBasisPoints: Int): Long {
        val product = htCents * rateBasisPoints
        return if (product >= 0L) {
            (product + BASIS_POINTS_DIVISOR / 2) / BASIS_POINTS_DIVISOR
        } else {
            -((abs(product) + BASIS_POINTS_DIVISOR / 2) / BASIS_POINTS_DIVISOR)
        }
    }
}
