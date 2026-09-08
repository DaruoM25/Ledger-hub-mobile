package com.ledgerhub.data.auth

import com.ledgerhub.domain.auth.ServerException
import com.ledgerhub.domain.auth.UnauthorizedException
import com.ledgerhub.domain.auth.ValidationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests unitaires du client Ktor pour l'API d'authentification et de cycle de vie (US-26).
 *
 * Simule les réponses distantes avec [MockEngine] pour valider la sérialisation, les en-têtes
 * et la conversion des codes d'erreur HTTP 401, 422, 500.
 */
class KtorAuthApiClientTest {

    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun requestPasswordReset_nominal_returnsSuccess() = runTest {
        var requestedUrl = ""
        var requestBody = ""

        val engine = MockEngine { request ->
            requestedUrl = request.url.encodedPath
            requestBody = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val client = KtorAuthApiClient(createClient(engine), baseUrl = "http://localhost")
        val result = client.requestPasswordReset("expert@cabinet.fr")

        assertTrue(result.isSuccess)
        assertEquals("/api/auth/forgot-password", requestedUrl)
        assertTrue(requestBody.contains("expert@cabinet.fr"))
    }

    @Test
    fun requestPasswordReset_mapsHttpErrorsToTypedExceptions() = runTest {
        val errorCodes = listOf(
            HttpStatusCode.Unauthorized to UnauthorizedException::class,
            HttpStatusCode.UnprocessableEntity to ValidationException::class,
            HttpStatusCode.InternalServerError to ServerException::class,
        )

        for ((statusCode, expectedExceptionClass) in errorCodes) {
            val engine = MockEngine {
                respond("Error", statusCode, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val client = KtorAuthApiClient(createClient(engine), baseUrl = "http://localhost")
            val result = client.requestPasswordReset("expert@cabinet.fr")

            assertTrue(result.isFailure)
            assertEquals(expectedExceptionClass, result.exceptionOrNull()!!::class)
        }
    }

    @Test
    fun anonymizeAccount_nominal_sendsBearerTokenAndConfirmationKeyword() = runTest {
        var authHeader: String? = null
        var requestBody = ""

        val engine = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            requestBody = request.body.toByteArray().decodeToString()
            respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        val client = KtorAuthApiClient(createClient(engine), baseUrl = "http://localhost")
        val result = client.anonymizeAccount(
            email = "expert@cabinet.fr",
            confirmation = "SUPPRIMER",
            token = "jwt-session-token-xyz",
        )

        assertTrue(result.isSuccess)
        assertEquals("Bearer jwt-session-token-xyz", authHeader)
        assertTrue(requestBody.contains("\"confirmation\":\"SUPPRIMER\""))
        assertTrue(requestBody.contains("\"email\":\"expert@cabinet.fr\""))
    }

    @Test
    fun anonymizeAccount_mapsHttpErrorsToTypedExceptions() = runTest {
        val errorCodes = listOf(
            HttpStatusCode.Unauthorized to UnauthorizedException::class,
            HttpStatusCode.UnprocessableEntity to ValidationException::class,
            HttpStatusCode.InternalServerError to ServerException::class,
        )

        for ((statusCode, expectedExceptionClass) in errorCodes) {
            val engine = MockEngine {
                respond("Error", statusCode, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val client = KtorAuthApiClient(createClient(engine), baseUrl = "http://localhost")
            val result = client.anonymizeAccount(
                email = "expert@cabinet.fr",
                confirmation = "SUPPRIMER",
            )

            assertTrue(result.isFailure)
            assertEquals(expectedExceptionClass, result.exceptionOrNull()!!::class)
        }
    }
}
