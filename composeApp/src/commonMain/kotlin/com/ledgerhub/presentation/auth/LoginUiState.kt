package com.ledgerhub.presentation.auth

/** État immuable de l'écran de connexion — pattern UDF, symétrique aux autres écrans de formulaire. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSucceeded: Boolean = false,
) {
    val isSubmitEnabled: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}
