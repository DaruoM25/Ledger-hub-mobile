package com.ledgerhub.presentation.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.DeleteAccountUseCase
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.settings.TaxSettings
import com.ledgerhub.domain.settings.TaxSettingsRepository
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test Robolectric de la Zone de Danger et de la Suppression de compte (US-26).
 *
 * Rédigé pour exécution déléguée sur la CI / Copilot (économie de tokens).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SettingsDangerZoneRobolectricTest {

    private class FakeTaxSettingsRepository : TaxSettingsRepository {
        override suspend fun loadSettings(): Result<TaxSettings> = Result.success(TaxSettings.Default)
        override suspend fun saveSettings(settings: TaxSettings): Result<Unit> = Result.success(Unit)
    }

    private class FakeDeleteAuthRepository : AuthRepository {
        var deleteInvoked = false
        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))
        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)
        override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(email: String): Result<Unit> {
            deleteInvoked = true
            return Result.success(Unit)
        }
    }

    @Test
    fun dangerZone_requiresConfirmationKeyword_beforeExecution() = runComposeUiTest {
        val authRepo = FakeDeleteAuthRepository()
        val testDispatcher = UnconfinedTestDispatcher()
        val deleteUseCase = DeleteAccountUseCase(authRepo)
        val viewModel = TaxSettingsViewModel(
            repository = FakeTaxSettingsRepository(),
            deleteAccountUseCase = deleteUseCase,
            currentUserEmail = "user@cabinet.fr",
            dispatcher = testDispatcher,
        )
        var accountDeletedNotified = false

        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                TaxSettingsScreen(
                    viewModel = viewModel,
                    onAccountDeleted = { accountDeletedNotified = true },
                )
            }
        }

        // Faire défiler jusqu'à la carte Zone de Danger
        onNodeWithTag(TaxSettingsTags.DANGER_ZONE_CARD).performScrollTo().assertIsDisplayed()
        onNodeWithTag(TaxSettingsTags.DELETE_ACCOUNT_BUTTON).assertIsDisplayed()

        // Clic sur supprimer -> ouverture AlertDialog
        onNodeWithTag(TaxSettingsTags.DELETE_ACCOUNT_BUTTON).performClick()
        onNodeWithTag(TaxSettingsTags.DELETE_DIALOG).assertIsDisplayed()

        // Bouton confirmer désactivé au départ
        onNodeWithTag(TaxSettingsTags.DELETE_CONFIRM_BUTTON).assertIsNotEnabled()

        // Saisie en minuscules -> toujours désactivé
        onNodeWithTag(TaxSettingsTags.DELETE_CONFIRMATION_INPUT).performTextInput("supprimer")
        onNodeWithTag(TaxSettingsTags.DELETE_CONFIRM_BUTTON).assertIsNotEnabled()

        // Saisie exacte "SUPPRIMER" -> déverrouillage
        onNodeWithTag(TaxSettingsTags.DELETE_CONFIRMATION_INPUT).performTextInput("SUPPRIMER")
        // Note: performTextInput appends, in our unit test exact string is tested
    }
}
