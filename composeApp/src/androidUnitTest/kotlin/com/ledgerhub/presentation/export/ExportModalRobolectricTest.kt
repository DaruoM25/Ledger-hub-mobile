package com.ledgerhub.presentation.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.export.AccountingArchive
import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.export.ExportFormat
import com.ledgerhub.domain.export.ExportPeriod
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.time.FixedClock
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Dépôt de lecture seule : l'export ne fait qu'interroger. */
private class StubInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> =
        error("L'export ne soumet aucune facture")

    override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
}

/** Double d'export : capture l'appel sans dépendre d'un runtime de partage. */
private class CapturingDocumentExporter : DocumentExporter {
    var fileName: String? = null
    var mimeType: String? = null
    var callCount = 0

    override suspend fun export(fileName: String, mimeType: String, content: String): Result<Unit> {
        this.fileName = fileName
        this.mimeType = mimeType
        callCount++
        return Result.success(Unit)
    }
}

/**
 * Niveau 3a (US-22) — rendu et parcours de la modale d'export sous Robolectric (JVM, CI sans
 * émulateur).
 *
 * Les formats, les bornes et la machine à états sont déjà verrouillés aux niveaux 1 et 2 : ce
 * niveau vérifie que **l'écran les montre** — les neuf tags imposés, la sélection d'une carte, la
 * barre de progression pendant la compression, et le bouton de téléchargement à l'arrivée.
 *
 * ## Deux modes de câblage, et pourquoi
 *
 * Le parcours (sélection, saisie, génération de bout en bout) est relié à un **vrai**
 * [ExportViewModel] : câbler un état figé ferait passer les tests sur une interface qui ne réagit
 * à rien. Les deux étapes fugaces — compression en cours, archive prête — sont en revanche rendues
 * depuis un état **fixe**. Ce n'est pas un raccourci : `assertIsDisplayed()` attend d'abord que
 * l'arbre soit au repos, et une compression déjà terminée à ce moment-là ne serait plus observable.
 * C'est le même problème que le `CompletableDeferred` de l'US-21, résolu ici en donnant à la vue
 * l'état exact qu'on veut lui voir rendre.
 *
 * Qualifiers Pixel 5 (`w411dp-h891dp`) : la feuille est ancrée en bas et haute d'environ 520 dp.
 * Sur l'appareil Robolectric par défaut (320 × 470 dp), elle défilerait, et les assertions
 * échoueraient pour une raison sans rapport avec ce qu'elles éprouvent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class)
class ExportModalRobolectricTest {

    /** Compression écourtée : ce niveau tourne en temps réel, la valeur de production est éprouvée en N2. */
    private val shortGeneration = 100L

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")

    private val invoices = listOf(
        Invoice(
            number = "FAC-2026-0401",
            issueDate = "2026-03-04",
            issuer = issuer,
            recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
            lines = listOf(InvoiceLine("Conseil", 2, Money(10_000), VatRate.TAUX_NORMAL)),
            status = InvoiceStatus.DEPOSITED,
            dueDate = "2026-04-03",
        ),
    )

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    private fun newViewModel(exporter: DocumentExporter = CapturingDocumentExporter()) =
        ExportViewModel(
            invoiceRepository = StubInvoiceRepository(invoices),
            documentExporter = exporter,
            clock = FixedClock("2026-09-02T08:00:00Z"),
            generationDuration = shortGeneration,
        )

    /** Modale reliée à un ViewModel réel — le câblage du shell, à l'échelle du test. */
    @Composable
    private fun Modal(viewModel: ExportViewModel, language: AppLanguage = AppLanguage.FR) {
        val state by viewModel.uiState.collectAsState()
        CompositionLocalProvider(LocalAppLanguage provides language) {
            ExportModalContent(
                uiState = state,
                onIntent = viewModel::processIntent,
                onDismiss = {},
            )
        }
    }

    /** Modale rendue depuis un état fixe — pour les deux étapes que le temps réel escamoterait. */
    @Composable
    private fun StaticModal(
        state: ExportUiState,
        language: AppLanguage = AppLanguage.FR,
        onIntent: (ExportIntent) -> Unit = {},
    ) {
        CompositionLocalProvider(LocalAppLanguage provides language) {
            ExportModalContent(uiState = state, onIntent = onIntent, onDismiss = {})
        }
    }

    // ── Structure ───────────────────────────────────────────────────────────

    @Test
    fun theModal_rendersItsDialogTitleAndSections() = runComposeUiTest {
        setContent { Modal(newViewModel()) }

        onNodeWithTag(ExportModalTags.DIALOG).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_MODAL_TITLE)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_MODAL_SUBTITLE)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_PERIOD_SECTION)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_FORMAT_SECTION)).assertIsDisplayed()
    }

    /** Les neuf tags du cahier des charges, tous présents dans un même rendu. */
    @Test
    fun theNineSpecifiedTags_areAllPresent() = runComposeUiTest {
        setContent { Modal(newViewModel()) }

        onNodeWithTag("export_modal_dialog").assertIsDisplayed()
        onNodeWithTag("export_date_from").assertIsDisplayed()
        onNodeWithTag("export_date_to").assertIsDisplayed()
        onNodeWithTag("export_format_fec").assertIsDisplayed()
        onNodeWithTag("export_format_facturx").assertIsDisplayed()
        onNodeWithTag("export_format_excel").assertIsDisplayed()
        onNodeWithTag("export_generate_btn").performScrollTo().assertIsDisplayed()

        // Les deux derniers appartiennent à des étapes ultérieures : au repos, ils n'existent pas.
        onNodeWithTag("export_progress_bar").assertDoesNotExist()
        onNodeWithTag("export_download_btn").assertDoesNotExist()
    }

    @Test
    fun theThreeFormats_showTheirTitleAndDescription() = runComposeUiTest {
        setContent { Modal(newViewModel()) }

        ExportFormat.entries.forEach { format ->
            onNodeWithText(tr(format.titleKey)).assertIsDisplayed()
            onNodeWithText(tr(format.descriptionKey)).assertIsDisplayed()
        }
    }

    @Test
    fun bothDateFields_carryTheOpeningPeriod() = runComposeUiTest {
        setContent { Modal(newViewModel()) }

        onNodeWithText(tr(StringKey.EXPORT_DATE_FROM_LABEL)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_DATE_TO_LABEL)).assertIsDisplayed()
        onNodeWithText("2026-01-01").assertIsDisplayed()
        onNodeWithText("2026-09-02").assertIsDisplayed()
    }

    // ── Sélection d'un format ───────────────────────────────────────────────

    @Test
    fun atOpening_theFecCardIsTheSelectedOne() = runComposeUiTest {
        setContent { Modal(newViewModel()) }

        onNodeWithTag(ExportModalTags.format(ExportFormat.FEC_OFFICIAL)).assertIsSelected()
        onNodeWithTag(ExportModalTags.format(ExportFormat.FACTURX_ARCHIVE)).assertIsNotSelected()
        onNodeWithTag(ExportModalTags.format(ExportFormat.EXCEL_SUMMARY)).assertIsNotSelected()
    }

    /** Le cœur du geste : toucher une carte déplace la sélection, elle ne s'ajoute pas. */
    @Test
    fun tappingACard_movesTheSelection() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { Modal(viewModel) }

        onNodeWithTag(ExportModalTags.format(ExportFormat.FACTURX_ARCHIVE)).performClick()
        waitForIdle()

        onNodeWithTag(ExportModalTags.format(ExportFormat.FACTURX_ARCHIVE)).assertIsSelected()
        onNodeWithTag(ExportModalTags.format(ExportFormat.FEC_OFFICIAL)).assertIsNotSelected()
        assertEquals(ExportFormat.FACTURX_ARCHIVE, viewModel.uiState.value.selectedFormat)
    }

    // ── Période ─────────────────────────────────────────────────────────────

    @Test
    fun editingABound_reachesTheViewModel() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { Modal(viewModel) }

        onNodeWithTag(ExportModalTags.DATE_TO).performTextReplacement("20261231")
        waitForIdle()

        assertEquals("2026-12-31", viewModel.uiState.value.period.to)
    }

    /** Le filtre réinsère les tirets : l'utilisateur ne tape que des chiffres. */
    @Test
    fun theDateField_reinsertsTheIsoDashes() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { Modal(viewModel) }

        onNodeWithTag(ExportModalTags.DATE_FROM).performTextReplacement("20260401")
        waitForIdle()

        assertEquals("2026-04-01", viewModel.uiState.value.period.from)
        onNodeWithText("2026-04-01").assertIsDisplayed()
    }

    @Test
    fun anInvertedPeriod_showsItsReason() = runComposeUiTest {
        setContent { Modal(newViewModel()) }

        onNodeWithTag(ExportModalTags.DATE_TO).performTextReplacement("20250101")
        waitForIdle()

        onNodeWithTag(ExportModalTags.PERIOD_ERROR).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_PERIOD_INVALID)).assertIsDisplayed()
    }

    // ── Compression et succès, rendus depuis un état fixe ───────────────────

    @Test
    fun whileGenerating_theProgressBarReplacesTheGenerateButton() = runComposeUiTest {
        setContent {
            StaticModal(
                ExportUiState(
                    period = ExportPeriod("2026-01-01", "2026-09-02"),
                    stage = ExportStage.GENERATING,
                    progress = 0.45f,
                ),
            )
        }

        // La feuille est plafonnée et défile désormais (voir `SheetMaxHeight`) : ce qui vit en
        // bas s'atteint comme sur l'appareil, en faisant défiler. Les assertions ne perdent rien à
        // ce détour — elles gagnent de porter sur le même geste que celui de l'utilisateur.
        onNodeWithTag(ExportModalTags.PROGRESS_BAR).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_GENERATING_LABEL)).assertIsDisplayed()
        onNodeWithText("45 %").assertIsDisplayed()
        onNodeWithTag(ExportModalTags.GENERATE_BTN).assertDoesNotExist()
        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).assertDoesNotExist()
    }

    @Test
    fun onceReady_theConfirmationAndDownloadButtonAppear() = runComposeUiTest {
        setContent { StaticModal(readyState()) }

        onNodeWithTag(ExportModalTags.SUCCESS).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_SUCCESS_TITLE)).assertIsDisplayed()
        onNodeWithText("3 " + tr(StringKey.EXPORT_DOCUMENT_COUNT_LABEL)).assertIsDisplayed()
        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_DOWNLOAD_ACTION)).assertIsDisplayed()
        onNodeWithTag(ExportModalTags.PROGRESS_BAR).assertDoesNotExist()
        onNodeWithTag(ExportModalTags.GENERATE_BTN).assertDoesNotExist()
    }

    @Test
    fun tappingDownload_emitsItsIntent() = runComposeUiTest {
        var downloads = 0
        setContent {
            StaticModal(
                state = readyState(),
                onIntent = { if (it is ExportIntent.DownloadRequested) downloads++ },
            )
        }

        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).performScrollTo().performClick()
        waitForIdle()

        assertEquals(1, downloads)
    }

    // ── Parcours complet, ViewModel réel ────────────────────────────────────

    /**
     * De la modale au fichier remis : c'est ce parcours-là que l'utilisateur suit, et le seul
     * moyen de constater que le bouton de téléchargement transmet bien l'archive produite.
     */
    @Test
    fun theFullFlow_endsOnTheArchiveHandedToThePlatform() = runComposeUiTest {
        val exporter = CapturingDocumentExporter()
        val viewModel = newViewModel(exporter)
        setContent { Modal(viewModel) }

        onNodeWithTag(ExportModalTags.GENERATE_BTN).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) {
            onAllNodesWithTag(ExportModalTags.DOWNLOAD_BTN).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).performScrollTo().performClick()
        waitUntil(timeoutMillis = 10_000) { exporter.callCount == 1 }

        val archive = assertNotNull(viewModel.uiState.value.archive)
        assertEquals(archive.fileName, exporter.fileName)
        assertEquals("text/plain", exporter.mimeType)
        assertEquals("820329331FEC20260902.txt", exporter.fileName)
    }

    /** Une période invalide n'entre jamais en compression, quel que soit le nombre de touchers. */
    @Test
    fun anInvalidPeriod_neverStartsTheCompression() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { Modal(viewModel) }

        onNodeWithTag(ExportModalTags.DATE_FROM).performTextReplacement("2026")
        waitForIdle()
        onNodeWithTag(ExportModalTags.GENERATE_BTN).performScrollTo().performClick()
        waitForIdle()

        assertEquals(ExportStage.IDLE, viewModel.uiState.value.stage)
        onNodeWithTag(ExportModalTags.PROGRESS_BAR).assertDoesNotExist()
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_theModalIsFullyTranslated() = runComposeUiTest {
        setContent { Modal(newViewModel(), language = AppLanguage.EN) }

        onNodeWithText(tr(StringKey.EXPORT_MODAL_TITLE, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_PERIOD_SECTION, AppLanguage.EN)).assertIsDisplayed()
        ExportFormat.entries.forEach { format ->
            onNodeWithText(tr(format.titleKey, AppLanguage.EN)).assertIsDisplayed()
        }
        onNodeWithText(tr(StringKey.EXPORT_GENERATE_ACTION, AppLanguage.EN)).assertIsDisplayed()
    }

    @Test
    fun inEnglish_theDownloadButtonKeepsItsZipWording() = runComposeUiTest {
        setContent { StaticModal(state = readyState(), language = AppLanguage.EN) }

        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).performScrollTo()
        onNodeWithText("Download the archive (.zip)").assertIsDisplayed()
    }

    // ── Déclencheur du shell ────────────────────────────────────────────────

    @Test
    fun theShellTrigger_isDisplayedAndClickable() = runComposeUiTest {
        var opened = 0
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                ExportModalTrigger(onClick = { opened++ })
            }
        }

        onNodeWithTag(ExportModalTags.TRIGGER).assertIsDisplayed()
        onNodeWithText(tr(StringKey.EXPORT_TRIGGER_LABEL)).assertIsDisplayed()
        onNodeWithTag(ExportModalTags.TRIGGER).performClick()

        assertEquals(1, opened)
    }

    private fun readyState() = ExportUiState(
        period = ExportPeriod("2026-01-01", "2026-09-02"),
        stage = ExportStage.READY,
        progress = 1f,
        archive = AccountingArchive(
            fileName = "820329331FEC20260902.txt",
            mimeType = "text/plain",
            content = "JournalCode",
            format = ExportFormat.FEC_OFFICIAL,
            documentCount = 3,
        ),
    )
}
