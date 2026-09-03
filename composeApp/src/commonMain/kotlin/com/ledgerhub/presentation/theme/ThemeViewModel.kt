package com.ledgerhub.presentation.theme

import com.ledgerhub.domain.theme.ThemeMode
import com.ledgerhub.domain.theme.ThemePreferenceRepository
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

/**
 * État du thème de l'application.
 *
 * [isLoaded] distingue « je n'ai pas encore lu la préférence » de « la préférence vaut le défaut ».
 * Sans lui, rien ne permettrait à un test de savoir si le `DARK` affiché est un choix ou une
 * attente.
 */
data class ThemeUiState(
    val mode: ThemeMode = ThemeMode.Default,
    val isLoaded: Boolean = false,
)

sealed interface ThemeIntent {
    /** Relecture de la préférence persistée — émise une fois au démarrage du shell. */
    data object Load : ThemeIntent

    /**
     * Appui sur `theme_toggle_btn`. L'apparence du système est fournie par la couche Compose
     * (`isSystemInDarkTheme`), le ViewModel n'ayant aucun moyen — ni aucune raison — de la lire.
     */
    data class Toggle(val systemIsDark: Boolean) : ThemeIntent

    /** Choix explicite d'un mode (y compris `SYSTEM`) — point d'entrée d'un futur écran Paramètres. */
    data class Select(val mode: ThemeMode) : ThemeIntent
}

/**
 * Gestionnaire d'état réactif du thème (US-25) — patron MVI des autres ViewModels du projet
 * (`TaxSettingsViewModel`), `StateFlow` exposé en lecture seule.
 *
 * ## Ordre volontaire : afficher, puis persister
 *
 * [ThemeIntent.Toggle] met l'état à jour **avant** d'écrire en base. Un aller-retour disque ne doit
 * pas retarder un repeint d'écran : l'utilisateur a appuyé sur un bouton d'apparence, il attend une
 * apparence. Corollaire assumé — un échec d'écriture ne rétablit pas l'ancien thème à l'écran : la
 * préférence sera simplement oubliée au prochain lancement, ce qui vaut mieux qu'un thème qui
 * revient en arrière tout seul sous les yeux de l'utilisateur.
 */
class ThemeViewModel(
    private val repository: ThemePreferenceRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ThemeUiState())
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    fun processIntent(intent: ThemeIntent) {
        when (intent) {
            ThemeIntent.Load -> load()
            is ThemeIntent.Toggle -> select(_uiState.value.mode.toggled(intent.systemIsDark))
            is ThemeIntent.Select -> select(intent.mode)
        }
    }

    private fun load() {
        scope.launch {
            // Une lecture en échec (base verrouillée, table absente sur une installation
            // ancienne) laisse le thème par défaut et marque l'état chargé : l'app démarre, elle
            // ne reste pas suspendue à une préférence d'affichage.
            val mode = repository.loadThemeMode().getOrDefault(ThemeMode.Default)
            _uiState.update { it.copy(mode = mode, isLoaded = true) }
        }
    }

    private fun select(mode: ThemeMode) {
        _uiState.update { it.copy(mode = mode, isLoaded = true) }
        scope.launch { repository.saveThemeMode(mode) }
    }

    fun onCleared() = scope.cancel()
}
