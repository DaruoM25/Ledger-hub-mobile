package com.ledgerhub.domain.support

/**
 * Exception levée lorsqu'un utilisateur a déjà voté pour une idée.
 */
class AlreadyVotedException(
    val userId: String,
    val featureRequestId: String,
    message: String = "L'utilisateur $userId a déjà voté pour l'idée $featureRequestId",
) : IllegalStateException(message)

/**
 * Exception levée lors d'une validation incorrecte de formulaire ticket.
 */
class InvalidTicketException(message: String) : IllegalArgumentException(message)

/**
 * Exception levée lors d'une validation incorrecte de formulaire idée.
 */
class InvalidFeatureRequestException(message: String) : IllegalArgumentException(message)
