package com.ledgerhub.domain.auth

import kotlinx.coroutines.CancellationException

/**
 * Cas d'usage de réinitialisation de mot de passe (US-26).
 *
 * Valide syntaxiquement l'adresse email et délègue au dépôt d'authentification
 * sous garantie de protection anti-énumération.
 */
class RequestPasswordResetUseCase(
    private val authRepository: AuthRepository,
    private val authApiClient: AuthApiClient? = null,
) {
    suspend fun execute(email: String): Result<Unit> {
        val normalized = normalizeEmail(email)
        if (!EmailValidator.isValid(normalized)) {
            return Result.failure(InvalidEmailException())
        }
        return try {
            val localResult = authRepository.requestPasswordReset(normalized)
            if (localResult.isFailure) return localResult

            if (authApiClient != null) {
                val remoteResult = authApiClient.requestPasswordReset(normalized)
                if (remoteResult.isFailure) return remoteResult
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
