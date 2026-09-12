package com.ledgerhub.presentation.export

import com.ledgerhub.domain.export.AccountingArchiveBuilder
import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.export.ExportPeriod
import com.ledgerhub.domain.export.NoOpDocumentExporter
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.ledgerhub.domain.subscription.SubscriptionRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel de la modale d'export comptable (US-22) — convention maison (cf. `AuthViewModel`) :
 * classe simple, [MutableStateFlow], portée annulable, aucun `androidx.lifecycle` en commonMain.
 */
class ExportViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val documentExporter: DocumentExporter = NoOpDocumentExporter,
    private val taxSettings: TaxSettings = TaxSettings.Default,
    clock: Clock = SystemClock,
    private val generationDuration: Long = GENERATION_MILLIS,
    private val subscriptionRepository: SubscriptionRepository? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Génération en vol. Conservée pour être annulée à la fermeture de la modale. */
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(
        ExportUiState(
            period = ExportPeriod.yearToDate(clock.nowIso()),
            isPro = true,
        ),
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        subscriptionRepository?.observeSubscription()
            ?.onEach { status ->
                _uiState.update { it.copy(isPro = status.isPro) }
            }
            ?.launchIn(scope)
    }

    fun processIntent(intent: ExportIntent) {
        when (intent) {
            is ExportIntent.DateFromChanged ->
                updatePeriod { it.copy(from = intent.value) }

            is ExportIntent.DateToChanged ->
                updatePeriod { it.copy(to = intent.value) }

            is ExportIntent.FormatSelected -> {
                // Changer de format après coup invalide l'archive produite : la garder afficherait
                // un bouton de téléchargement qui ne remettrait pas le format affiché comme actif.
                if (_uiState.value.isGenerating) return
                _uiState.update {
                    it.copy(selectedFormat = intent.format).resetProduction()
                }
            }

            ExportIntent.GenerateRequested -> generate()

            ExportIntent.DownloadRequested -> download()

            ExportIntent.Dismissed -> {
                generationJob?.cancel()
                generationJob = null
                _uiState.update { it.resetProduction() }
            }
        }
    }

    /** Modifier une borne pendant la compression n'aurait aucun effet sur l'archive en cours. */
    private inline fun updatePeriod(transform: (ExportPeriod) -> ExportPeriod) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(period = transform(it.period)).resetProduction() }
    }

    private fun generate() {
        val state = _uiState.value
        // Garde-fou de ré-entrée : sans lui, une double frappe sur le bouton lancerait deux
        // compressions concurrentes dont la plus lente écraserait l'archive de la plus rapide.
        if (!state.isGenerateEnabled) return

        generationJob?.cancel()
        _uiState.update { it.copy(stage = ExportStage.GENERATING, progress = 0f, archive = null) }

        generationJob = scope.launch {
            val invoices = invoiceRepository.fetchInvoices().getOrElse { emptyList() }
            val stepDelay = generationDuration / PROGRESS_STEPS
            repeat(PROGRESS_STEPS) { step ->
                delay(stepDelay)
                _uiState.update { it.copy(progress = (step + 1).toFloat() / PROGRESS_STEPS) }
            }
            val archive = AccountingArchiveBuilder.build(
                format = state.selectedFormat,
                period = state.period,
                invoices = invoices,
                settings = taxSettings,
            )
            _uiState.update {
                it.copy(stage = ExportStage.READY, progress = 1f, archive = archive)
            }
        }
    }

    /**
     * Remise du document à la plateforme. Sans effet hors de [ExportStage.READY] : le bouton
     * n'existe alors pas à l'écran, mais un ViewModel ne se repose pas sur l'absence d'un bouton.
     */
    private fun download() {
        val archive = _uiState.value.archive ?: return
        scope.launch {
            documentExporter.export(
                fileName = archive.fileName,
                mimeType = archive.mimeType,
                content = archive.content,
            )
        }
    }

    /** Retour à l'état de repos, période et format conservés — ce sont des réglages, pas un résultat. */
    private fun ExportUiState.resetProduction(): ExportUiState =
        copy(stage = ExportStage.IDLE, progress = 0f, archive = null)

    /**
     * À appeler depuis le cycle de vie de la plateforme.
     * Android : depuis onDestroy() ou la disparition de l'overlay.
     * iOS     : depuis le deinit de la UIViewController.
     */
    fun onCleared() = scope.cancel()

    companion object {
        /** Durée de la compression simulée, imposée par le cahier des charges US-22. */
        const val GENERATION_MILLIS: Long = 2_000L

        /** 40 paliers de 50 ms : assez fin pour que la barre coule, assez grossier pour rester sobre. */
        const val PROGRESS_STEPS: Int = 40
    }
}
