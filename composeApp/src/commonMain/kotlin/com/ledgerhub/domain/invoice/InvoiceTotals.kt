package com.ledgerhub.domain.invoice

/**
 * Sous-total de TVA pour un taux donné — mention obligatoire Factur-X (ventilation par taux).
 * [baseHt] est la somme des montants HT des lignes de ce taux ; la TVA est calculée une seule
 * fois sur cette base agrégée (et non en sommant des TVA arrondies ligne par ligne), pour éviter
 * les écarts d'arrondi de quelques centimes entre le détail et le total — c'est la méthode de
 * calcul attendue par l'administration fiscale pour une facture multi-lignes.
 */
data class VatBreakdown(val rate: VatRate, val baseHt: Money, val vatAmount: Money) {
    val totalTtc: Money get() = baseHt + vatAmount
}

/** Regroupe les lignes par taux de TVA et calcule la TVA sur la base HT agrégée de chaque groupe. */
fun computeVatBreakdown(lines: List<InvoiceLine>): List<VatBreakdown> =
    VatRate.entries.mapNotNull { rate ->
        val linesForRate = lines.filter { it.vatRate == rate }
        if (linesForRate.isEmpty()) return@mapNotNull null
        val baseHt = linesForRate.fold(Money.ZERO) { acc, line -> acc + line.totalHt }
        VatBreakdown(rate = rate, baseHt = baseHt, vatAmount = baseHt.vatFor(rate.basisPoints))
    }

fun totalHtOf(lines: List<InvoiceLine>): Money =
    lines.fold(Money.ZERO) { acc, line -> acc + line.totalHt }

fun totalVatOf(lines: List<InvoiceLine>): Money =
    computeVatBreakdown(lines).fold(Money.ZERO) { acc, breakdown -> acc + breakdown.vatAmount }

fun totalTtcOf(lines: List<InvoiceLine>): Money = totalHtOf(lines) + totalVatOf(lines)
