package com.ledgerhub.domain.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeleteAccountUseCaseTest {

    private class TestAuthRepository : AuthRepository {
        var deletedEmail: String? = null
        var shouldFail: Boolean = false

        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))

        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)

        override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAccount(email: String): Result<Unit> {
            deletedEmail = email
            return if (shouldFail) Result.failure(RuntimeException("Database error")) else Result.success(Unit)
        }
    }

    private class TestAuthApiClient : AuthApiClient {
        var requestedEmail: String? = null
        var requestedConfirmation: String? = null
        var requestedToken: String? = null
        var errorToThrow: Throwable? = null

        override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun anonymizeAccount(email: String, confirmation: String, token: String?): Result<Unit> {
            requestedEmail = email
            requestedConfirmation = confirmation
            requestedToken = token
            return errorToThrow?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }

    @Test
    fun validEmailAndConfirmation_callsRepositoryWithNormalizedEmail() = runTest {
        val repository = TestAuthRepository()
        val useCase = DeleteAccountUseCase(repository)

        val result = useCase.execute("  Owner@Cabinet.FR  ", "SUPPRIMER")

        assertTrue(result.isSuccess)
        assertEquals("owner@cabinet.fr", repository.deletedEmail)
    }

    @Test
    fun invalidConfirmation_returnsFailure_withoutCallingRepository() = runTest {
        val repository = TestAuthRepository()
        val useCase = DeleteAccountUseCase(repository)

        val result = useCase.execute("owner@cabinet.fr", "WRONG_KEYWORD")

        assertTrue(result.isFailure)
        assertIs<ValidationException>(result.exceptionOrNull())
        assertEquals(null, repository.deletedEmail)
    }

    @Test
    fun validCall_callsRemoteClient_withBearerTokenAndConfirmation() = runTest {
        val repository = TestAuthRepository()
        val apiClient = TestAuthApiClient()
        val useCase = DeleteAccountUseCase(repository, apiClient)

        val result = useCase.execute(
            email = "owner@cabinet.fr",
            confirmation = "SUPPRIMER",
            token = "active-jwt-token",
        )

        assertTrue(result.isSuccess)
        assertEquals("owner@cabinet.fr", apiClient.requestedEmail)
        assertEquals("SUPPRIMER", apiClient.requestedConfirmation)
        assertEquals("active-jwt-token", apiClient.requestedToken)
        assertEquals("owner@cabinet.fr", repository.deletedEmail)
    }

    @Test
    fun remoteError_isPropagated_withoutLocalDeletion() = runTest {
        val repository = TestAuthRepository()
        val apiClient = TestAuthApiClient().apply {
            errorToThrow = UnauthorizedException("Non autorisé (401)")
        }
        val useCase = DeleteAccountUseCase(repository, apiClient)

        val result = useCase.execute("owner@cabinet.fr", "SUPPRIMER")

        assertTrue(result.isFailure)
        assertIs<UnauthorizedException>(result.exceptionOrNull())
        assertEquals(null, repository.deletedEmail)
    }

    @Test
    fun blankEmail_returnsFailure() = runTest {
        val repository = TestAuthRepository()
        val useCase = DeleteAccountUseCase(repository)

        val result = useCase.execute("   ")

        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(null, repository.deletedEmail)
    }

    @Test
    fun repositoryFailure_isPropagated() = runTest {
        val repository = TestAuthRepository().apply { shouldFail = true }
        val useCase = DeleteAccountUseCase(repository)

        val result = useCase.execute("user@cabinet.fr")

        assertTrue(result.isFailure)
        assertEquals("Database error", result.exceptionOrNull()?.message)
    }
}
