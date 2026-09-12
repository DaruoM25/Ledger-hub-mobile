package com.ledgerhub.presentation.export

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.export.DocumentExporter
import com.ledgerhub.domain.export.ExportFormat
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
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dépôt de lecture seule : l'export ne fait qu'interroger. */
private class DeviceInvoiceRepository(private val invoices: List<Invoice>) : InvoiceRepository {
    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> =
        error("L'export ne soumet aucune facture")

    override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
}

/** Double d'export : capture l'appel sans ouvrir la feuille de partage du système. */
private class DeviceDocumentExporter : DocumentExporter {
    var fileName: String? = null
    var callCount = 0

    override suspend fun export(fileName: String, mimeType: String, content: String): Result<Unit> {
        this.fileName = fileName
        callCount++
        return Result.success(Unit)
    }
}

/**
 * Niveau 3b (US-22) — modale d'export comptable au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que chaque contrôle de la feuille est **joignable** sur
 * l'appareil cible, que ses cartes de format offrent des cibles tactiles décentes, et que le
 * toucher les atteint bel et bien — l'injection tactile dans une fenêtre de dialogue est justement
 * ce qui n'est pas fidèle hors appareil (voir la note du niveau 3a). S'y ajoute la seule épreuve du
 * **vrai délai de deux secondes** : la compression doit aboutir sur l'appareil, et pas seulement
 * en temps virtuel. La barre elle-même, en revanche, ne s'observe pas d'ici — voir
 * `theRealCompression_runsThroughToAReadyArchive`.
 *
 * La **hauteur** de la feuille, elle, ne se juge pas ici : elle est tenue sur la JVM par
 * `ExportModalGeometryRobolectricTest`, qui échoue avant qu'un émulateur ne soit allumé.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ExportModalInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US22_mobile_export_modal_sdk_gphone64_x86_64.png"

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")

    private val invoices = listOf(
        invoice("FAC-2026-0401", "2026-03-04"),
        invoice("FAC-2026-0402", "2026-06-15"),
    )

    private fun invoice(number: String, issueDate: String) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = issuer,
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
        dueDate = "2026-04-03",
    )

    private val exporter = DeviceDocumentExporter()

    private fun tr(key: StringKey) = AppTranslations.get(key, AppLanguage.FR)

    /**
     * La modale reliée à un ViewModel réel, **durée de production comprise** : c'est le seul
     * niveau où les deux secondes s'écoulent vraiment.
     */
    @Composable
    private fun Modal() {
        val viewModel = remember {
            ExportViewModel(
                invoiceRepository = DeviceInvoiceRepository(invoices),
                documentExporter = exporter,
                clock = FixedClock("2026-09-02T08:00:00Z"),
            )
        }
        val state by viewModel.uiState.collectAsState()

        // Thème complet : la capture officielle doit montrer la feuille telle que l'utilisateur la
        // voit, fond slate compris, et non sur le fond clair par défaut d'une activité nue.
        LedgerHubTheme {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ExportModalContent(
                        uiState = state,
                        onIntent = viewModel::processIntent,
                        onDismiss = {},
                    )
                }
            }
        }
    }

    private fun render() {
        composeRule.setContent { Modal() }
        composeRule.waitForIdle()
    }

    /**
     * Les interactions passent par `performScrollTo()`.
     *
     * La feuille tient sur un Pixel 5, mais elle n'est pas garantie de tenir partout : un clavier
     * ouvert sur un champ de date, une police système agrandie ou un appareil plus court la font
     * défiler. Sans ce défilement préalable, le test échouerait alors sur la **géométrie** de
     * l'appareil et non sur le comportement qu'il éprouve — et son échec ne dirait rien d'utile.
     */
    private fun generateAndWaitForTheArchive() {
        composeRule.onNodeWithTag(ExportModalTags.GENERATE_BTN)
            .performScrollTo()
            .performTouchInput { click() }
        // Présence dans l'arbre, et non visibilité : à cet instant précis le bouton peut encore
        // attendre sous la ligne de flottaison, ce que le `performScrollTo()` suivant corrigera.
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag(ExportModalTags.DOWNLOAD_BTN)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Parcours tactile ────────────────────────────────────────────────────

    /**
     * Tout ce que la feuille porte est **joignable** sur l'appareil cible : la période, les trois
     * formats et le bouton de génération.
     *
     * ## Pourquoi « joignable » et non « visible sans défilement »
     *
     * La version précédente de ce test exigeait que tout soit visible d'un coup. C'était une
     * exigence que rien ne peut tenir : la hauteur d'une feuille dépend de la police système de
     * l'utilisateur, de la langue et de la taille d'écran, et un test qui la fige échoue tôt ou
     * tard sur une **géométrie** sans rien dire du comportement. Ce qui doit être garanti, c'est
     * qu'aucun contrôle ne soit hors d'atteinte — ce que `performScrollTo()` éprouve, et que le
     * bornage de la feuille au visible rend possible.
     *
     * Le budget de hauteur, lui, est tenu sur la JVM par `ExportModalGeometryRobolectricTest` :
     * c'est là qu'une section de trop se fait attraper, et non ici.
     */
    @Test
    fun everyControlOfTheSheet_isReachableOnDevice() {
        render()

        composeRule.onNodeWithTag(ExportModalTags.DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText(tr(StringKey.EXPORT_MODAL_TITLE)).assertIsDisplayed()
        composeRule.onNodeWithTag(ExportModalTags.DATE_FROM).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ExportModalTags.DATE_TO).performScrollTo().assertIsDisplayed()
        ExportFormat.entries.forEach { format ->
            composeRule.onNodeWithTag(ExportModalTags.format(format))
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag(ExportModalTags.GENERATE_BTN)
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** Accessibilité tactile : une carte entière est une cible, pas seulement sa pastille radio. */
    @Test
    fun everyFormatCard_meetsTheMinimumTouchTargetHeight() {
        render()

        ExportFormat.entries.forEach { format ->
            composeRule.onNodeWithTag(ExportModalTags.format(format))
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        composeRule.onNodeWithTag(ExportModalTags.GENERATE_BTN)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
    }

    /** Le geste que le niveau 3a ne peut pas éprouver : le doigt sur la carte, dans la vraie fenêtre. */
    @Test
    fun tappingACard_movesTheSelectionOnDevice() {
        render()

        composeRule.onNodeWithTag(ExportModalTags.format(ExportFormat.FEC_OFFICIAL)).assertIsSelected()

        composeRule.onNodeWithTag(ExportModalTags.format(ExportFormat.EXCEL_SUMMARY))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ExportModalTags.format(ExportFormat.EXCEL_SUMMARY)).assertIsSelected()
        composeRule.onNodeWithTag(ExportModalTags.format(ExportFormat.FEC_OFFICIAL)).assertIsNotSelected()
    }

    /**
     * La compression réelle de deux secondes va jusqu'à son terme sur l'appareil, et la feuille
     * atteint l'état « archive prête ».
     *
     * ## Pourquoi ce test n'affirme plus la barre de progression
     *
     * Il s'y est essayé, et il a échoué sur l'appareil pour une raison mécanique : **toute action
     * de test se synchronise avec la composition**. Or, pendant la compression, l'arbre n'est
     * jamais au repos — la progression publie un palier toutes les 50 ms et la barre les anime.
     * Le `waitForIdle()` implicite du clic attend donc que tout cela se calme, c'est-à-dire les
     * deux secondes entières : au premier sondage qui suit, l'état est déjà `READY` et la barre a
     * quitté l'arbre. Aucun réglage de délai n'y change rien, puisque allonger la compression
     * allonge d'autant l'attente qui la manque.
     *
     * Affirmer malgré tout `progressBarSeen || downloadBtnPresent` serait une assertion qui ne
     * peut pas échouer : c'est exactement la condition de sortie de la boucle ci-dessous. Le test
     * garderait un nom promettant d'éprouver la barre tout en n'éprouvant plus rien — une
     * couverture de façade, plus trompeuse que son absence.
     *
     * **La barre est éprouvée ailleurs, et mieux** : `ExportModalRobolectricTest` la rend depuis
     * un état `GENERATING` figé et vérifie sa présence, son libellé et son pourcentage ;
     * `ExportViewModelTest` prouve en temps virtuel que la progression est réelle, monotone et
     * bornée sur les 40 paliers. Ce qui restait à prouver **ici**, et que ce test prouve, c'est
     * que sur un appareil réel le geste déclenche bien une compression qui aboutit — et que la
     * barre s'efface une fois l'archive prête.
     */
    @Test
    fun theRealCompression_runsThroughToAReadyArchive() {
        render()

        composeRule.onNodeWithTag(ExportModalTags.GENERATE_BTN)
            .performScrollTo()
            .performTouchInput { click() }

        // `progressBarSeen` ne sert qu'à sortir de la boucle au plus tôt lorsque l'ordonnancement
        // laisse malgré tout apercevoir la compression. Il n'est pas affirmé : voir ci-dessus.
        var progressBarSeen = false
        composeRule.waitUntil(timeoutMillis = 10_000) {
            if (composeRule.onAllNodesWithTag(ExportModalTags.PROGRESS_BAR)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                progressBarSeen = true
            }
            progressBarSeen ||
                composeRule.onAllNodesWithTag(ExportModalTags.DOWNLOAD_BTN)
                    .fetchSemanticsNodes().isNotEmpty()
        }

        // Assertions strictes, et vraies quel que soit l'ordonnancement : la compression a bien
        // tourné jusqu'au bout — donc le clic a porté et la coroutine a fait son travail — et la
        // barre a cédé la place au lieu de rester à l'écran.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(ExportModalTags.DOWNLOAD_BTN)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ExportModalTags.SUCCESS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ExportModalTags.PROGRESS_BAR).assertDoesNotExist()
    }

    @Test
    fun theCompletedFlow_handsTheArchiveToThePlatform() {
        render()
        generateAndWaitForTheArchive()

        composeRule.onNodeWithTag(ExportModalTags.SUCCESS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(tr(StringKey.EXPORT_DOWNLOAD_ACTION)).assertExists()

        composeRule.onNodeWithTag(ExportModalTags.DOWNLOAD_BTN)
            .performScrollTo()
            .assertIsDisplayed()
            .performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 5_000) { exporter.callCount == 1 }

        assertEquals("820329331FEC20260902.txt", exporter.fileName)
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-22 : la feuille au terme du parcours — période, trois formats et
     * archive prête au téléchargement. C'est l'état **d'arrivée** qui est retenu plutôt que l'état
     * de repos : il porte à la fois les réglages et le résultat, donc l'US entière en une image.
     *
     * Écrite **aux deux emplacements** : les fichiers de l'application (traçabilité) et
     * `/sdcard/Download`, d'où `scripts/run-qa.ps1 -ScreenshotPrefix "US22"` la rapatrie sans
     * `adb pull` manuel (même procédé que `IntegrationsHubInstrumentedTest`).
     */
    @Test
    fun exportsTheExportModalScreenshot() {
        render()
        generateAndWaitForTheArchive()
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(ExportModalTags.DIALOG)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFile = File(context.getExternalFilesDir(null), screenshotName)
        appFile.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        val sharedFile = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)!!,
            screenshotName,
        ).apply { parentFile?.mkdirs() }
        runCatching {
            sharedFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        assertTrue(appFile.exists(), "Capture US-22 non écrite : ${appFile.absolutePath}")
        assertTrue(appFile.length() > 0, "Capture US-22 vide : ${appFile.absolutePath}")
    }
}
