package com.ledgerhub.presentation.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.settings.TaxSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * N2 — Test Robolectric pour le bouton et le flux de déconnexion dans Paramètres.
 *
 * Vérifie :
 * 1. La présence et conformité ergonomique du bouton neutre (>= 48 dp).
 * 2. L'absence d'erreur lors du clic.
 * 3. La bascule réactive de l'état vers `loggedOut = true`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class SettingsLogoutRobolectricTest {

    private class StubTaxSettingsRepository : TaxSettingsRepository {
        override suspend fun loadSettings(): Result<TaxSettings> = Result.success(TaxSettings.Default)
        override suspend fun saveSettings(settings: TaxSettings): Result<Unit> = Result.success(Unit)
    }

    private class FakeAuthRepository : AuthRepository {
        var logoutCalled = false

        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))

        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)

        override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(email: String): Result<Unit> = Result.success(Unit)

        override suspend fun logout(): Result<Unit> {
            logoutCalled = true
            return Result.success(Unit)
        }
    }

    @Test
    fun logoutButton_hasTouchTargetAtLeast48dp_andTransitionsToLoggedOutState() = runComposeUiTest {
        val testDispatcher = UnconfinedTestDispatcher()
        val authRepo = FakeAuthRepository()
        val viewModel = TaxSettingsViewModel(
            repository = StubTaxSettingsRepository(),
            authRepository = authRepo,
            dispatcher = testDispatcher,
        )

        setContent {
            TaxSettingsScreen(viewModel = viewModel)
        }

        // 1. Assertion sur l'ergonomie tactile minimale de 48 dp
        onNodeWithTag(TaxSettingsTags.LOGOUT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHeightIsAtLeast(48.dp)

        // 2. Déclenchement du clic
        onNodeWithTag(TaxSettingsTags.LOGOUT_BUTTON).performClick()

        // 3. Assertions sur la fin de session
        assertTrue(authRepo.logoutCalled, "AuthRepository.logout() doit être invoqué")
        assertTrue(viewModel.uiState.value.loggedOut, "L'état UI doit basculer vers loggedOut = true")
        assertFalse(viewModel.uiState.value.isLoggingOut, "isLoggingOut doit être remis à false")
        assertNull(viewModel.uiState.value.errorMessage, "Aucun message d'erreur ne doit être présent")
    }
}
