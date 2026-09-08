package com.ledgerhub.domain.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RequestPasswordResetUseCaseTest {

    private class TestAuthRepository : AuthRepository {
        var requestedEmail: String? = null
        var shouldFail: Boolean = false

        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))

        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            requestedEmail = email
            return if (shouldFail) Result.failure(RuntimeException("Network error")) else Result.success(Unit)
        }

        override suspend fun deleteAccount(email: String): Result<Unit> = Result.success(Unit)
    }

    private class TestAuthApiClient : AuthApiClient {
        var requestedEmail: String? = null
        var errorToThrow: Throwable? = null

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            requestedEmail = email
            return errorToThrow?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override suspend fun anonymizeAccount(email: String, confirmation: String, token: String?): Result<Unit> =
            Result.success(Unit)
    }

    @Test
    fun validEmail_callsRepositoryWithNormalizedEmail_andReturnsSuccess() = runTest {
        val repository = TestAuthRepository()
        val useCase = RequestPasswordResetUseCase(repository)

        val result = useCase.execute("  User.Test@Company.FR  ")

        assertTrue(result.isSuccess)
        assertEquals("user.test@company.fr", repository.requestedEmail)
    }

    @Test
    fun validEmail_callsRemoteClient_whenConfigured() = runTest {
        val repository = TestAuthRepository()
        val apiClient = TestAuthApiClient()
        val useCase = RequestPasswordResetUseCase(repository, apiClient)

        val result = useCase.execute("expert@cabinet.fr")

        assertTrue(result.isSuccess)
        assertEquals("expert@cabinet.fr", repository.requestedEmail)
        assertEquals("expert@cabinet.fr", apiClient.requestedEmail)
    }

    @Test
    fun remoteErrors_arePropagated() = runTest {
        val repository = TestAuthRepository()
        val apiClient = TestAuthApiClient().apply {
            errorToThrow = ValidationException("Données invalides (422)")
        }
        val useCase = RequestPasswordResetUseCase(repository, apiClient)

        val result = useCase.execute("expert@cabinet.fr")

        assertTrue(result.isFailure)
        assertIs<ValidationException>(result.exceptionOrNull())
    }

    @Test
    fun invalidEmail_returnsFailure_withoutCallingRepository() = runTest {
        val repository = TestAuthRepository()
        val useCase = RequestPasswordResetUseCase(repository)

        val result = useCase.execute("invalid-email-format")

        assertTrue(result.isFailure)
        assertIs<InvalidEmailException>(result.exceptionOrNull())
        assertEquals(null, repository.requestedEmail)
    }

    @Test
    fun repositoryFailure_isPropagated() = runTest {
        val repository = TestAuthRepository().apply { shouldFail = true }
        val useCase = RequestPasswordResetUseCase(repository)

        val result = useCase.execute("valid@domain.com")

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }
}

