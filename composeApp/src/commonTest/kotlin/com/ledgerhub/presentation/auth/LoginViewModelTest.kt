package com.ledgerhub.presentation.auth

import com.ledgerhub.domain.auth.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Faux repository — pas d'accès réseau dans les tests, contrôle direct du succès/échec.
 * [delayMillis] simule un appel réseau non instantané — nécessaire pour observer l'état
 * `Loading` intermédiaire sous [StandardTestDispatcher] (voir MockCreditNoteRepository pour le
 * même principe) : sans délai, la coroutine se termine entièrement dans le même `runCurrent()`.
 */
private class FakeAuthRepository(
    private val result: Result<Unit>,
    private val delayMillis: Long = 0L,
) : AuthRepository {
    var lastEmail: String? = null
    var lastPassword: String? = null

    override suspend fun login(email: String, password: String): Result<Unit> {
        delay(delayMillis)
        lastEmail = email
        lastPassword = password
        return result
    }
}

/** Tests QA (Skill 2) du cycle de vie de la connexion. */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @Test
    fun initialState_hasSubmitDisabled() {
        val viewModel = LoginViewModel(authRepository = FakeAuthRepository(Result.success(Unit)))
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun blankPassword_keepsSubmitDisabled() {
        val viewModel = LoginViewModel(authRepository = FakeAuthRepository(Result.success(Unit)))
        viewModel.processIntent(LoginIntent.EmailChanged("vous@cabinet.fr"))
        assertFalse(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun filledEmailAndPassword_enablesSubmit() {
        val viewModel = LoginViewModel(authRepository = FakeAuthRepository(Result.success(Unit)))
        viewModel.processIntent(LoginIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(LoginIntent.PasswordChanged("motdepasse"))
        assertTrue(viewModel.uiState.value.isSubmitEnabled)
    }

    @Test
    fun submit_withValidCredentials_transitionsThroughLoadingToSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeAuthRepository(Result.success(Unit), delayMillis = 500L)
        val viewModel = LoginViewModel(authRepository = repository, dispatcher = dispatcher)
        viewModel.processIntent(LoginIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(LoginIntent.PasswordChanged("motdepasse"))

        viewModel.processIntent(LoginIntent.Submit)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loginSucceeded)
        assertEquals("vous@cabinet.fr", repository.lastEmail)
        assertEquals("motdepasse", repository.lastPassword)
    }

    @Test
    fun submit_whenRepositoryFails_surfacesErrorMessage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeAuthRepository(Result.failure(IllegalStateException("Identifiants invalides")))
        val viewModel = LoginViewModel(authRepository = repository, dispatcher = dispatcher)
        viewModel.processIntent(LoginIntent.EmailChanged("vous@cabinet.fr"))
        viewModel.processIntent(LoginIntent.PasswordChanged("motdepasse"))

        viewModel.processIntent(LoginIntent.Submit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loginSucceeded)
        assertEquals("Identifiants invalides", state.errorMessage)
    }

    @Test
    fun submit_withBlankFields_isNoOp() = runTest {
        val repository = FakeAuthRepository(Result.success(Unit))
        val viewModel = LoginViewModel(authRepository = repository)

        viewModel.processIntent(LoginIntent.Submit)

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, repository.lastEmail)
    }
}
