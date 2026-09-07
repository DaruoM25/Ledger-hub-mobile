package com.ledgerhub.presentation.quotes

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.data.invoice.MockInvoiceRepository
import com.ledgerhub.data.quote.MockQuoteRepository
import com.ledgerhub.domain.invoice.SubmitInvoiceUseCase
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Tests IHM Robolectric (Skill 2) de [QuotesView] : affichage des badges de statuts colorés
 * et cinématique du "bouton magique" de conversion d'un devis Accepté en facture.
 * Délais réseau mock réduits au minimum pour garder les tests rapides et déterministes.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class QuotesViewRobolectricTest {

    private fun viewModel() = QuotesViewModel(
        quoteRepository = MockQuoteRepository(simulatedDelayMillis = 10L),
        convertQuoteToInvoiceUseCase = ConvertQuoteToInvoiceUseCase(),
        submitInvoiceUseCase = SubmitInvoiceUseCase(MockInvoiceRepository(simulatedDelayMillis = 10L)),
    )

    @Test
    fun quotesList_displaysOneStatusBadgePerQuote() = runComposeUiTest {
        setContent { QuotesView(viewModel = viewModel()) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.rowTag("DEV-2026-001")).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-001")))
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-001")).assertIsDisplayed()
        onNodeWithTag(QuotesTags.statusBadgeTag("DEV-2026-001")).assertIsDisplayed()
        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-003")))
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-003")).assertIsDisplayed()
        onNodeWithTag(QuotesTags.statusBadgeTag("DEV-2026-003")).assertIsDisplayed()
    }

    @Test
    fun acceptedQuote_showsConvertButton_draftQuote_doesNot() = runComposeUiTest {
        setContent { QuotesView(viewModel = viewModel()) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-003")))
        onNodeWithTag(QuotesTags.convertButtonTag("DEV-2026-003")).assertIsDisplayed()

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-001")))
        onNodeWithTag(QuotesTags.convertButtonTag("DEV-2026-001")).assertDoesNotExist()
    }

    @Test
    fun clickingConvert_onAcceptedQuote_endsWithConvertedInvoiceLabel() = runComposeUiTest {
        setContent { QuotesView(viewModel = viewModel()) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-003")))
        onNodeWithTag(QuotesTags.convertButtonTag("DEV-2026-003")).performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.convertedInvoiceTag("DEV-2026-003")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(QuotesTags.convertedInvoiceTag("DEV-2026-003")).assertIsDisplayed()
    }

    @Test
    fun filterChip_filtersQuotesByStatus() = runComposeUiTest {
        setContent { QuotesView(viewModel = viewModel()) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        // Filtre par Brouillon
        onNodeWithTag(QuotesTags.filterChipTag(QuoteStatusFilter.DRAFT)).performClick()
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-001")).assertIsDisplayed()
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-002")).assertDoesNotExist()
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-003")).assertDoesNotExist()

        // Filtre par Accepté
        onNodeWithTag(QuotesTags.filterChipTag(QuoteStatusFilter.ACCEPTED)).performClick()
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-003")).assertIsDisplayed()
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-001")).assertDoesNotExist()

        // Revenir à Tous
        onNodeWithTag(QuotesTags.filterChipTag(QuoteStatusFilter.TOUS)).performClick()
        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-001")))
        onNodeWithTag(QuotesTags.rowTag("DEV-2026-001")).assertIsDisplayed()
    }

    @Test
    fun editButton_displayedOnlyForDraft() = runComposeUiTest {
        setContent { QuotesView(viewModel = viewModel()) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-001")))
        onNodeWithTag(QuotesTags.editButtonTag("DEV-2026-001")).assertIsDisplayed()

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-002")))
        onNodeWithTag(QuotesTags.editButtonTag("DEV-2026-002")).assertDoesNotExist()
    }

    @Test
    fun clickCreateButton_triggersCallback() = runComposeUiTest {
        var createClicked = false
        setContent {
            QuotesView(
                viewModel = viewModel(),
                onCreateQuote = { createClicked = true }
            )
        }
        onNodeWithTag(QuotesTags.CREATE_BUTTON).performClick()
        assert(createClicked)
    }

    @Test
    fun customConvertToInvoiceCallback_triggersWithQuote() = runComposeUiTest {
        var convertedQuoteNumber: String? = null
        setContent {
            QuotesView(
                viewModel = viewModel(),
                onConvertToInvoice = { quote -> convertedQuoteNumber = quote.number }
            )
        }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(QuotesTags.LIST).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(QuotesTags.LIST).performScrollToNode(hasTestTag(QuotesTags.rowTag("DEV-2026-003")))
        onNodeWithTag(QuotesTags.convertButtonTag("DEV-2026-003")).performClick()
        assert(convertedQuoteNumber == "DEV-2026-003")
    }
}
