package com.ledgerhub.data.support

import com.ledgerhub.db.FeatureRequest as FeatureRequestRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.support.AlreadyVotedException
import com.ledgerhub.domain.support.FeatureCategory
import com.ledgerhub.domain.support.FeatureFeedbackRepository
import com.ledgerhub.domain.support.FeatureRequest
import com.ledgerhub.domain.support.FeatureStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implémentation SQLDelight du dépôt de boîte à idées et de votes (US-30).
 */
class SqlDelightFeatureFeedbackRepository(
    private val database: LedgerHubDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : FeatureFeedbackRepository {

    override suspend fun getFeatureRequests(currentUserId: String): Result<List<FeatureRequest>> = runCatching {
        withContext(ioDispatcher) {
            val userVotes = if (currentUserId.isNotBlank()) {
                database.featureVoteQueries.selectVotesByUser(currentUserId).executeAsList()
                    .map { it.featureRequestId }
                    .toSet()
            } else {
                emptySet()
            }

            database.featureRequestQueries.selectAllByPopularity().executeAsList().map { row ->
                row.toDomain(hasVoted = userVotes.contains(row.id))
            }
        }
    }

    override suspend fun getFeatureRequestById(id: String, currentUserId: String): Result<FeatureRequest?> = runCatching {
        withContext(ioDispatcher) {
            val row = database.featureRequestQueries.selectById(id).executeAsOneOrNull() ?: return@withContext null
            val hasVoted = if (currentUserId.isNotBlank()) {
                database.featureVoteQueries.selectVote(currentUserId, id).executeAsOneOrNull() != null
            } else {
                false
            }
            row.toDomain(hasVoted = hasVoted)
        }
    }

    override suspend fun submitFeatureRequest(featureRequest: FeatureRequest): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            database.featureRequestQueries.insertOrReplace(
                id = featureRequest.id,
                title = featureRequest.title,
                description = featureRequest.description,
                category = featureRequest.category.rawValue,
                authorId = featureRequest.authorId,
                authorEmail = featureRequest.authorEmail,
                voteCount = featureRequest.voteCount,
                status = featureRequest.status.rawValue,
                createdAt = featureRequest.createdAt,
            )
        }
    }

    override suspend fun hasUserVoted(userId: String, featureRequestId: String): Result<Boolean> = runCatching {
        withContext(ioDispatcher) {
            database.featureVoteQueries.selectVote(userId, featureRequestId).executeAsOneOrNull() != null
        }
    }

    override suspend fun voteFeatureRequest(
        userId: String,
        featureRequestId: String,
        votedAt: String,
    ): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            database.transaction {
                val existing = database.featureVoteQueries.selectVote(userId, featureRequestId).executeAsOneOrNull()
                if (existing != null) {
                    throw AlreadyVotedException(userId, featureRequestId)
                }
                database.featureVoteQueries.insertVote(userId, featureRequestId, votedAt)
                database.featureRequestQueries.incrementVoteCount(featureRequestId)
            }
        }
    }

    override suspend fun cancelVote(userId: String, featureRequestId: String): Result<Unit> = runCatching {
        withContext(ioDispatcher) {
            database.transaction {
                val existing = database.featureVoteQueries.selectVote(userId, featureRequestId).executeAsOneOrNull()
                if (existing != null) {
                    database.featureVoteQueries.deleteVote(userId, featureRequestId)
                    database.featureRequestQueries.decrementVoteCount(featureRequestId)
                }
            }
        }
    }

    private fun FeatureRequestRow.toDomain(hasVoted: Boolean): FeatureRequest = FeatureRequest(
        id = id,
        title = title,
        description = description,
        category = FeatureCategory.fromRaw(category),
        authorId = authorId,
        authorEmail = authorEmail,
        voteCount = voteCount,
        status = FeatureStatus.fromRaw(status),
        createdAt = createdAt,
        hasVoted = hasVoted,
    )
}
