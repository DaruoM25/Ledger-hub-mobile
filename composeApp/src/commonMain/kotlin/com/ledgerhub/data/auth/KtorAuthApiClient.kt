package com.ledgerhub.data.auth

import com.ledgerhub.data.remote.createPlatformHttpClient
import com.ledgerhub.data.remote.ledgerApiBaseUrl
import com.ledgerhub.domain.auth.AuthApiClient
import com.ledgerhub.domain.auth.NetworkException
import com.ledgerhub.domain.auth.ServerException
import com.ledgerhub.domain.auth.UnauthorizedException
import com.ledgerhub.domain.auth.ValidationException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
internal data class ForgotPasswordPayload(val email: String)

@Serializable
internal data class AnonymizeAccountPayload(
    val email: String,
    val confirmation: String,
)

/**
 * Implémentation Ktor du client d'API d'authentification et de cycle de vie (US-26).
 *
 * Consomme les routes distantes du backend Web :
 * - `POST /api/auth/forgot-password`
 * - `POST /api/auth/account/anonymize` (avec Bearer token et mot-clé "SUPPRIMER").
 */
class KtorAuthApiClient(
    private val httpClient: HttpClient = createPlatformHttpClient(),
    private val baseUrl: String = ledgerApiBaseUrl,
) : AuthApiClient {

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/api/auth/forgot-password") {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordPayload(email = email))
            }
            when {
                response.status.isSuccess() -> Result.success(Unit)
                response.status == HttpStatusCode.Unauthorized ->
                    Result.failure(UnauthorizedException("Non autorisé (code 401)"))
                response.status == HttpStatusCode.UnprocessableEntity ->
                    Result.failure(ValidationException("Format ou données invalides (code 422)"))
                response.status.value in 500..599 ->
                    Result.failure(ServerException("Erreur serveur distant (code ${response.status.value})"))
                else ->
                    Result.failure(NetworkException("Erreur inattendue (code ${response.status.value})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(NetworkException("Échec de communication réseau: ${e.message}", e))
        }
    }

    override suspend fun anonymizeAccount(
        email: String,
        confirmation: String,
        token: String?,
    ): Result<Unit> {
        return try {
            val response = httpClient.post("$baseUrl/api/auth/account/anonymize") {
                contentType(ContentType.Application.Json)
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(AnonymizeAccountPayload(email = email, confirmation = confirmation))
            }
            when {
                response.status.isSuccess() -> Result.success(Unit)
                response.status == HttpStatusCode.Unauthorized ->
                    Result.failure(UnauthorizedException("Session invalide ou expirée (code 401)"))
                response.status == HttpStatusCode.UnprocessableEntity ->
                    Result.failure(ValidationException("Confirmation invalide : mot-clé 'SUPPRIMER' requis (code 422)"))
                response.status.value in 500..599 ->
                    Result.failure(ServerException("Erreur serveur lors de l'anonymisation (code ${response.status.value})"))
                else ->
                    Result.failure(NetworkException("Erreur inattendue (code ${response.status.value})"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(NetworkException("Échec de communication réseau: ${e.message}", e))
        }
    }
}
