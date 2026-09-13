package com.ledgerhub.presentation.degraded

import com.ledgerhub.domain.degraded.ProcessSyncQueueBatchUseCase
import com.ledgerhub.domain.degraded.SyncBatchResult
import com.ledgerhub.domain.degraded.SyncQueueRepository
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
 * État immuable de la file de synchronisation en mode dégradé (US-29).
 */
data class SyncQueueUiState(
    val pendingCount: Long = 0L,
    val isSyncing: Boolean = false,
    val lastSyncResult: SyncBatchResult? = null,
    val errorMessage: String? = null,
)

/**
 * ViewModel orchestrant la file de synchronisation et la télétransmission groupée (US-29).
 */
class SyncQueueViewModel(
    private val syncQueueRepository: SyncQueueRepository,
    private val processSyncQueueBatchUseCase: ProcessSyncQueueBatchUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(SyncQueueUiState())
    val uiState: StateFlow<SyncQueueUiState> = _uiState.asStateFlow()

    init {
        loadPendingCount()
    }

    fun loadPendingCount() {
        scope.launch {
            syncQueueRepository.countPending().fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(pendingCount = count, errorMessage = null) }
                },
                onFailure = { throwable ->
                    _uiState.update { it.copy(errorMessage = throwable.message) }
                },
            )
        }
    }

    fun processBatch(onComplete: () -> Unit = {}) {
        if (_uiState.value.isSyncing) return
        _uiState.update { it.copy(isSyncing = true, errorMessage = null) }

        scope.launch {
            processSyncQueueBatchUseCase().fold(
                onSuccess = { result ->
                    val newCount = syncQueueRepository.countPending().getOrDefault(0L)
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            pendingCount = newCount,
                            lastSyncResult = result,
                            errorMessage = null,
                        )
                    }
                    onComplete()
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            errorMessage = throwable.message ?: "Échec de la régularisation",
                        )
                    }
                },
            )
        }
    }

    fun onCleared() = scope.cancel()
}
