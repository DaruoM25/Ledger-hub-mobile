package com.ledgerhub.presentation.export

import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.export.ExportFormat
import com.ledgerhub.domain.export.ExportPeriod
import com.ledgerhub.domain.export.ExportPeriodError
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dépôt de lecture seule : l'export ne fait qu'interroger, il n'écrit jamais. */
private class FakeInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
    var fetchCount = 0

    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> =
        error("L'export ne soumet aucune facture")

    override suspend fun fetchInvoices(): Result<List<Invoice>> {
        fetchCount++
        return Result.success(invoices)
    }
}

/** Dépôt en panne — l'export doit rester une action sans conséquence, pas un plantage. */
private class FailingInvoiceRepository : InvoiceRepository {
    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> =
        error("L'export ne soumet aucune facture")

    override suspend fun fetchInvoices(): Result<List<Invoice>> =
        Result.failure(IllegalStateException("base indisponible"))
}

/** Double d'export : capture l'appel sans dépendre d'un runtime de partage. */
private class RecordingDocumentExporter : DocumentExporter {
    var fileName: String? = null
    var mimeType: String? = null
    var content: String? = null
    var callCount = 0

    override suspend fun export(fileName: String, mimeType: String, content: String): Result<Unit> {
        this.fileName = fileName
        this.mimeType = mimeType
        this.content = content
        callCount++
        return Result.success(Unit)
    }
}

