package com.ledgerhub.domain.auth

import kotlinx.coroutines.CancellationException

/**
 * Cas d'usage de suppression de compte conforme (RGPD Art. 17 vs LPF Art. L.102 B).
 *
 * Exécute le droit à l'effacement en purgeant l'enregistrement d'authentification ([UserAccount]),
 * tout en garantissant la préservation intégrale des pièces comptables et écritures
 * locales/distantes soumises à la conservation légale décennale (LPF Art. L.102 B & Code de commerce Art. L123-22).
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val authApiClient: AuthApiClient? = null,
) {
    suspend fun execute(
        email: String,
        confirmation: String = "SUPPRIMER",
        token: String? = null,
    ): Result<Unit> {
        val normalized = normalizeEmail(email)
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("L'adresse email ne peut pas être vide."))
        }
        if (confirmation.trim() != "SUPPRIMER") {
            return Result.failure(ValidationException("Le mot-clé de confirmation 'SUPPRIMER' est requis."))
        }
        return try {
            // 1. Appel distant vers le backend Web (/api/auth/account/anonymize)
            if (authApiClient != null) {
                val remoteResult = authApiClient.anonymizeAccount(
                    email = normalized,
                    confirmation = confirmation,
                    token = token,
                )
                if (remoteResult.isFailure) return remoteResult
            }

            // 2. Purge locale (UserAccount effacé, audit unique ACCOUNT_DELETED, pièces comptables préservées)
            authRepository.deleteAccount(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
