package com.ledgerhub.presentation.reconciliation

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.ReconciliationMatch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-18) — lettrage au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que les cartes offrent une cible tactile décente, que le
 * bouton étendu se laisse réellement toucher une fois apparu, et que les deux agencements se
 * comportent comme prévu de part et d'autre du seuil de 840 dp. S'y ajoute l'export de la capture
 * servant de preuve QA.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ReconciliationScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US18_mobile_bank_reconciliation_sdk_gphone64_x86_64.png"

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** 240,00 € TTC. */
    private fun invoice(number: String = "FAC-2026-0301") = Invoice(
        number = number,
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire PPF", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
    )

    private fun transaction(id: String = "TX-2026-0091", cents: Long = 24_000) = BankTransaction(
        id = id,
        label = "VIR SEPA BOULANGERIE MOREAU",
        amount = Money(cents),
        valueDateIso = "2026-09-01",
        counterparty = "Boulangerie Moreau SARL",
    )

    private fun state(matches: List<ReconciliationMatch> = emptyList()) = ReconciliationUiState(
        isLoading = false,
        transactions = listOf(transaction(), transaction("TX-2026-0092", 11_850)),
        invoices = listOf(invoice(), invoice("FAC-2026-0302")),
        matches = matches,
    )

    /** Écran piloté par un état observable : sans lui, aucun clic ne recomposerait quoi que ce soit. */
    private fun renderScreen(width: Dp = 1_000.dp, onMatch: () -> Unit = {}) {
        composeRule.setContent {
            var uiState by remember { mutableStateOf(state()) }
            Viewport(width, 800.dp) {
                ReconciliationContent(
                    uiState = uiState,
                    onIntent = { intent ->
                        uiState = when (intent) {
                            is ReconciliationIntent.SelectTransaction ->
                                uiState.copy(selectedTransactionId = intent.transactionId)

                            is ReconciliationIntent.SelectInvoice ->
                                uiState.copy(selectedInvoiceNumber = intent.invoiceNumber)

                            ReconciliationIntent.PerformMatch -> {
                                onMatch()
                                uiState.copy(
                                    matches = listOf(
                                        ReconciliationMatch(
                                            transactionId = "TX-2026-0091",
                                            invoiceNumber = "FAC-2026-0301",
                                            matchedAtIso = "2026-09-01T10:15:00Z",
                                            deltaCents = 0L,
                                        ),
                                    ),
                                    selectedTransactionId = null,
                                    selectedInvoiceNumber = null,
                                )
                            }

                            else -> uiState
                        }
                    },
                    onSelectTab = { tab -> uiState = uiState.copy(compactTab = tab) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    // ── Geste tactile ───────────────────────────────────────────────────────

    @Test
    fun tappingBothSides_revealsTheButton_andPerformsTheMatchOnDevice() {
        var matched = false
        renderScreen(onMatch = { matched = true })

        composeRule.onAllNodesWithTag(ReconciliationTags.RECONCILE_BUTTON)
            .fetchSemanticsNodes().let { assertTrue(it.isEmpty(), "Le bouton ne doit pas précéder la sélection") }

        composeRule.onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301"))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).performTouchInput { click() }

        // L'apparition du badge passe par une recomposition : on attend le résultat plutôt que
        // de le supposer acquis à la milliseconde du clic.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ReconciliationTags.RECONCILE_BUTTON)
                .fetchSemanticsNodes().isEmpty()
        }
        assertTrue(matched, "Le toucher du bouton doit déclencher le lettrage")
        composeRule.onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .assertTextContains(
                AppTranslations.get(StringKey.RECONCILIATION_BADGE_RECONCILED, AppLanguage.FR),
                substring = true,
            )
    }

    /** Accessibilité tactile : c'est la carte entière qui porte la cible, pas son seul titre. */
    @Test
    fun everyCard_meetsTheMinimumTouchTargetHeight() {
        renderScreen()

        composeRule.onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301"))
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    // ── Agencement responsive ───────────────────────────────────────────────

    @Test
    fun onAnExpandedWidth_bothListsSitSideBySide_withoutScrolling() {
        renderScreen(width = 1_000.dp)

        composeRule.onNodeWithTag(ReconciliationTags.TRANSACTIONS_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(ReconciliationTags.INVOICES_LIST).assertIsDisplayed()
    }

    /**
     * En compact, la sélection de l'onglet masqué doit survivre à la bascule : c'est ce qui permet
     * d'associer deux éléments qu'on ne peut pas voir en même temps.
     */
    @Test
    fun onACompactWidth_theSelectionSurvivesATabSwitch() {
        renderScreen(width = 400.dp)

        composeRule.onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.onNodeWithTag(ReconciliationTags.TAB_INVOICES).performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReconciliationTags.INVOICES_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301"))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        // Le bouton n'apparait que si la selection de l'onglet masque a bien ete conservee.
        composeRule.onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).assertIsDisplayed()
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-18 : les deux listes, une sélection croisée en cours et le bouton
     * de lettrage apparu — exactement ce que l'écran doit démontrer.
     *
     * Export sous `US18_mobile_bank_reconciliation_sdk_gphone64_x86_64.png`, à rapatrier dans
     * `screenshots/` aux côtés des captures US-10 à US-17.
     */
    @Test
    fun exportsTheReconciliationScreenshot() {
        renderScreen()

        composeRule.onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301"))
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).assertIsDisplayed()

        val bitmap = composeRule.onNodeWithTag(ReconciliationTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), screenshotName)
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-18 non écrite : ${output.absolutePath}")
        assertTrue(output.length() > 0, "Capture US-18 vide : ${output.absolutePath}")
    }
}

/**
 * Fenêtre de test d'une largeur donnée, **en dp réellement disponibles**.
 *
 * `Modifier.size` ramène la taille demandée dans les contraintes reçues, donc aux 393 dp du
 * Pixel 5 : un `Box(Modifier.size(1000.dp, ...))` y mesurerait 393 dp et l'écran basculerait sur
 * l'agencement en onglets, à l'inverse de ce que le test prétend éprouver. `requiredSize` ne
 * réglerait rien — la seconde colonne sortirait de l'écran physique et resterait invisible. La
 * densité est donc ramenée à 1 : la même dalle de 1080 px expose 1080 dp, de quoi loger 1000 dp
 * sans rien rogner (helper introduit en US-17).
 */
@Composable
private fun Viewport(width: Dp, height: Dp, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
        Box(modifier = Modifier.size(width = width, height = height)) { content() }
    }
}
