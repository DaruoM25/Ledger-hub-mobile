package com.ledgerhub.presentation.hello

/**
 * État UI immuable — pattern UDF (Unidirectional Data Flow).
 * Une seule source de vérité consommée par [HelloScreenContent].
 */
data class HelloUiState(
    val isLoading: Boolean = true,
    val message: String = "",
    val error: String? = null
)

/** Intentions utilisateur — seul point d'entrée accepté par [HelloViewModel.processIntent]. */
sealed interface HelloIntent {
    data object LoadWelcomeMessage : HelloIntent
    data object RetryLoad : HelloIntent
}
