package com.ledgerhub.domain.support

import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Use-case de création d'un ticket d'aide réglementaire / technique (US-30).
 */
class CreateSupportTicketUseCase(
    private val supportRepository: SupportRepository,
    private val clock: Clock = SystemClock,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        userId: String,
        userEmail: String,
        category: SupportCategory,
        subject: String,
        description: String,
    ): Result<SupportTicket> = runCatching {
        val trimmedSubject = subject.trim()
        val trimmedDescription = description.trim()
        val trimmedEmail = userEmail.trim()

        if (trimmedSubject.length < 3) {
            throw InvalidTicketException("Le sujet du ticket doit comporter au moins 3 caractères.")
        }
        if (trimmedDescription.length < 5) {
            throw InvalidTicketException("La description du problème doit comporter au moins 5 caractères.")
        }
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            throw InvalidTicketException("L'adresse e-mail utilisateur est invalide.")
        }

        val ticket = SupportTicket(
            id = Uuid.random().toString(),
            userId = userId.trim(),
            userEmail = trimmedEmail,
            category = category,
            subject = trimmedSubject,
            description = trimmedDescription,
            status = TicketStatus.OPEN,
            createdAt = clock.nowIso(),
            updatedAt = null,
        )

        supportRepository.createTicket(ticket).getOrThrow()
        ticket
    }
}
