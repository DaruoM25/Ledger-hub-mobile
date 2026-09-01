package com.ledgerhub.presentation.reconciliation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
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
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 3a (US-18) — interaction sémantique du lettrage sous Robolectric (JVM, CI sans émulateur).
 *
 * Les règles sont déjà verrouillées par `ReconciliationViewModelTest` : ce niveau ne les re-teste
 * pas, il vérifie que le **geste** produit l'écran attendu — sélectionner une carte de chaque côté
 * fait apparaître le bouton, et le toucher déclenche le lettrage.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists` : un nœud présent dans
 * l'arbre mais invisible ne prouve rien à l'utilisateur.
 *
 * La taille de l'écran simulé se déclare en **qualifiers Robolectric**, jamais en enveloppant la
 * composition dans un `Modifier.size` : celui-ci ramène la taille demandée dans les contraintes
 * reçues, donc aux 320 dp de l'appareil par défaut de Robolectric. L'écran serait alors mesuré en
 * compact et la colonne des factures n'existerait pas — à l'inverse de ce que ces tests éprouvent.
 * La classe simule une tablette par défaut ; le test compact redéclare son propre format.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1280dp-h900dp")
@OptIn(ExperimentalTestApi::class)
class ReconciliationScreenRobolectricTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** 240,00 € TTC. */
    private fun invoice(number: String = "FAC-2026-0301") = Invoice(
        number = number,
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = InvoiceStatus.DEPOSITED,
    )

    private fun transaction(id: String = "TX-2026-0091", cents: Long = 24_000) = BankTransaction(
        id = id,
        label = "VIR SEPA BOULANGERIE MOREAU",
        amount = Money(cents),
        valueDateIso = "2026-09-01",
    )

    private fun state(
        transactions: List<BankTransaction> = listOf(transaction()),
        invoices: List<Invoice> = listOf(invoice()),
        matches: List<ReconciliationMatch> = emptyList(),
        tab: ReconciliationTab = ReconciliationTab.TRANSACTIONS,
    ) = ReconciliationUiState(
        isLoading = false,
        transactions = transactions,
        invoices = invoices,
        matches = matches,
        compactTab = tab,
    )

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    // ── Structure ───────────────────────────────────────────────────────────

    @Test
    fun theScreen_showsItsTitleAndBothLists_onAnExpandedWidth() = runComposeUiTest {
        setContent { ReconciliationContent(state(), onIntent = {}) }

        onNodeWithTag(ReconciliationTags.SCREEN).assertIsDisplayed()
        onNodeWithText(tr(StringKey.RECONCILIATION_TITLE)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.RECONCILIATION_TRANSACTIONS_COLUMN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.RECONCILIATION_INVOICES_COLUMN)).assertIsDisplayed()
        onNodeWithTag(ReconciliationTags.TRANSACTIONS_LIST).assertIsDisplayed()
        onNodeWithTag(ReconciliationTags.INVOICES_LIST).assertIsDisplayed()
    }

    /** Sous le seuil, les deux faces deviennent des onglets — deux colonnes y seraient illisibles. */
    @Test
    @Config(qualifiers = "w400dp-h800dp")
    fun onACompactWidth_theTwoSidesBecomeTabs() = runComposeUiTest {
        setContent { ReconciliationContent(state(), onIntent = {}) }

        onNodeWithTag(ReconciliationTags.TAB_TRANSACTIONS).assertIsDisplayed()
        onNodeWithTag(ReconciliationTags.TAB_INVOICES).assertIsDisplayed()
        onNodeWithTag(ReconciliationTags.TRANSACTIONS_LIST).assertIsDisplayed()
    }

    @Test
    fun theCards_carryTheirIdentifiersAsTags() = runComposeUiTest {
        setContent { ReconciliationContent(state(), onIntent = {}) }

        onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091")).performScrollTo().assertIsDisplayed()
        onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301")).performScrollTo().assertIsDisplayed()
    }

    // ── Interaction : sélection croisée → bouton → lettrage ──────────────────

    /**
     * Le scénario central de l'US-18. L'état est porté par un `mutableStateOf` piloté par les
     * intentions : sans état observable, les clics ne recomposeraient rien et le test vérifierait
     * une interface figée.
     */
    @Test
    fun selectingBothSides_revealsTheButton_andTriggersTheMatch() = runComposeUiTest {
        var uiState by mutableStateOf(state())
        var matchRequested = false

        setContent {
            ReconciliationContent(
                uiState = uiState,
                onIntent = { intent ->
                    uiState = when (intent) {
                        is ReconciliationIntent.SelectTransaction ->
                            uiState.copy(selectedTransactionId = intent.transactionId)

                        is ReconciliationIntent.SelectInvoice ->
                            uiState.copy(selectedInvoiceNumber = intent.invoiceNumber)

                        ReconciliationIntent.PerformMatch -> {
                            matchRequested = true
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
            )
        }

        // Rien n'est sélectionné : le bouton n'existe pas encore.
        onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).assertDoesNotExist()

        onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091")).performScrollTo().performClick()
        onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON)
            .assertDoesNotExist()

        onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301")).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).assertIsDisplayed()
        onNodeWithText(tr(StringKey.RECONCILIATION_ACTION_MATCH)).assertIsDisplayed()

        onNodeWithTag(ReconciliationTags.RECONCILE_BUTTON).performClick()
        waitForIdle()

        assertEquals(true, matchRequested, "Le clic doit émettre l'intention de lettrage")
        onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .assertTextContains(tr(StringKey.RECONCILIATION_BADGE_RECONCILED), substring = true)
    }

    // ── Badges ──────────────────────────────────────────────────────────────

    @Test
    fun anAlreadyMatchedTransaction_showsTheReconciledBadge() = runComposeUiTest {
        val matched = ReconciliationMatch("TX-2026-0091", "FAC-2026-0301", "2026-09-01T10:15:00Z", 0L)
        setContent { ReconciliationContent(state(matches = listOf(matched)), onIntent = {}) }

        onNodeWithTag(ReconciliationTags.transactionCard("TX-2026-0091"))
            .performScrollTo()
            .assertTextContains(tr(StringKey.RECONCILIATION_BADGE_RECONCILED), substring = true)
    }

    /** L'écart ne s'affiche que sur le couple en cours : hors sélection, il n'a pas de terme. */
    @Test
    fun aMismatchedPair_showsTheAmountMismatchBadge() = runComposeUiTest {
        setContent {
            ReconciliationContent(
                uiState = state(transactions = listOf(transaction(cents = 23_850))).copy(
                    selectedTransactionId = "TX-2026-0091",
                    selectedInvoiceNumber = "FAC-2026-0301",
                ),
                onIntent = {},
            )
        }

        onNodeWithTag(ReconciliationTags.invoiceCard("FAC-2026-0301"))
            .performScrollTo()
            .assertTextContains(tr(StringKey.RECONCILIATION_BADGE_AMOUNT_MISMATCH), substring = true)
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_theScreenIsFullyTranslated() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                ReconciliationContent(state(), onIntent = {})
            }
        }

        onNodeWithText(tr(StringKey.RECONCILIATION_TITLE, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.RECONCILIATION_TRANSACTIONS_COLUMN, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.RECONCILIATION_INVOICES_COLUMN, AppLanguage.EN)).assertIsDisplayed()
    }
}
