package com.ledgerhub.presentation.support

import com.ledgerhub.domain.support.AlreadyVotedException
import com.ledgerhub.domain.support.CreateSupportTicketUseCase
import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureFeedbackRepository
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.FeatureStatus
import com.ledgerhub.domain.support.GetFeatureRequestsUseCase
import com.ledgerhub.domain.support.SubmitFeatureRequestUseCase
import com.ledgerhub.domain.support.SupportCategory
import com.ledgerhub.domain.support.SupportRepository
import com.ledgerhub.domain.support.SupportTicket
import com.ledgerhub.domain.support.TicketStatus
import com.ledgerhub.domain.support.VoteFeatureRequestUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SupportFeedbackViewModelTest {

    private class FakeSupportRepository : SupportRepository {
        val tickets = mutableListOf<SupportTicket>()

        override suspend fun getAllTickets(): Result<List<SupportTicket>> = Result.success(tickets)
        override suspend fun getTicketsByUser(userId: String): Result<List<SupportTicket>> =
            Result.success(tickets.filter { it.userId == userId })
        override suspend fun getTicketById(id: String): Result<SupportTicket?> =
            Result.success(tickets.firstOrNull { it.id == id })
        override suspend fun createTicket(ticket: SupportTicket): Result<Unit> {
            tickets.add(ticket)
            return Result.success(Unit)
        }
        override suspend fun updateTicketStatus(id: String, status: TicketStatus, updatedAt: String): Result<Unit> =
            Result.success(Unit)
        override suspend fun countOpenTicketsByUser(userId: String): Result<Long> =
            Result.success(tickets.count { it.status == TicketStatus.OPEN }.toLong())
    }

    private class FakeFeatureFeedbackRepository : FeatureFeedbackRepository {
        val requests = mutableListOf<FeatureRequest>()
        val votes = mutableSetOf<Pair<String, String>>()
        var shouldFailVote = false

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
            if (shouldFailVote) {
                return Result.failure(RuntimeException("Erreur réseau simulée"))
            }
            if (votes.contains(userId to featureRequestId)) {
                return Result.failure(AlreadyVotedException(userId, featureRequestId))
            }
            votes.add(userId to featureRequestId)
            val index = requests.indexOfFirst { it.id == featureRequestId }
            if (index >= 0) {
                val current = requests[index]
                requests[index] = current.copy(voteCount = current.voteCount + 1)
            }
            return Result.success(Unit)
        }

        override suspend fun cancelVote(userId: String, featureRequestId: String): Result<Unit> {
            votes.remove(userId to featureRequestId)
            return Result.success(Unit)
        }
    }

    @Test
    fun loadData_populatesInitialState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()

        featureRepo.requests.add(
            FeatureRequest(
                id = "feat-1",
                title = "Export Chorus Pro",
                description = "Télétransmission directe API",
                category = FeatureCategory.INTEGRATIONS,
                authorId = "user-1",
                authorEmail = "u1@ledgerhub.app",
                voteCount = 3L,
                status = FeatureStatus.PROPOSED,
                createdAt = "2026-09-01T00:00:00Z",
            ),
        )

        val vm = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            currentUserId = "test-user",
            currentUserEmail = "test@ledgerhub.app",
            dispatcher = testDispatcher,
        )

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.featureRequests.size)
        assertEquals("Export Chorus Pro", state.featureRequests.first().title)
        assertEquals(3L, state.featureRequests.first().voteCount)
    }

    @Test
    fun submitTicket_success_addsTicketAndClearsInput() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()

        val vm = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            currentUserId = "test-user",
            currentUserEmail = "test@ledgerhub.app",
            dispatcher = testDispatcher,
        )

        advanceUntilIdle()

        vm.processIntent(SupportFeedbackIntent.TicketSubjectChanged("Question TVA 20%"))
        vm.processIntent(SupportFeedbackIntent.TicketDescriptionChanged("Précision sur la mention d'exonération article 293 B"))
        vm.processIntent(SupportFeedbackIntent.TicketCategoryChanged(SupportCategory.VAT_CALCULATION))
        vm.processIntent(SupportFeedbackIntent.SubmitTicket)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.ticketSubject)
        assertEquals("", state.ticketDescription)
        assertEquals(1, state.tickets.size)
        assertEquals("Question TVA 20%", state.tickets.first().subject)
        assertNotNull(state.successMessage)
    }

    @Test
    fun voteIdea_optimisticUpdateAndRollbackOnError() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()

        featureRepo.requests.add(
            FeatureRequest(
                id = "feat-1",
                title = "Export Chorus Pro",
                description = "Télétransmission directe API",
                category = FeatureCategory.INTEGRATIONS,
                authorId = "user-1",
                authorEmail = "u1@ledgerhub.app",
                voteCount = 5L,
                status = FeatureStatus.PROPOSED,
                createdAt = "2026-09-01T00:00:00Z",
            ),
        )
        featureRepo.shouldFailVote = true // Simuler un échec réseau

        val vm = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            currentUserId = "test-user",
            currentUserEmail = "test@ledgerhub.app",
            dispatcher = testDispatcher,
        )

        advanceUntilIdle()

        // Déclencher le vote
        vm.processIntent(SupportFeedbackIntent.VoteIdea("feat-1"))

        // Immédiatement : mise à jour optimiste dans le StateFlow
        assertEquals(6L, vm.uiState.value.featureRequests.first().voteCount)
        assertTrue(vm.uiState.value.featureRequests.first().hasVoted)

        // Traiter l'asynchronisme et observer le rollback
        advanceUntilIdle()

        assertEquals(5L, vm.uiState.value.featureRequests.first().voteCount)
        assertFalse(vm.uiState.value.featureRequests.first().hasVoted)
        assertNotNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun submitIdea_success_addsIdeaToList() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val supportRepo = FakeSupportRepository()
        val featureRepo = FakeFeatureFeedbackRepository()

        val vm = SupportFeedbackViewModel(
            supportRepository = supportRepo,
            createSupportTicketUseCase = CreateSupportTicketUseCase(supportRepo),
            getFeatureRequestsUseCase = GetFeatureRequestsUseCase(featureRepo),
            submitFeatureRequestUseCase = SubmitFeatureRequestUseCase(featureRepo),
            voteFeatureRequestUseCase = VoteFeatureRequestUseCase(featureRepo),
            currentUserId = "test-user",
            currentUserEmail = "test@ledgerhub.app",
            dispatcher = testDispatcher,
        )

        advanceUntilIdle()

        vm.processIntent(SupportFeedbackIntent.ShowIdeaDialog(true))
        vm.processIntent(SupportFeedbackIntent.IdeaTitleChanged("Mode sombre AMOLED"))
        vm.processIntent(SupportFeedbackIntent.IdeaDescriptionChanged("Ajout d'un thème ultra noir pour économiser la batterie OLED"))
        vm.processIntent(SupportFeedbackIntent.IdeaCategoryChanged(FeatureCategory.UI_UX))
        vm.processIntent(SupportFeedbackIntent.SubmitIdea)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.showIdeaDialog)
        assertEquals(1, state.featureRequests.size)
        assertEquals("Mode sombre AMOLED", state.featureRequests.first().title)
        assertNotNull(state.successMessage)
    }
}
