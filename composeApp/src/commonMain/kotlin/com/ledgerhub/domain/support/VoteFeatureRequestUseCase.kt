package com.ledgerhub.domain.support

import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock

/**
 * Use-case de vote pour une idée de la boîte à idées (US-30).
 *
 * Règle de déduplication : un utilisateur ne peut voter qu'une seule fois pour une idée donnée.
 * Tout second vote lève [AlreadyVotedException].
 */
class VoteFeatureRequestUseCase(
    private val featureFeedbackRepository: FeatureFeedbackRepository,
    private val clock: Clock = SystemClock,
) {
    suspend operator fun invoke(
        userId: String,
        featureRequestId: String,
    ): Result<Unit> = runCatching {
        val trimmedUserId = userId.trim()
        val trimmedFeatureId = featureRequestId.trim()

        if (trimmedUserId.isBlank() || trimmedFeatureId.isBlank()) {
            throw IllegalArgumentException("userId et featureRequestId ne peuvent être vides.")
        }

        val alreadyVoted = featureFeedbackRepository.hasUserVoted(
            userId = trimmedUserId,
            featureRequestId = trimmedFeatureId,
        ).getOrThrow()

        if (alreadyVoted) {
            throw AlreadyVotedException(
                userId = trimmedUserId,
                featureRequestId = trimmedFeatureId,
            )
        }

        featureFeedbackRepository.voteFeatureRequest(
            userId = trimmedUserId,
            featureRequestId = trimmedFeatureId,
            votedAt = clock.nowIso(),
        ).getOrThrow()
    }
}
