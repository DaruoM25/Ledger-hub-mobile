package com.ledgerhub.presentation.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.RequestPasswordResetUseCase
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordScreen
import com.ledgerhub.presentation.auth.forgotpassword.ForgotPasswordViewModel
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test Robolectric de l'écran Mot de passe oublié (US-26).
 *
 * Rédigé pour exécution déléguée sur la CI / Copilot (économie de tokens).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ForgotPasswordRobolectricTest {

    private class FakeResetAuthRepository : AuthRepository {
        var resetCalled = false
        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))
        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)
        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            resetCalled = true
            return Result.success(Unit)
        }
        override suspend fun deleteAccount(email: String): Result<Unit> = Result.success(Unit)
    }

    @Test
    fun forgotPassword_fullFlow_nominal() = runComposeUiTest {
        val repository = FakeResetAuthRepository()
        val testDispatcher = UnconfinedTestDispatcher()
        val useCase = RequestPasswordResetUseCase(repository)
        val viewModel = ForgotPasswordViewModel(useCase, dispatcher = testDispatcher)
        var backClicked = false

        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                ForgotPasswordScreen(
                    viewModel = viewModel,
                    onBackToLogin = { backClicked = true },
                )
            }
        }

        onNodeWithTag(AuthTags.FORGOT_PASSWORD_SCREEN).assertIsDisplayed()

        // Au départ le bouton est désactivé
        onNodeWithTag(AuthTags.FORGOT_PASSWORD_SUBMIT_BUTTON).assertIsNotEnabled()

        // Saisie d'un email valide
        onNodeWithTag(AuthTags.FORGOT_PASSWORD_EMAIL_FIELD).performTextInput("comptable@cabinet.fr")
        onNodeWithTag(AuthTags.FORGOT_PASSWORD_SUBMIT_BUTTON).assertIsEnabled()

        // Soumission
        onNodeWithTag(AuthTags.FORGOT_PASSWORD_SUBMIT_BUTTON).performClick()

        // Message de confirmation anti-énumération affiché
        onNodeWithTag(AuthTags.FORGOT_PASSWORD_SUCCESS_MESSAGE).assertIsDisplayed()
        assertTrue(repository.resetCalled)

        // Clic retour à la connexion
        onNodeWithTag(AuthTags.FORGOT_PASSWORD_BACK_BUTTON).performClick()
        assertTrue(backClicked)
    }
}
