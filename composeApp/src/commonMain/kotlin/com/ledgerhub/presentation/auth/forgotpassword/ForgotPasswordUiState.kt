package com.ledgerhub.presentation.auth.forgotpassword

import com.ledgerhub.domain.auth.EmailValidator

/**
 * État MVI de l'écran de réinitialisation de mot de passe (US-26).
 */
data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
) {
    /** L'adresse doit satisfaire les critères stricts du domaine. */
    val isEmailValid: Boolean get() = EmailValidator.isValid(email)

    /** Le bouton de soumission est actif dès que l'e-mail est valide et hors chargement. */
    val canSubmit: Boolean get() = isEmailValid && !isLoading
}
