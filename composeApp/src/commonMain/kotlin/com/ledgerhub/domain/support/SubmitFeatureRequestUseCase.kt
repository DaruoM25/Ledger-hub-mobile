package com.ledgerhub.domain.support

import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Use-case de proposition d'une nouvelle idée dans la boîte à idées (US-30).
 */
class SubmitFeatureRequestUseCase(
    private val featureFeedbackRepository: FeatureFeedbackRepository,
    private val clock: Clock = SystemClock,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        title: String,
        description: String,
        category: FeatureCategory,
        authorId: String,
        authorEmail: String,
    ): Result<FeatureRequest> = runCatching {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        val trimmedEmail = authorEmail.trim()
        val trimmedAuthorId = authorId.trim()

        if (trimmedTitle.length < 4) {
            throw InvalidFeatureRequestException("Le titre de l'idée doit comporter au moins 4 caractères.")
        }
        if (trimmedDescription.length < 10) {
            throw InvalidFeatureRequestException("La description de l'idée doit comporter au moins 10 caractères.")
        }
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            throw InvalidFeatureRequestException("L'e-mail de l'auteur est invalide.")
        }

        val request = FeatureRequest(
            id = Uuid.random().toString(),
            title = trimmedTitle,
            description = trimmedDescription,
            category = category,
            authorId = trimmedAuthorId,
            authorEmail = trimmedEmail,
            voteCount = 0L,
            status = FeatureStatus.PROPOSED,
            createdAt = clock.nowIso(),
            hasVoted = false,
        )

        featureFeedbackRepository.submitFeatureRequest(request).getOrThrow()
        request
    }
}
