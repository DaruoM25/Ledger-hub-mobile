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

const val SIRET_LENGTH = 14
private const val QUANTITY_MAX_DIGITS = 6
private const val FR_VAT_LENGTH = 13
