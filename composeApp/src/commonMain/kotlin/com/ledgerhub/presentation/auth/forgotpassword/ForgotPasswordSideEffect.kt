package com.ledgerhub.presentation.auth.forgotpassword

/**
 * Effets de bord ponctuels émis par [ForgotPasswordViewModel].
 */
sealed interface ForgotPasswordSideEffect {
    data object NavigateBackToLogin : ForgotPasswordSideEffect
}
