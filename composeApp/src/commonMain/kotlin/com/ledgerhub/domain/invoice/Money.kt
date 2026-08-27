package com.ledgerhub.domain.invoice

/**
 * Montant HT/TTC en centimes (Long) — jamais en Double/Float.
 * BigDecimal n'existe pas en commonMain KMP ; l'arithmétique en centimes avec
 * arrondi manuel "round half up" est la seule façon d'éviter les rejets de
 * l'administration fiscale liés aux erreurs de flottant.
 */
data class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)

    override fun compareTo(other: Money) = cents.compareTo(other.cents)

    companion object {
        val ZERO = Money(0)
    }
}

/**
 * Taux de TVA exprimé en points de base (1/100 de %) pour rester en arithmétique entière.
 * Ex: 2000 = 20.00%, 550 = 5.50%.
 */
private const val BASIS_POINTS_DIVISOR = 10_000L

/** Calcule la TVA d'un montant HT, arrondie au centime le plus proche (round half up). */
fun Money.vatFor(rateBasisPoints: Int): Money {
    val product = cents * rateBasisPoints
    val roundedQuotient = (product + BASIS_POINTS_DIVISOR / 2) / BASIS_POINTS_DIVISOR
    return Money(roundedQuotient)
}

/**
 * Parse une saisie utilisateur ("12.50" ou "12,50") en centimes.
 * Retourne null si la saisie n'est pas un montant positif à 0-2 décimales.
 */
fun parseAmountToCents(raw: String): Long? {
    val normalized = raw.trim().replace(',', '.')
    val match = Regex("""^(\d+)(?:\.(\d{1,2}))?$""").matchEntire(normalized) ?: return null
    val (wholePart, decimalPart) = match.destructured
    val cents = decimalPart.padEnd(2, '0').ifEmpty { "00" }
    return wholePart.toLong() * 100 + cents.toLong()
}
