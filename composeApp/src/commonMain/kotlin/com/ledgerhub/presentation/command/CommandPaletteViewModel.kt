package com.ledgerhub.presentation.command

import com.ledgerhub.domain.command.CommandActionFilter
import com.ledgerhub.domain.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel de la palette de commandes (US-19) — convention maison (cf. `DirectoryViewModel`) :
 * classe simple, [MutableStateFlow], aucun `androidx.lifecycle` en commonMain.
 *
 * Sans coroutine ni portée : le filtrage est synchrone et immédiat. Une palette qui accuserait un
 * aller-retour asynchrone entre la frappe et le résultat perdrait sa raison d'être — c'est un
 * outil qu'on utilise sans lever les yeux du clavier.
 *
 * La langue est passée à chaque intention plutôt que capturée à la construction : les mots-clés de
 * recherche diffèrent d'une langue à l'autre, et la palette doit suivre un changement de langue
 * sans avoir à être recréée.
 */
class CommandPaletteViewModel {

    private val _uiState = MutableStateFlow(CommandPaletteUiState())
    val uiState: StateFlow<CommandPaletteUiState> = _uiState.asStateFlow()

    fun processIntent(intent: CommandPaletteIntent, language: AppLanguage = AppLanguage.FR) {
        when (intent) {
            CommandPaletteIntent.Open -> open(language)
            CommandPaletteIntent.Close -> close()
            is CommandPaletteIntent.UpdateQuery -> updateQuery(intent.value, language)
            is CommandPaletteIntent.ExecuteAction -> _uiState.update {
                // La palette se ferme dans le meme temps qu'elle publie le choix : laisser la
                // modale ouverte derriere l'ecran d'arrivee serait deroutant.
                it.copy(isOpen = false, query = "", executedAction = intent.action)
            }

            CommandPaletteIntent.ActionConsumed -> _uiState.update { it.copy(executedAction = null) }
        }
    }

    /**
     * L'ouverture repart d'une requête vierge et de la liste complète : rouvrir la palette sur la
     * recherche précédente obligerait à effacer avant de saisir, à chaque fois.
     */
    private fun open(language: AppLanguage) = _uiState.update {
        it.copy(isOpen = true, query = "", results = CommandActionFilter.filter("", language))
    }

    private fun close() = _uiState.update { it.copy(isOpen = false, query = "") }

    private fun updateQuery(value: String, language: AppLanguage) = _uiState.update {
        it.copy(query = value, results = CommandActionFilter.filter(value, language))
    }
}
