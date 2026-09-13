package com.ledgerhub.domain.support

/**
 * Use-case de consultation des idées de la communauté ordonnées par popularité (US-30).
 */
class GetFeatureRequestsUseCase(
    private val featureFeedbackRepository: FeatureFeedbackRepository,
) {
    suspend operator fun invoke(currentUserId: String): Result<List<FeatureRequest>> =
        featureFeedbackRepository.getFeatureRequests(currentUserId.trim())
}
