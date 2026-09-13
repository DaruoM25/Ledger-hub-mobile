package com.ledgerhub.presentation.support

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.support.CreateSupportTicketUseCase
import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureFeedbackRepository
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.FeatureStatus
import com.ledgerhub.domain.support.GetFeatureRequestsUseCase
import com.ledgerhub.domain.support.SubmitFeatureRequestUseCase
import com.ledgerhub.domain.support.SupportRepository
import com.ledgerhub.domain.support.SupportTicket
import com.ledgerhub.domain.support.TicketStatus
import com.ledgerhub.domain.support.VoteFeatureRequestUseCase
import com.ledgerhub.presentation.theme.LedgerHubTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class SupportFeedbackRobolectricTest {

    private class FakeSupportRepository : SupportRepository {
        val tickets = mutableListOf<SupportTicket>()
        override suspend fun getAllTickets(): Result<List<SupportTicket>> = Result.success(tickets.toList())
        override suspend fun getTicketsByUser(userId: String): Result<List<SupportTicket>> = Result.success(tickets.toList())
        override suspend fun getTicketById(id: String): Result<SupportTicket?> = Result.success(tickets.firstOrNull { it.id == id })
        override suspend fun createTicket(ticket: SupportTicket): Result<Unit> {
            tickets.add(ticket)
            return Result.success(Unit)
        }
        override suspend fun updateTicketStatus(id: String, status: TicketStatus, updatedAt: String): Result<Unit> = Result.success(Unit)
        override suspend fun countOpenTicketsByUser(userId: String): Result<Long> = Result.success(tickets.size.toLong())
    }

    private class FakeFeatureFeedbackRepository : FeatureFeedbackRepository {
        val requests = mutableListOf<FeatureRequest>()
        val votes = mutableSetOf<Pair<String, String>>()

        override suspend fun getFeatureRequests(currentUserId: String): Result<List<FeatureRequest>> =
            Result.success(requests.map { it.copy(hasVoted = votes.contains(currentUserId to it.id)) }.sortedByDescending { it.voteCount })

        override suspend fun getFeatureRequestById(id: String, currentUserId: String): Result<FeatureRequest?> =
            Result.success(requests.firstOrNull { it.id == id }?.copy(hasVoted = votes.contains(currentUserId to id)))

        override suspend fun submitFeatureRequest(featureRequest: FeatureRequest): Result<Unit> {
            requests.add(featureRequest)
            return Result.success(Unit)
        }

        override suspend fun hasUserVoted(userId: String, featureRequestId: String): Result<Boolean> =
            Result.success(votes.contains(userId to featureRequestId))

        override suspend fun voteFeatureRequest(userId: String, featureRequestId: String, votedAt: String): Result<Unit> {
            votes.add(userId to featureRequestId)
            val idx = requests.indexOfFirst { it.id == featureRequestId }
            if (idx >= 0) {
                val current = requests[idx]
                requests[idx] = current.copy(voteCount = current.voteCount + 1)
            }
            return Result.success(Unit)
        }

        override suspend fun cancelVote(userId: String, featureRequestId: String): Result<Unit> {
            votes.remove(userId to featureRequestId)
            return Result.success(Unit)
        }
    }

    @Test
    fun renderScreen_displaysTabsAndRegulatoryTabByDefault() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()
        val viewModel = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            dispatcher = testDispatcher,
        )

        setContent {
            LedgerHubTheme {
                SupportFeedbackScreen(viewModel = viewModel)
            }
        }

        onNodeWithTag(SupportFeedbackTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.TAB_ROW).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.TAB_REGULATORY).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.TAB_IDEAS).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.INPUT_SUBJECT).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.INPUT_DESCRIPTION).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.BTN_SUBMIT_TICKET).assertExists()
    }

    @Test
    fun switchTab_andVoteIdea_optimisticUpdateDisplayed() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()
        featureRepo.requests.add(
            FeatureRequest(
                id = "feat-42",
                title = "Génération PDF Factur-X 2026",
                description = "Génération du PDF combiné avec XML intégré",
                category = FeatureCategory.TAX_COMPLIANCE,
                authorId = "user-1",
                authorEmail = "user1@ledgerhub.app",
                voteCount = 7L,
                status = FeatureStatus.PROPOSED,
                createdAt = "2026-09-01T00:00:00Z",
            ),
        )

        val viewModel = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            dispatcher = testDispatcher,
        )

        setContent {
            LedgerHubTheme {
                SupportFeedbackScreen(viewModel = viewModel)
            }
        }

        // Bascule vers l'onglet Boîte à idées
        onNodeWithTag(SupportFeedbackTags.TAB_IDEAS).performClick()

        onNodeWithTag(SupportFeedbackTags.IDEAS_LIST).assertIsDisplayed()
        onNodeWithTag(SupportFeedbackTags.IDEA_ITEM_PREFIX + "feat-42").assertIsDisplayed()

        // Interaction de vote
        onNodeWithTag(SupportFeedbackTags.BTN_VOTE_PREFIX + "feat-42").performClick()

        // Vérification de l'incrémentation optimiste (7 -> 8)
        assertEquals(8L, viewModel.uiState.value.featureRequests.first().voteCount)
        assertTrue(viewModel.uiState.value.featureRequests.first().hasVoted)
    }

    @Test
    fun submitTicketForm_inputsAndSubmitsSuccessfully() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()
        val viewModel = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            dispatcher = testDispatcher,
        )

        setContent {
            LedgerHubTheme {
                SupportFeedbackScreen(viewModel = viewModel)
            }
        }

        onNodeWithTag(SupportFeedbackTags.INPUT_SUBJECT).performTextInput("Question TVA débits")
        waitForIdle()
        onNodeWithTag(SupportFeedbackTags.INPUT_DESCRIPTION).performTextInput("Détail complet sur l'option pour le paiement de la taxe d'après les débits")
        waitForIdle()
        onNodeWithTag(SupportFeedbackTags.BTN_SUBMIT_TICKET).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(SupportFeedbackTags.SUCCESS_BANNER).assertIsDisplayed()
        assertEquals(1, viewModel.uiState.value.tickets.size)
        assertEquals("Question TVA débits", viewModel.uiState.value.tickets.first().subject)
    }
}