/**
 * Niveau 2 (US-22) — machine à états de la modale d'export comptable.
 *
 * Toute la temporalité est éprouvée en **temps virtuel** : la durée de production
 * ([ExportViewModel.GENERATION_MILLIS]) est celle qui partira en production, sans que la suite
 * n'attende deux secondes réelles ni ne parie sur l'ordonnanceur.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExportViewModelTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")

    private fun invoice(number: String, issueDate: String) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = issuer,
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
        dueDate = "2026-04-03",
    )

    private val invoices = listOf(
        invoice("FAC-2025-0009", "2025-11-02"),
        invoice("FAC-2026-0001", "2026-03-04"),
        invoice("FAC-2026-0002", "2026-06-15"),
    )

    private fun viewModel(
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        repository: InvoiceRepository = FakeInvoiceRepository(invoices),
        exporter: DocumentExporter = RecordingDocumentExporter(),
    ) = ExportViewModel(
        invoiceRepository = repository,
        documentExporter = exporter,
        clock = FixedClock("2026-09-02T08:00:00Z"),
        dispatcher = StandardTestDispatcher(scheduler),
    )

    // ── État initial ────────────────────────────────────────────────────────

    @Test
    fun atRest_theModalOffersTheCurrentFiscalYearAndTheFecFormat() = runTest {
        val state = viewModel(testScheduler).uiState.value

        assertEquals(ExportStage.IDLE, state.stage)
        assertEquals(ExportPeriod("2026-01-01", "2026-09-02"), state.period)
        assertEquals(ExportFormat.FEC_OFFICIAL, state.selectedFormat)
        assertEquals(0f, state.progress)
        assertNull(state.archive)
        assertNull(state.periodError)
        assertTrue(state.isGenerateEnabled)
    }

    // ── Réglages ────────────────────────────────────────────────────────────

    @Test
    fun selectingAFormat_replacesTheCurrentOne() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.FormatSelected(ExportFormat.FACTURX_ARCHIVE))
        assertEquals(ExportFormat.FACTURX_ARCHIVE, vm.uiState.value.selectedFormat)

        vm.processIntent(ExportIntent.FormatSelected(ExportFormat.EXCEL_SUMMARY))
        assertEquals(ExportFormat.EXCEL_SUMMARY, vm.uiState.value.selectedFormat)
    }

    @Test
    fun editingABound_updatesThePeriod() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.DateFromChanged("2026-04-01"))
        vm.processIntent(ExportIntent.DateToChanged("2026-06-30"))

        assertEquals(ExportPeriod("2026-04-01", "2026-06-30"), vm.uiState.value.period)
    }

    @Test
    fun anInvertedPeriod_reportsItsReasonAndBlocksGeneration() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.DateToChanged("2025-01-01"))

        assertEquals(ExportPeriodError.RANGE_INVERTED, vm.uiState.value.periodError)
        assertTrue(!vm.uiState.value.isGenerateEnabled)
    }

    @Test
    fun anIncompleteDate_blocksGenerationWithoutLaunchingAnything() = runTest {
        val repository = FakeInvoiceRepository(invoices)
        val vm = viewModel(testScheduler, repository = repository)

        vm.processIntent(ExportIntent.DateFromChanged("2026-04"))
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        assertEquals(ExportPeriodError.FROM_INVALID, vm.uiState.value.periodError)
        assertEquals(ExportStage.IDLE, vm.uiState.value.stage)
        assertEquals(0, repository.fetchCount)
    }

    // ── Génération ──────────────────────────────────────────────────────────

    @Test
    fun requestingGeneration_entersTheGeneratingStageImmediately() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.GenerateRequested)

        assertEquals(ExportStage.GENERATING, vm.uiState.value.stage)
        assertEquals(0f, vm.uiState.value.progress)
        advanceUntilIdle()
    }

    /** La progression doit être **observable** : une barre qui saute de 0 à 100 % ne dit rien. */
    @Test
    fun midFlight_theProgressSitsStrictlyBetweenItsBounds() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.GenerateRequested)
        advanceTimeBy(ExportViewModel.GENERATION_MILLIS / 2)

        val state = vm.uiState.value
        assertEquals(ExportStage.GENERATING, state.stage)
        assertTrue(state.progress > 0f, "Progression restée à zéro à mi-parcours")
        assertTrue(state.progress < 1f, "Progression déjà achevée à mi-parcours")
        advanceUntilIdle()
    }

    @Test
    fun theProgress_growsMonotonicallyWithinItsBounds() = runTest {
        val vm = viewModel(testScheduler)
        val samples = mutableListOf<Float>()

        vm.processIntent(ExportIntent.GenerateRequested)
        repeat(ExportViewModel.PROGRESS_STEPS) {
            advanceTimeBy(ExportViewModel.GENERATION_MILLIS / ExportViewModel.PROGRESS_STEPS)
            samples += vm.uiState.value.progress
        }
        advanceUntilIdle()

        assertTrue(samples.all { it in 0f..1f }, "Progression hors bornes : $samples")
        assertEquals(samples.sorted(), samples, "Progression non monotone : $samples")
        assertEquals(1f, vm.uiState.value.progress)
    }

    @Test
    fun onceTheDurationHasElapsed_theArchiveIsReady() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ExportStage.READY, state.stage)
        assertEquals(1f, state.progress)
        val archive = assertNotNull(state.archive)
        assertEquals(ExportFormat.FEC_OFFICIAL, archive.format)
        // Deux factures de 2026 sur les trois du dépôt : la période a bien filtré.
        assertEquals(2, archive.documentCount)
    }

    @Test
    fun theArchive_followsTheSelectedFormat() = runTest {
        val vm = viewModel(testScheduler)

        vm.processIntent(ExportIntent.FormatSelected(ExportFormat.EXCEL_SUMMARY))
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        val archive = assertNotNull(vm.uiState.value.archive)
        assertEquals(ExportFormat.EXCEL_SUMMARY, archive.format)
        assertEquals("text/csv", archive.mimeType)
        assertTrue(archive.fileName.endsWith(".csv"))
    }

    /**
     * Sans garde-fou, une double frappe lancerait deux compressions concurrentes dont la plus
     * lente écraserait l'archive de la plus rapide.
     */
    @Test
    fun aSecondRequestInFlight_isIgnored() = runTest {
        val repository = FakeInvoiceRepository(invoices)
        val vm = viewModel(testScheduler, repository = repository)

        vm.processIntent(ExportIntent.GenerateRequested)
        advanceTimeBy(ExportViewModel.GENERATION_MILLIS / 2)
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        assertEquals(1, repository.fetchCount)
        assertEquals(ExportStage.READY, vm.uiState.value.stage)
    }

    /** Une modale figée sur « compression » ne dirait pas à l'utilisateur qu'il peut réessayer. */
    @Test
    fun anUnavailableRepository_stillProducesAnArchive_empty() = runTest {
        val vm = viewModel(testScheduler, repository = FailingInvoiceRepository())

        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        assertEquals(ExportStage.READY, vm.uiState.value.stage)
        assertEquals(0, assertNotNull(vm.uiState.value.archive).documentCount)
    }

    // ── Invalidation d'une archive périmée ──────────────────────────────────

    @Test
    fun changingTheFormatAfterGenerating_invalidatesTheArchive() = runTest {
        val vm = viewModel(testScheduler)
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        vm.processIntent(ExportIntent.FormatSelected(ExportFormat.FACTURX_ARCHIVE))

        assertEquals(ExportStage.IDLE, vm.uiState.value.stage)
        assertNull(vm.uiState.value.archive)
    }

    @Test
    fun changingThePeriodAfterGenerating_invalidatesTheArchive() = runTest {
        val vm = viewModel(testScheduler)
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        vm.processIntent(ExportIntent.DateFromChanged("2026-02-01"))

        assertEquals(ExportStage.IDLE, vm.uiState.value.stage)
        assertNull(vm.uiState.value.archive)
    }

    /** Les réglages ne bougent pas pendant la compression : l'archive en cours les a déjà figés. */
    @Test
    fun duringGeneration_theSettingsAreFrozen() = runTest {
        val vm = viewModel(testScheduler)
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceTimeBy(ExportViewModel.GENERATION_MILLIS / 2)

        vm.processIntent(ExportIntent.FormatSelected(ExportFormat.EXCEL_SUMMARY))
        vm.processIntent(ExportIntent.DateFromChanged("2026-02-01"))

        assertEquals(ExportFormat.FEC_OFFICIAL, vm.uiState.value.selectedFormat)
        assertEquals("2026-01-01", vm.uiState.value.period.from)
        assertEquals(ExportStage.GENERATING, vm.uiState.value.stage)
        advanceUntilIdle()
    }

    // ── Téléchargement ──────────────────────────────────────────────────────

    @Test
    fun downloadingBeforeTheArchiveExists_doesNothing() = runTest {
        val exporter = RecordingDocumentExporter()
        val vm = viewModel(testScheduler, exporter = exporter)

        vm.processIntent(ExportIntent.DownloadRequested)
        advanceUntilIdle()

        assertEquals(0, exporter.callCount)
    }

    @Test
    fun downloading_handsTheArchiveToThePlatform() = runTest {
        val exporter = RecordingDocumentExporter()
        val vm = viewModel(testScheduler, exporter = exporter)
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceUntilIdle()

        vm.processIntent(ExportIntent.DownloadRequested)
        advanceUntilIdle()

        val archive = assertNotNull(vm.uiState.value.archive)
        assertEquals(1, exporter.callCount)
        assertEquals(archive.fileName, exporter.fileName)
        assertEquals(archive.mimeType, exporter.mimeType)
        assertEquals(archive.content, exporter.content)
    }

    // ── Fermeture ───────────────────────────────────────────────────────────

    /** Une génération en vol publierait sinon son archive dans une modale déjà refermée. */
    @Test
    fun dismissing_cancelsAGenerationInFlight() = runTest {
        val vm = viewModel(testScheduler)
        vm.processIntent(ExportIntent.GenerateRequested)
        advanceTimeBy(ExportViewModel.GENERATION_MILLIS / 2)

        vm.processIntent(ExportIntent.Dismissed)
        advanceUntilIdle()

        assertEquals(ExportStage.IDLE, vm.uiState.value.stage)
        assertEquals(0f, vm.uiState.value.progress)
        assertNull(vm.uiState.value.archive)
    }

    /** Période et format sont des réglages, pas un résultat : ils survivent à une fermeture. */
    @Test
    fun dismissing_keepsThePeriodAndTheFormat() = runTest {
        val vm = viewModel(testScheduler)
        vm.processIntent(ExportIntent.FormatSelected(ExportFormat.EXCEL_SUMMARY))
        vm.processIntent(ExportIntent.DateFromChanged("2026-02-01"))

        vm.processIntent(ExportIntent.Dismissed)

        assertEquals(ExportFormat.EXCEL_SUMMARY, vm.uiState.value.selectedFormat)
        assertEquals("2026-02-01", vm.uiState.value.period.from)
    }
}
