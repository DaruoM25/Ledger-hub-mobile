package com.ledgerhub.presentation.ereporting

import com.ledgerhub.domain.ereporting.AlreadyAcknowledgedException
import com.ledgerhub.domain.ereporting.EReportingReport
import com.ledgerhub.domain.ereporting.EReportingRepository
import com.ledgerhub.domain.ereporting.TransmitEReportUseCase
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

/** Intentions de l'écran e-Reporting (UDF). */
sealed interface EReportingIntent {
    /** Chargement initial ou rechargement de la liste. */
    data object Load : EReportingIntent

    /** Transmission au PPF de la déclaration [reportId] (active uniquement si DRAFT). */
    data class Transmit(val reportId: String) : EReportingIntent

    /** L'UI a consommé le message d'accusé ou d'erreur courant. */
    data object MessageShown : EReportingIntent
}

/**
 * État immuable de l'écran e-Reporting.
 *
 * @param transmittingId identifiant de la déclaration dont la transmission est en cours ; sert à
 *   désactiver son bouton et à empêcher une seconde transmission concurrente.
 * @param ackMessage message de confirmation portant le numéro d'accusé, à afficher en Snackbar.
 */
data class EReportingUiState(
    val isLoading: Boolean = false,
    val reports: List<EReportingReport> = emptyList(),
    val errorMessage: String? = null,
    val ackMessage: String? = null,
    val transmittingId: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && reports.isEmpty()
}

/**
 * ViewModel de l'écran e-Reporting — convention maison (cf. `ClientsViewModel`) : classe simple,
 * [CoroutineScope] + [MutableStateFlow], aucun `androidx.lifecycle` en commonMain.
 *
 * @param dispatcher injecté pour des tests sans dépendance au thread réel.
 */
class EReportingViewModel(
    private val repository: EReportingRepository,
    private val transmitEReportUseCase: TransmitEReportUseCase = TransmitEReportUseCase(repository),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(EReportingUiState())
    val uiState: StateFlow<EReportingUiState> = _uiState.asStateFlow()

    init {
        processIntent(EReportingIntent.Load)
    }

    fun processIntent(intent: EReportingIntent) {
        when (intent) {
            EReportingIntent.Load -> load()
            is EReportingIntent.Transmit -> transmit(intent.reportId)
            EReportingIntent.MessageShown ->
                _uiState.update { it.copy(ackMessage = null, errorMessage = null) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            runCatching { repository.getAll() }.fold(
                onSuccess = { reports ->
                    _uiState.update { it.copy(isLoading = false, reports = reports) }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Chargement des déclarations impossible",
                        )
                    }
                },
            )
        }
    }

    private fun transmit(reportId: String) {
        // Une transmission déjà en vol interdit d'en lancer une autre : l'accusé du PPF n'est pas
        // rejouable.
        if (_uiState.value.transmittingId != null) return

        _uiState.update { it.copy(transmittingId = reportId, errorMessage = null, ackMessage = null) }
        scope.launch {
            runCatching { transmitEReportUseCase(reportId) }.fold(
                onSuccess = { report ->
                    _uiState.update { current ->
                        current.copy(
                            transmittingId = null,
                            ackMessage = "Déclaration transmise au PPF — accusé ${report.ackNumber}",
                            reports = current.reports.map { if (it.id == report.id) report else it },
                        )
                    }
                },
                onFailure = { throwable ->
                    val message = when (throwable) {
                        is AlreadyAcknowledgedException ->
                            "Cette déclaration a déjà été transmise et acquittée (HTTP 409)"
                        else -> throwable.message ?: "Transmission au PPF impossible"
                    }
                    _uiState.update { it.copy(transmittingId = null, errorMessage = message) }
                },
            )
        }
    }

    fun onCleared() = scope.cancel()
}
