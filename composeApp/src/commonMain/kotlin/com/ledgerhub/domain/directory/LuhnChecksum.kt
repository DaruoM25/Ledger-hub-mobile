package com.ledgerhub.domain.directory

/**
 * Clé de contrôle de Luhn — miroir strict de l'US-09 Web.
 *
 * Utilisée pour valider la cohérence interne des identifiants d'entreprise français avant tout
 * appel à l'annuaire : SIREN (9 chiffres) et SIRET (14 chiffres) portent une clé de Luhn sur
 * leur dernier chiffre.
 *
 * Exception officielle **La Poste** : le SIREN `356000000` et l'ensemble de ses établissements
 * (SIRET commençant par `35600000`) ne respectent pas la clé de Luhn mais sont malgré tout des
 * identifiants valides au registre INSEE. Ils sont acceptés explicitement.
 */
object LuhnChecksum {

    /** SIREN de La Poste — dérogation historique à la clé de Luhn (référentiel INSEE). */
    const val LA_POSTE_SIREN = "356000000"

    private const val LA_POSTE_SIRET_PREFIX = "35600000"

    /**
     * Algorithme de Luhn pur sur une chaîne de chiffres.
     *
     * @return `true` si [digits] est non vide, exclusivement numérique, et de somme de Luhn
     *   divisible par 10.
     */
    fun isLuhnValid(digits: String): Boolean {
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return false
        var sum = 0
        // On double un chiffre sur deux en partant de la droite (le chiffre de contrôle n'est
        // jamais doublé).
        val lastIndex = digits.lastIndex
        for (i in lastIndex downTo 0) {
            var d = digits[i] - '0'
            val positionFromRight = lastIndex - i
            if (positionFromRight % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    /** SIREN valide : exactement 9 chiffres et clé de Luhn correcte (ou dérogation La Poste). */
    fun isValidSiren(siren: String): Boolean {
        if (siren.length != 9 || !siren.all { it in '0'..'9' }) return false
        if (siren == LA_POSTE_SIREN) return true
        return isLuhnValid(siren)
    }

    /** SIRET valide : exactement 14 chiffres et clé de Luhn correcte (ou dérogation La Poste). */
    fun isValidSiret(siret: String): Boolean {
        if (siret.length != 14 || !siret.all { it in '0'..'9' }) return false
        if (siret.startsWith(LA_POSTE_SIRET_PREFIX)) return true
        return isLuhnValid(siret)
    }
}
