package com.ledgerhub.domain.directory

/**
 * Numéro de TVA intracommunautaire français — calcul et vérification par la formule Modulo 97,
 * miroir strict de l'US-09 Web :
 *
 * ```
 * FR + ((12 + 3 * (siren % 97)) % 97).padStart(2, '0') + siren
 * ```
 *
 * `siren` est traité en [Long] : un SIREN à 9 chiffres (jusqu'à 999 999 999) tient dans un `Int`
 * mais le produit `3 * (siren % 97)` reste petit — `Long` est retenu par cohérence avec le Web
 * et pour écarter toute ambiguïté d'arithmétique.
 */
object FrenchVatNumber {

    private val STRUCTURE_REGEX = Regex("""^FR\d{2}\d{9}$""")

    /** Clé à 2 chiffres (`00`–`96`) calculée à partir du SIREN. Précondition : 9 chiffres. */
    fun computeKey(siren: String): String {
        require(siren.length == 9 && siren.all { it in '0'..'9' }) {
            "SIREN attendu : 9 chiffres, reçu « $siren »"
        }
        val sirenValue = siren.toLong()
        val key = (12 + 3 * (sirenValue % 97)) % 97
        return key.toString().padStart(2, '0')
    }

    /** Numéro complet `FR{clé}{siren}`. Précondition : 9 chiffres. */
    fun format(siren: String): String = "FR" + computeKey(siren) + siren

    /**
     * Vérifie un numéro de TVA français : structure `FR` + 2 chiffres + 9 chiffres, **et** clé
     * recalculée depuis le SIREN identique à la clé portée par le numéro.
     */
    fun isValid(vatNumber: String): Boolean {
        val normalized = vatNumber.trim().uppercase()
        if (!STRUCTURE_REGEX.matches(normalized)) return false
        val providedKey = normalized.substring(2, 4)
        val siren = normalized.substring(4)
        return providedKey == computeKey(siren)
    }
}
