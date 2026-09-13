package com.ledgerhub.domain.support

/**
 * Entrée de vote utilisateur sur une idée de la boîte à idées.
 */
data class FeatureVote(
    val userId: String,
    val featureRequestId: String,
    val votedAt: String,
)
