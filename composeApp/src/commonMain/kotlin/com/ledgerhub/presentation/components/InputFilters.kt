package com.ledgerhub.presentation.components

/**
 * Filtres de saisie partagés entre le formulaire de facture, la fiche client et les paramètres
 * fiscaux. Ils normalisent la frappe pour écarter les saisies structurellement impossibles ; la
 * validation métier (`FiscalValidation`, `parseAmountToCents`) reste seule juge de la conformité.
 */

/** SIRET : 14 chiffres exactement — on ne laisse entrer que des chiffres, et pas un de plus. */
fun filterSiret(input: String): String = input.filter { it.isDigit() }.take(SIRET_LENGTH)

/** Quantité : entier positif, borné pour écarter les saisies aberrantes. */
fun filterQuantity(input: String): String = input.filter { it.isDigit() }.take(QUANTITY_MAX_DIGITS)

/**
 * Montant : chiffres et un séparateur décimal unique. La virgule et le point sont acceptés
 * (claviers FR et EN), `parseAmountToCents` normalise ensuite.
 */
fun filterAmount(input: String): String {
    val builder = StringBuilder()
    var separatorSeen = false
    for (char in input) {
        when {
            char.isDigit() -> builder.append(char)
            (char == ',' || char == '.') && !separatorSeen && builder.isNotEmpty() -> {
                separatorSeen = true
                builder.append(char)
            }
        }
    }
    return builder.toString()
}

/**
 * Numéro de TVA intracommunautaire français : `FR` + clé à 2 caractères + SIREN à 9 chiffres.
 * Alphanumérique, majuscules, 13 caractères au plus.
 */
fun filterVatNumber(input: String): String =
    input.uppercase().filter { it.isLetterOrDigit() }.take(FR_VAT_LENGTH)

/**
 * Date ISO `AAAA-MM-JJ` : seuls les chiffres sont retenus, les deux tirets sont réinsérés à leur
 * place. L'utilisateur n'a donc ni à les taper ni à se tromper de séparateur — et le champ ne peut
 * structurellement pas produire autre chose que la forme attendue par `ExportPeriod`.
 *
 * Le filtre ne juge **pas** la validité de la date : `2026-99-99` en sort intact. C'est la
 * validation métier qui tranche, ici comme ailleurs (voir l'en-tête de ce fichier).
 */
fun filterIsoDate(input: String): String {
    val digits = input.filter { it.isDigit() }.take(ISO_DATE_DIGITS)
    return buildString {
        digits.forEachIndexed { index, digit ->
            if (index == YEAR_DIGITS || index == YEAR_DIGITS + MONTH_DIGITS) append('-')
            append(digit)
        }
    }
}

const val SIRET_LENGTH = 14
private const val QUANTITY_MAX_DIGITS = 6
private const val FR_VAT_LENGTH = 13
private const val YEAR_DIGITS = 4
private const val MONTH_DIGITS = 2
private const val ISO_DATE_DIGITS = 8
