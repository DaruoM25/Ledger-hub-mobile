package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.invoice.Money
import kotlin.math.abs

/**
 * Conversions de format exigées par le profil BASIC : montants décimaux, dates au format 102 et
 * taux en pourcentage. Toutes opèrent en arithmétique entière — aucun `Double` ne doit s'approcher
 * d'un montant destiné à l'administration fiscale (même règle que [Money]).
 */
object FacturXFormat {

    /** Devise unique de l'application. */
    const val CURRENCY = "EUR"

    /**
     * Montant décimal à deux chiffres : `220000` centimes → `"2200.00"`, `-44000` → `"-440.00"`.
     * Séparateur décimal **point**, indépendamment de la langue de l'interface : c'est le format
     * du schéma (`xs:decimal`), pas un affichage utilisateur.
     */
    fun amount(money: Money): String = amount(money.cents)

    fun amount(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    /**
     * Date au format 102 (`AAAAMMJJ`) à partir d'une date ISO `AAAA-MM-JJ`.
     * Une entrée non conforme est renvoyée débarrassée de ses tirets plutôt que rejetée : le
     * générateur ne doit jamais lever, la validation XSD signalera le cas échéant.
     */
    fun date102(isoDate: String): String = isoDate.trim().replace("-", "")

    /**
     * Taux en pourcentage décimal : 2000 points de base → `"20.00"`, 550 → `"5.50"`.
     * Les points de base sont des centièmes de pourcent, donc la même mise à l'échelle que
     * les centimes — d'où la réutilisation de [amount].
     */
    fun percent(basisPoints: Int): String = amount(basisPoints.toLong())
}
