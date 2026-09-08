package com.ledgerhub.presentation.auth.forgotpassword

import com.ledgerhub.domain.auth.RequestPasswordResetUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel MVI pour le flux de mot de passe oublié (US-26).
 *
 * Découplé de tout composant plateforme Android/iOS, respectant le cycle de vie KMP
 * et garantissant une immunité contre l'énumération d'adresses email.
 */
class ForgotPasswordViewModel(
    private val requestPasswordResetUseCase: RequestPasswordResetUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<ForgotPasswordSideEffect>(Channel.BUFFERED)
    val sideEffect: Flow<ForgotPasswordSideEffect> = _sideEffect.receiveAsFlow()

    fun processIntent(intent: ForgotPasswordIntent) {
        when (intent) {
            is ForgotPasswordIntent.EmailChanged -> _uiState.update {
                it.copy(email = intent.value, errorMessage = null)
            }

            ForgotPasswordIntent.Submit -> submit()

            ForgotPasswordIntent.ResetState -> _uiState.update {
                ForgotPasswordUiState()
            }
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        scope.launch {
            try {
                val result = requestPasswordResetUseCase.execute(state.email)
                _uiState.update { current ->
                    result.fold(
                        onSuccess = { current.copy(isLoading = false, isSubmitted = true) },
                        onFailure = { error ->
                            current.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Une erreur est survenue.",
                            )
                        },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Une erreur est survenue.",
                    )
                }
            }
        }
    }

    fun onBackToLogin() {
        scope.launch {
            _sideEffect.send(ForgotPasswordSideEffect.NavigateBackToLogin)
        }
    }

    fun onCleared() = scope.cancel()
}
