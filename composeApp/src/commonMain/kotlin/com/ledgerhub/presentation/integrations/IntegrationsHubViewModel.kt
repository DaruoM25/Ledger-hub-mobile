package com.ledgerhub.presentation.integrations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel du hub d'intégrations (US-20) — convention maison (cf. `CommandPaletteViewModel`) :
 * classe simple, [MutableStateFlow], aucun `androidx.lifecycle` en commonMain.
 *
 * Sans coroutine ni portée : le catalogue est statique et connu à la compilation. Rien à charger,
 * donc rien à attendre — un état `isLoading` n'aurait ici aucune réalité à décrire.
 */
class IntegrationsHubViewModel {

    private val _uiState = MutableStateFlow(IntegrationsHubUiState())
    val uiState: StateFlow<IntegrationsHubUiState> = _uiState.asStateFlow()

    fun processIntent(intent: IntegrationsHubIntent) {
        when (intent) {
            // Toucher un second module remplace le bandeau au lieu de l'empiler : c'est toujours
            // le dernier geste de l'utilisateur qui est expliqué.
            is IntegrationsHubIntent.ModuleSelected ->
                _uiState.update { it.copy(noticeModule = intent.module) }

            IntegrationsHubIntent.NoticeDismissed ->
                _uiState.update { it.copy(noticeModule = null) }
        }
    }
}
