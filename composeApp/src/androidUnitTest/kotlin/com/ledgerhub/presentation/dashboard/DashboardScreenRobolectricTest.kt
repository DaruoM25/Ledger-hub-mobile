package com.ledgerhub.presentation.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeUp
import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.dashboard.GetDashboardAnalyticsUseCase
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteRepository
import com.ledgerhub.domain.time.FixedClock
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Tests IHM Robolectric (Skill 2) de [DashboardScreen] : les 3 cartes KPI et le graphique Canvas
 * doivent s'afficher sans crash une fois les données chargées — voir QuotesViewRobolectricTest
 * pour le même principe (délais réseau mock réduits, attente active via waitUntil).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class)
class DashboardScreenRobolectricTest {

    /**
     * Horloge figée : la section « Devis à relancer » se mesure en jours avant échéance, elle ne
     * peut pas dépendre de la date d'exécution de la suite. Le jeu de `MockQuoteRepository` fixe
     * les validités au 2026-09-01, soit J+2 ici.
     */
    private val clock = FixedClock("2026-08-30T09:00:00Z")

    private fun viewModel() = DashboardViewModel(
        invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = 10L),
        creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 10L),
        quoteRepository = MockQuoteRepository(simulatedDelayMillis = 10L),
        clock = clock,
    )

    /** ViewModel sans aucun devis : la section de relance doit alors afficher son état vide. */
    private fun viewModelWithoutQuotes() = DashboardViewModel(
        getDashboardAnalyticsUseCase = GetDashboardAnalyticsUseCase(
            invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = 10L),
            creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 10L),
            quoteRepository = EmptyQuoteRepository,
            clock = clock,
        ),
    )

    private object EmptyQuoteRepository : QuoteRepository {
        override suspend fun submitQuote(quote: Quote): Result<Unit> = Result.success(Unit)
        override suspend fun fetchQuotes(): Result<List<Quote>> = Result.success(emptyList())
    }

    @Test
    fun dashboard_rendersFourKpiCards_andRevenueChart_onceLoaded() = runComposeUiTest {
        setContent { DashboardScreen(viewModel = viewModel()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.COLLECTED_CARD).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.KPI_GRID).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.COLLECTED_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.PENDING_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.ISSUED_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.KPI_QUOTES_PENDING).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.REVENUE_CHART).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dashboard_rendersRecentActivityList_withThreeDocuments() = runComposeUiTest {
        setContent { DashboardScreen(viewModel = viewModel()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.RECENT_ACTIVITY_LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.RECENT_ACTIVITY_LIST).performScrollTo().assertIsDisplayed()
        // Vérification que les 3 factures les plus récentes sont présentes dans le tableau
        onNodeWithTag(DashboardTags.recentDocumentTag("F-2026-110")).assertExists()
        onNodeWithTag(DashboardTags.recentDocumentStatusBadgeTag("F-2026-110")).assertExists()
        onNodeWithTag(DashboardTags.recentDocumentTag("F-2026-109")).assertExists()
        onNodeWithTag(DashboardTags.recentDocumentStatusBadgeTag("F-2026-109")).assertExists()
        onNodeWithTag(DashboardTags.recentDocumentTag("F-2026-108")).assertExists()
        onNodeWithTag(DashboardTags.recentDocumentStatusBadgeTag("F-2026-108")).assertExists()
    }

    // ── Devis à relancer (US-12) ────────────────────────────────────────────

    @Test
    fun dashboard_rendersTheQuotesToFollowUpSection_withTheSentQuote() = runComposeUiTest {
        setContent { DashboardScreen(viewModel = viewModel()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_SECTION).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_LIST).performScrollTo().assertIsDisplayed()
        // MockQuoteRepository ne sème qu'un devis Envoyé, échéant au 2026-09-01.
        onNodeWithTag(DashboardTags.quoteFollowUpTag("DEV-2026-002")).assertExists()
        onNodeWithTag(DashboardTags.quoteFollowUpDeadlineTag("DEV-2026-002")).assertExists()
    }

    @Test
    fun dashboard_showsTheEmptyState_whenNoQuoteNeedsFollowUp() = runComposeUiTest {
        setContent { DashboardScreen(viewModel = viewModelWithoutQuotes()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_EMPTY).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_SECTION).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_EMPTY).performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_LIST).assertCountEquals(0)
    }

    @Test
    fun dashboard_showsEmptyState_withZeroKpisAndEmptySections_whenAccountHasNoDocuments() = runComposeUiTest {
        val emptyViewModel = DashboardViewModel(
            getDashboardAnalyticsUseCase = GetDashboardAnalyticsUseCase(
                invoiceRepository = object : com.ledgerhub.domain.invoice.InvoiceRepository {
                    override suspend fun submitInvoice(invoice: com.ledgerhub.domain.invoice.Invoice) = Result.success(Unit)
                    override suspend fun fetchInvoices(): Result<List<com.ledgerhub.domain.invoice.Invoice>> = Result.success(emptyList())
                },
                creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 0L),
                quoteRepository = EmptyQuoteRepository,
                clock = clock,
            ),
        )

        setContent { DashboardScreen(viewModel = emptyViewModel) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.RECENT_ACTIVITY_EMPTY).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.KPI_GRID).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.REVENUE_CHART).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.RECENT_ACTIVITY_EMPTY).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_EMPTY).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dashboardHeader_displaysTitleAndCreateInvoicePillButton() = runComposeUiTest {
        var createClicked = false
        setContent {
            DashboardScreen(
                viewModel = viewModel(),
                onCreateInvoice = { createClicked = true },
            )
        }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.CREATE_INVOICE_BUTTON).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.CREATE_INVOICE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun dashboard_rendersPopulatedActivityTables_withInvoiceAndQuoteDetails() = runComposeUiTest {
        val specificInvoice = com.ledgerhub.domain.invoice.Invoice(
            number = "FAC-2026-001",
            issueDate = "2026-08-20",
            dueDate = "2026-09-20",
            issuer = com.ledgerhub.domain.invoice.Party("Cabinet", "123456789", "12345678900012"),
            recipient = com.ledgerhub.domain.invoice.Party("Client Alpha", "987654321", "98765432100045"),
            lines = listOf(
                com.ledgerhub.domain.invoice.InvoiceLine(
                    label = "Prestation",
                    quantity = 1,
                    unitPriceHt = com.ledgerhub.domain.invoice.Money(10000),
                    vatRate = com.ledgerhub.domain.invoice.VatRate.TAUX_NORMAL,
                )
            ),
            status = com.ledgerhub.domain.invoice.InvoiceStatus.DEPOSITED,
        )

        val specificQuote = com.ledgerhub.domain.quote.Quote(
            number = "DEV-2026-001",
            issueDate = "2026-08-15",
            validityDate = "2026-09-02",
            issuer = com.ledgerhub.domain.invoice.Party("Cabinet", "123456789", "12345678900012"),
            recipient = com.ledgerhub.domain.invoice.Party("Client Beta", "987654321", "98765432100045"),
            lines = listOf(
                com.ledgerhub.domain.quote.QuoteLine(
                    label = "Mission",
                    quantity = 1,
                    unitPriceHt = com.ledgerhub.domain.invoice.Money(20000),
                    vatRate = com.ledgerhub.domain.invoice.VatRate.TAUX_NORMAL,
                )
            ),
            status = com.ledgerhub.domain.quote.QuoteStatus.SENT,
        )

        val customViewModel = DashboardViewModel(
            getDashboardAnalyticsUseCase = GetDashboardAnalyticsUseCase(
                invoiceRepository = object : com.ledgerhub.domain.invoice.InvoiceRepository {
                    override suspend fun submitInvoice(invoice: com.ledgerhub.domain.invoice.Invoice) = Result.success(Unit)
                    override suspend fun fetchInvoices(): Result<List<com.ledgerhub.domain.invoice.Invoice>> = Result.success(listOf(specificInvoice))
                },
                creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 0L),
                quoteRepository = object : com.ledgerhub.domain.quote.QuoteRepository {
                    override suspend fun submitQuote(quote: com.ledgerhub.domain.quote.Quote) = Result.success(Unit)
                    override suspend fun fetchQuotes(): Result<List<com.ledgerhub.domain.quote.Quote>> = Result.success(listOf(specificQuote))
                },
                clock = clock,
            ),
        )

        setContent { DashboardScreen(viewModel = customViewModel) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.RECENT_ACTIVITY_LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.RECENT_ACTIVITY_LIST).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.recentDocumentTag("FAC-2026-001")).assertExists()
        onNodeWithTag(DashboardTags.recentDocumentStatusBadgeTag("FAC-2026-001")).assertExists()

        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_SECTION).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.QUOTES_TO_FOLLOWUP_LIST).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.quoteFollowUpTag("DEV-2026-001")).assertExists()
        onNodeWithTag(DashboardTags.quoteFollowUpDeadlineTag("DEV-2026-001")).assertExists()
    }
}

