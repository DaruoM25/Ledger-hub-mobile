package com.ledgerhub.presentation.settings

import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.DeleteAccountUseCase
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.settings.TaxSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaxSettingsViewModelDangerZoneTest {

    private class FakeTaxSettingsRepository : TaxSettingsRepository {
        override suspend fun loadSettings(): Result<TaxSettings> = Result.success(TaxSettings.Default)
        override suspend fun saveSettings(settings: TaxSettings): Result<Unit> = Result.success(Unit)
    }

    private class FakeAuthRepository : AuthRepository {
        var deletedEmail: String? = null

        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))

        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)

        override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun deleteAccount(email: String): Result<Unit> {
            deletedEmail = email
            return Result.success(Unit)
        }
    }

    @Test
    fun dangerZone_initialState_isSecure() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val authRepo = FakeAuthRepository()
        val deleteUseCase = DeleteAccountUseCase(authRepo)
        val viewModel = TaxSettingsViewModel(
            repository = FakeTaxSettingsRepository(),
            deleteAccountUseCase = deleteUseCase,
            currentUserEmail = "owner@cabinet.fr",
            dispatcher = testDispatcher,
        )

        val state = viewModel.uiState.value
        assertFalse(state.showDeleteAccountDialog)
        assertEquals("", state.deleteConfirmationInput)
        assertFalse(state.isDeleteUnlocked)
        assertFalse(state.isDeletingAccount)
        assertFalse(state.accountDeleted)
    }

    @Test
    fun deleteAccountDialog_unlocksOnlyOnExactKeyword() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val authRepo = FakeAuthRepository()
        val deleteUseCase = DeleteAccountUseCase(authRepo)
        val viewModel = TaxSettingsViewModel(
            repository = FakeTaxSettingsRepository(),
            deleteAccountUseCase = deleteUseCase,
            currentUserEmail = "owner@cabinet.fr",
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(TaxSettingsIntent.OpenDeleteAccountDialog)
        assertTrue(viewModel.uiState.value.showDeleteAccountDialog)

        // Casse minuscule
        viewModel.processIntent(TaxSettingsIntent.DeleteConfirmationInputChanged("supprimer"))
        assertFalse(viewModel.uiState.value.isDeleteUnlocked)

        // Préfixe incomplet
        viewModel.processIntent(TaxSettingsIntent.DeleteConfirmationInputChanged("SUPPRIM"))
        assertFalse(viewModel.uiState.value.isDeleteUnlocked)

        // Espace parasite
        viewModel.processIntent(TaxSettingsIntent.DeleteConfirmationInputChanged("SUPPRIMER "))
        assertFalse(viewModel.uiState.value.isDeleteUnlocked)

        // Chaîne exacte
        viewModel.processIntent(TaxSettingsIntent.DeleteConfirmationInputChanged("SUPPRIMER"))
        assertTrue(viewModel.uiState.value.isDeleteUnlocked)
    }

    @Test
    fun confirmDeleteAccount_triggersUseCase_andSignalsAccountDeleted() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val authRepo = FakeAuthRepository()
        val deleteUseCase = DeleteAccountUseCase(authRepo)
        val viewModel = TaxSettingsViewModel(
            repository = FakeTaxSettingsRepository(),
            deleteAccountUseCase = deleteUseCase,
            currentUserEmail = "owner@cabinet.fr",
            dispatcher = testDispatcher,
        )

        viewModel.processIntent(TaxSettingsIntent.OpenDeleteAccountDialog)
        viewModel.processIntent(TaxSettingsIntent.DeleteConfirmationInputChanged("SUPPRIMER"))
        viewModel.processIntent(TaxSettingsIntent.ConfirmDeleteAccount)

        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isDeletingAccount)
        assertFalse(state.showDeleteAccountDialog)
        assertTrue(state.accountDeleted)
        assertEquals("owner@cabinet.fr", authRepo.deletedEmail)
    }
}
