package com.ledgerhub.presentation.auth

import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.RequestPasswordResetUseCase
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordIntent
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordSideEffect
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private class TestAuthRepository(
        private val shouldFail: Boolean = false,
    ) : AuthRepository {
        var requestedEmail: String? = null

        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))

        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            requestedEmail = email
            return if (shouldFail) Result.failure(Exception("Service indisponible")) else Result.success(Unit)
        }

        override suspend fun deleteAccount(email: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun initialState_hasCanSubmitDisabled() {
        val repository = TestAuthRepository()
        val useCase = RequestPasswordResetUseCase(repository)
        val viewModel = ForgotPasswordViewModel(useCase)

        val state = viewModel.uiState.value
        assertEquals("", state.email)
        assertFalse(state.isEmailValid)
        assertFalse(state.canSubmit)
        assertFalse(state.isLoading)
        assertFalse(state.isSubmitted)
        assertNull(state.errorMessage)
    }

    @Test
    fun emailChanged_enablesSubmitOnlyWhenValid() {
        val repository = TestAuthRepository()
        val useCase = RequestPasswordResetUseCase(repository)
        val viewModel = ForgotPasswordViewModel(useCase)

        viewModel.processIntent(ForgotPasswordIntent.EmailChanged("not-an-email"))
        assertFalse(viewModel.uiState.value.isEmailValid)
        assertFalse(viewModel.uiState.value.canSubmit)

        viewModel.processIntent(ForgotPasswordIntent.EmailChanged("valid@cabinet.fr"))
        assertTrue(viewModel.uiState.value.isEmailValid)
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun submit_triggersSuccessState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = TestAuthRepository()
        val useCase = RequestPasswordResetUseCase(repository)
        val viewModel = ForgotPasswordViewModel(useCase, dispatcher = testDispatcher)

        viewModel.processIntent(ForgotPasswordIntent.EmailChanged("user@domain.com"))
        viewModel.processIntent(ForgotPasswordIntent.Submit)

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isSubmitted)
        assertNull(state.errorMessage)
        assertEquals("user@domain.com", repository.requestedEmail)
    }

    @Test
    fun submit_failure_setsErrorMessage() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = TestAuthRepository(shouldFail = true)
        val useCase = RequestPasswordResetUseCase(repository)
        val viewModel = ForgotPasswordViewModel(useCase, dispatcher = testDispatcher)

        viewModel.processIntent(ForgotPasswordIntent.EmailChanged("user@domain.com"))
        viewModel.processIntent(ForgotPasswordIntent.Submit)

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isSubmitted)
        assertEquals("Service indisponible", state.errorMessage)
    }

    @Test
    fun onBackToLogin_emitsSideEffect() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = TestAuthRepository()
        val useCase = RequestPasswordResetUseCase(repository)
        val viewModel = ForgotPasswordViewModel(useCase, dispatcher = testDispatcher)

        viewModel.onBackToLogin()
        val effect = viewModel.sideEffect.first()
        assertEquals(ForgotPasswordSideEffect.NavigateBackToLogin, effect)
    }
}
