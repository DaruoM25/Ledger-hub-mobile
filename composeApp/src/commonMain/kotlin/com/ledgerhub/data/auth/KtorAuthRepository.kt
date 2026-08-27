package com.ledgerhub.data.auth

import com.ledgerhub.data.network.authBaseUrl
import com.ledgerhub.data.network.createPlatformHttpClient
import com.ledgerhub.domain.auth.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
private data class LoginRequest(val email: String, val password: String)

/**
 * Repository d'authentification réel — cible le backend local de dev (voir [authBaseUrl]),
 * jamais un service cloud (pivot 100% local, voir App.kt). Structure symétrique aux autres
 * repositories Sql*Repository : implémente le contrat [AuthRepository] côté domaine, garde le
 * détail réseau (Ktor, JSON) entièrement privé à cette classe.
 */
class KtorAuthRepository(
    private val httpClient: HttpClient = createPlatformHttpClient(),
    private val baseUrl: String = authBaseUrl,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val response = httpClient.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = password))
        }
        check(response.status.isSuccess()) {
            "Échec de connexion (code ${response.status.value}) — vérifiez que le serveur local tourne sur $baseUrl"
        }
    }
}
