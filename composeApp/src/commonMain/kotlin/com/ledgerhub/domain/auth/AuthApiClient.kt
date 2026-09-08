package com.ledgerhub.domain.auth

/**
 * Contrat du client réseau d'authentification et de cycle de vie du compte utilisateur (US-26).
 *
 * Établit la parité avec les endpoints distants de `ledgerhub-web`.
 */
interface AuthApiClient {

    /**
     * Déclenche la demande de réinitialisation de mot de passe côté backend.
     * Cible : POST `/api/auth/forgot-password`
     *
     * @param email Adresse email du compte
     * @return `Result.success(Unit)` ou `Result.failure` avec une exception typée ([UnauthorizedException], [ValidationException], [ServerException], etc.).
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * Déclenche l'anonymisation / suppression du compte côté backend.
     * Cible : POST `/api/auth/account/anonymize`
     *
     * Le backend Web exige :
     * 1. Un jeton Bearer actif dans l'en-tête Authorization (sinon HTTP 401).
     * 2. Le mot-clé de confirmation strict "SUPPRIMER" dans le corps de requête (sinon HTTP 422).
     *
     * @param email Adresse email du compte à supprimer
     * @param confirmation Mot-clé de confirmation ("SUPPRIMER")
     * @param token Jeton de session Bearer actif
     */
    suspend fun anonymizeAccount(
        email: String,
        confirmation: String = "SUPPRIMER",
        token: String? = null,
    ): Result<Unit>
}
