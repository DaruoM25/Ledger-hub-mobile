package com.ledgerhub.presentation.auth

import com.ledgerhub.data.auth.KtorAuthRepository
import com.ledgerhub.domain.auth.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel de l'écran de connexion — soumet les identifiants au backend local via [AuthRepository]. */
class LoginViewModel(
    private val authRepository: AuthRepository = KtorAuthRepository(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun processIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> _uiState.update { it.copy(email = intent.value, errorMessage = null) }
            is LoginIntent.PasswordChanged -> _uiState.update { it.copy(password = intent.value, errorMessage = null) }
            LoginIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.isSubmitEnabled) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val result = authRepository.login(state.email, state.password)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, loginSucceeded = true) },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Erreur inconnue lors de la connexion",
                        )
                    },
                )
            }
        }
    }

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : depuis onDestroy() ou rememberViewModel().
     * iOS     : depuis le deinit de la UIViewController.
     */
    fun onCleared() = scope.cancel()
}
