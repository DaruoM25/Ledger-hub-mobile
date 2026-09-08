package com.ledgerhub.presentation.auth.forgotpassword

/**
 * Intentions utilisateur pour le flux de réinitialisation de mot de passe (MVI).
 */
sealed interface ForgotPasswordIntent {
    data class EmailChanged(val value: String) : ForgotPasswordIntent
    data object Submit : ForgotPasswordIntent
    data object ResetState : ForgotPasswordIntent
}
