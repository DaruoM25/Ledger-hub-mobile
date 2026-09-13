package com.ledgerhub.domain.support

import com.ledgerhub.domain.time.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VoteFeatureRequestUseCaseTest {

    private class FakeFeatureFeedbackRepository : FeatureFeedbackRepository {
        val requests = mutableMapOf<String, FeatureRequest>()
        val votes = mutableSetOf<Pair<String, String>>() // (userId, featureRequestId)

        override suspend fun getFeatureRequests(currentUserId: String): Result<List<FeatureRequest>> {
            return Result.success(
                requests.values.map {
                    it.copy(hasVoted = votes.contains(currentUserId to it.id))
                }.sortedByDescending { it.voteCount },
            )
        }

        override suspend fun getFeatureRequestById(id: String, currentUserId: String): Result<FeatureRequest?> {
            val req = requests[id] ?: return Result.success(null)
            return Result.success(req.copy(hasVoted = votes.contains(currentUserId to id)))
        }

        override suspend fun submitFeatureRequest(featureRequest: FeatureRequest): Result<Unit> {
            requests[featureRequest.id] = featureRequest
            return Result.success(Unit)
        }

        override suspend fun hasUserVoted(userId: String, featureRequestId: String): Result<Boolean> {
            return Result.success(votes.contains(userId to featureRequestId))
        }

        override suspend fun voteFeatureRequest(userId: String, featureRequestId: String, votedAt: String): Result<Unit> {
            if (votes.contains(userId to featureRequestId)) {
                return Result.failure(AlreadyVotedException(userId, featureRequestId))
            }
            votes.add(userId to featureRequestId)
            val req = requests[featureRequestId]
            if (req != null) {
                requests[featureRequestId] = req.copy(voteCount = req.voteCount + 1)
            }
            return Result.success(Unit)
        }

        override suspend fun cancelVote(userId: String, featureRequestId: String): Result<Unit> {
            if (votes.remove(userId to featureRequestId)) {
                val req = requests[featureRequestId]
                if (req != null) {
                    requests[featureRequestId] = req.copy(voteCount = req.voteCount - 1)
                }
            }
            return Result.success(Unit)
        }
    }

    @Test
    fun voteFeatureRequest_successFirstVote() = runTest {
        val repo = FakeFeatureFeedbackRepository()
        val initialRequest = FeatureRequest(
            id = "feat-1",
            title = "Export Chorus Pro direct",
            description = "Intégration directe via API Chorus Pro sans passer par PPF",
            category = FeatureCategory.INTEGRATIONS,
            authorId = "user-1",
            authorEmail = "user1@ledgerhub.app",
            voteCount = 5L,
            status = FeatureStatus.PROPOSED,
            createdAt = "2026-09-01T00:00:00Z",
        )
        repo.submitFeatureRequest(initialRequest)

        val clock = FixedClock("2026-09-13T12:00:00Z")
        val useCase = VoteFeatureRequestUseCase(repo, clock)

        val result = useCase(userId = "user-2", featureRequestId = "feat-1")
        assertTrue(result.isSuccess)
        assertEquals(6L, repo.requests["feat-1"]?.voteCount)
        assertTrue(repo.votes.contains("user-2" to "feat-1"))
    }

    @Test
    fun voteFeatureRequest_failsOnDoubleVote() = runTest {
        val repo = FakeFeatureFeedbackRepository()
        val initialRequest = FeatureRequest(
            id = "feat-1",
            title = "Export Chorus Pro direct",
            description = "Intégration directe via API Chorus Pro sans passer par PPF",
            category = FeatureCategory.INTEGRATIONS,
            authorId = "user-1",
            authorEmail = "user1@ledgerhub.app",
            voteCount = 5L,
            status = FeatureStatus.PROPOSED,
            createdAt = "2026-09-01T00:00:00Z",
        )
        repo.submitFeatureRequest(initialRequest)
        val useCase = VoteFeatureRequestUseCase(repo)

        // Premier vote : succès
        useCase(userId = "user-2", featureRequestId = "feat-1").getOrThrow()

        // Second vote par le même utilisateur : levée de AlreadyVotedException
        assertFailsWith<AlreadyVotedException> {
            useCase(userId = "user-2", featureRequestId = "feat-1").getOrThrow()
        }
        assertEquals(6L, repo.requests["feat-1"]?.voteCount)
    }

    @Test
    fun voteFeatureRequest_failsOnBlankParameters() = runTest {
        val repo = FakeFeatureFeedbackRepository()
        val useCase = VoteFeatureRequestUseCase(repo)

        assertFailsWith<IllegalArgumentException> {
            useCase(userId = "", featureRequestId = "feat-1").getOrThrow()
        }
        assertFailsWith<IllegalArgumentException> {
            useCase(userId = "user-1", featureRequestId = " ").getOrThrow()
        }
    }
}
