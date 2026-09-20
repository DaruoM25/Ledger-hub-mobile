package com.ledgerhub.presentation.directory

import com.ledgerhub.data.directory.MockDirectoryRepository
import com.ledgerhub.domain.directory.DirectoryLookupResult
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.IdentifierKind
import com.ledgerhub.domain.directory.LuhnChecksum
import com.ledgerhub.domain.directory.ResolveDirectoryEntryUseCase
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
 * ViewModel de l'écran Annuaire DGFIP — convention maison (cf. `ClientsViewModel`) : classe
 * simple, [CoroutineScope] + [MutableStateFlow], aucun `androidx.lifecycle` en commonMain.
 *
 * Le contrôle de clé de Luhn est **synchrone** : recalculé à chaque frappe pour un retour
 * immédiat. Seule la résolution ([ResolveDirectoryEntryUseCase]) est asynchrone.
 *
 * @param dispatcher injecté pour des tests sans dépendance au thread réel.
 */
class DirectoryViewModel(
    private val repository: DirectoryRepository,
    private val resolveUseCase: ResolveDirectoryEntryUseCase = ResolveDirectoryEntryUseCase(repository),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()

    fun processIntent(intent: DirectoryIntent) {
        when (intent) {
            is DirectoryIntent.QueryChanged -> onQueryChanged(intent.value)
            DirectoryIntent.Search -> search()
            DirectoryIntent.MessageShown -> _uiState.update { it.copy(errorMessage = null) }
        }
    }

    private fun onQueryChanged(value: String) {
        val digits = ResolveDirectoryEntryUseCase.normalize(value)
        val kind = ResolveDirectoryEntryUseCase.identifierKindOf(digits)
        val luhnValid = when (kind) {
            IdentifierKind.SIREN -> LuhnChecksum.isValidSiren(digits)
            IdentifierKind.SIRET -> LuhnChecksum.isValidSiret(digits)
            IdentifierKind.UNKNOWN -> null
        }
        _uiState.update {
            it.copy(
                query = value,
                identifierKind = kind,
                luhnValid = luhnValid,
                // Toute nouvelle saisie invalide la fiche précédemment affichée.
                resolved = null,
                notFound = false,
                errorMessage = null,
            )
        }
    }

    private fun search() {
        val current = _uiState.value
        if (!current.isSearchEnabled) return

        _uiState.update { it.copy(isSearching = true, resolved = null, notFound = false, errorMessage = null) }
        scope.launch {
            val result = runCatching { resolveUseCase(current.query) }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { lookup ->
                        when (lookup) {
                            is DirectoryLookupResult.Resolved ->
                                state.copy(isSearching = false, resolved = lookup.entry, notFound = false)

                            DirectoryLookupResult.NotFound ->
                                state.copy(isSearching = false, resolved = null, notFound = true)

                            DirectoryLookupResult.InvalidChecksum ->
                                state.copy(
                                    isSearching = false,
                                    resolved = null,
                                    notFound = false,
                                    errorMessage = "Clé de contrôle invalide",
                                )
                        }
                    },
                    onFailure = { throwable ->
                        state.copy(
                            isSearching = false,
                            errorMessage = throwable.message ?: "Résolution de l'annuaire impossible",
                        )
                    },
                )
            }
        }
    }

    fun onCleared() = scope.cancel()
}
