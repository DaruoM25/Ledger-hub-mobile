package com.ledgerhub.domain.support

/**
 * Contrat d'accès à la boîte à idées et aux votes (US-30).
 */
interface FeatureFeedbackRepository {
    suspend fun getFeatureRequests(currentUserId: String): Result<List<FeatureRequest>>
    suspend fun getFeatureRequestById(id: String, currentUserId: String): Result<FeatureRequest?>
    suspend fun submitFeatureRequest(featureRequest: FeatureRequest): Result<Unit>
    suspend fun hasUserVoted(userId: String, featureRequestId: String): Result<Boolean>
    suspend fun voteFeatureRequest(userId: String, featureRequestId: String, votedAt: String): Result<Unit>
    suspend fun cancelVote(userId: String, featureRequestId: String): Result<Unit>
}
