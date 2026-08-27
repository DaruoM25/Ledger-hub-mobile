package com.ledgerhub.domain.invoice

/** Résultat d'une validation métier — pas d'exception, un état explicite consommable par l'UI. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

/**
 * Validation stricte des identifiants d'entreprise avant tout envoi de payload au backend.
 * SIREN : exactement 9 chiffres. SIRET : exactement 14 chiffres.
 */
object FiscalValidation {

    fun validateSiren(siren: String): ValidationResult =
        if (siren.length == 9 && siren.all(Char::isDigit)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Le SIREN doit comporter exactement 9 chiffres")
        }

    fun validateSiret(siret: String): ValidationResult =
        if (siret.length == 14 && siret.all(Char::isDigit)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Le SIRET doit comporter exactement 14 chiffres")
        }
}
