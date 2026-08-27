package com.ledgerhub.domain.quote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatBreakdown
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.vatFor

/** Regroupe les lignes par taux de TVA et calcule la TVA sur la base HT agrégée de chaque groupe. */
fun computeQuoteVatBreakdown(lines: List<QuoteLine>): List<VatBreakdown> =
    VatRate.entries.mapNotNull { rate ->
        val linesForRate = lines.filter { it.vatRate == rate }
        if (linesForRate.isEmpty()) return@mapNotNull null
        val baseHt = linesForRate.fold(Money.ZERO) { acc, line -> acc + line.totalHt }
        VatBreakdown(rate = rate, baseHt = baseHt, vatAmount = baseHt.vatFor(rate.basisPoints))
    }

fun totalHtOf(lines: List<QuoteLine>): Money =
    lines.fold(Money.ZERO) { acc, line -> acc + line.totalHt }

fun totalVatOf(lines: List<QuoteLine>): Money =
    computeQuoteVatBreakdown(lines).fold(Money.ZERO) { acc, breakdown -> acc + breakdown.vatAmount }

fun totalTtcOf(lines: List<QuoteLine>): Money = totalHtOf(lines) + totalVatOf(lines)
