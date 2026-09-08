package com.ledgerhub.domain.auth

/**
 * Résultat typé de la validation de robustesse d'un mot de passe.
 */
sealed interface PasswordValidationResult {
    data object Valid : PasswordValidationResult
    data class Invalid(val reason: String) : PasswordValidationResult
}

/**
 * Validateur de robustesse de mot de passe pur Kotlin multiplateforme (sans dépendance Android/Java).
 *
 * Exigences de sécurité DGFiP / RGPD :
 * - Longueur minimale : 8 caractères
 * - Au moins une lettre majuscule (`[A-Z]`)
 * - Au moins un chiffre (`[0-9]`)
 * - Au moins un caractère spécial (`[^A-Za-z0-9]`)
 */
object PasswordValidator {

    const val ERROR_MESSAGE =
        "Le mot de passe doit comporter au moins 8 caractères, une majuscule, un chiffre et un caractère spécial."

    private val UPPERCASE_REGEX = Regex("[A-Z]")
    private val DIGIT_REGEX = Regex("[0-9]")
    private val SPECIAL_CHAR_REGEX = Regex("[^A-Za-z0-9]")

    /**
     * Vérifie si le mot de passe respecte tous les critères de complexité.
     */
    fun isValid(password: String): Boolean {
        if (password.length < 8) return false
        if (!password.contains(UPPERCASE_REGEX)) return false
        if (!password.contains(DIGIT_REGEX)) return false
        if (!password.contains(SPECIAL_CHAR_REGEX)) return false
        return true
    }

    /**
     * Valide le mot de passe et retourne un résultat typé portant le message réglementaire en cas d'échec.
     */
    fun validate(password: String): PasswordValidationResult {
        return if (isValid(password)) {
            PasswordValidationResult.Valid
        } else {
            PasswordValidationResult.Invalid(ERROR_MESSAGE)
        }
    }
}
