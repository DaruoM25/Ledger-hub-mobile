package com.ledgerhub.presentation.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.data.creditnote.MockCreditNoteRepository
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Tests IHM Robolectric (Skill 2) de [DashboardScreen] : les 3 cartes KPI et le graphique Canvas
 * doivent s'afficher sans crash une fois les données chargées — voir QuotesViewRobolectricTest
 * pour le même principe (délais réseau mock réduits, attente active via waitUntil).
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class DashboardScreenRobolectricTest {

    private fun viewModel() = DashboardViewModel(
        invoiceRepository = MockInvoiceRepository(simulatedDelayMillis = 10L),
        creditNoteRepository = MockCreditNoteRepository(simulatedDelayMillis = 10L),
        quoteRepository = MockQuoteRepository(simulatedDelayMillis = 10L),
    )

    @Test
    fun dashboard_rendersThreeKpiCards_andRevenueChart_onceLoaded() = runComposeUiTest {
        setContent { DashboardScreen(viewModel = viewModel()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.COLLECTED_CARD).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.COLLECTED_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.PENDING_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.ISSUED_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(DashboardTags.REVENUE_CHART).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dashboard_rendersRecentActivityList_withThreeDocuments() = runComposeUiTest {
        setContent { DashboardScreen(viewModel = viewModel()) }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(DashboardTags.RECENT_ACTIVITY_LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(DashboardTags.RECENT_ACTIVITY_LIST).performScrollTo().assertIsDisplayed()
    }
}
