package com.ledgerhub.presentation.hello

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel KMP — PAS d'héritage Android ViewModel (impossible dans commonMain).
 *
 * PATTERN UDF / MVVM :
 * - Une seule [StateFlow] en sortie → la vue est déterministe.
 * - Un seul point d'entrée [processIntent] → tous les changements sont traçables.
 *
 * @param dispatcher Injecté pour permettre les tests sans dépendance au thread réel.
 *                   Production → [Dispatchers.Default]
 *                   Tests      → [StandardTestDispatcher]
 */
class HelloViewModel(
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(HelloUiState())
    val uiState: StateFlow<HelloUiState> = _uiState.asStateFlow()

    init {
        processIntent(HelloIntent.LoadWelcomeMessage)
    }

    /** Point d'entrée unique — toutes les actions utilisateur passent ici. */
    fun processIntent(intent: HelloIntent) {
        when (intent) {
            HelloIntent.LoadWelcomeMessage,
            HelloIntent.RetryLoad -> loadWelcomeMessage()
        }
    }

    private fun loadWelcomeMessage() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            runCatching {
                // TODO Sprint 2 : remplacer par un UseCase → Ktor → backend Kubernetes
                // Ex: val msg = welcomeUseCase.execute()
                delay(600L)
                "Bienvenue sur LedgerHub — Factur-X 2026 Ready!"
            }.fold(
                onSuccess = { message ->
                    _uiState.update { it.copy(isLoading = false, message = message) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Erreur de connexion au serveur"
                        )
                    }
                }
            )
        }
    }

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : depuis onDestroy() ou rememberViewModel().
     * iOS     : depuis le deinit de la UIViewController.
     */
    fun onCleared() = scope.cancel()
}
