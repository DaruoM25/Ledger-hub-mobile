package com.ledgerhub.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.PasswordValidator
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * N2 — Test Robolectric de validation réactive du formulaire d'authentification.
 *
 * Valide les exigences de sécurité RGPD & DGFiP :
 * 1. Blocage du bouton de soumission sur e-mail erroné.
 * 2. Affichage immédiat du message d'exigence réglementaire sur mot de passe faible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class AuthFormValidationRobolectricTest {

    private class StubAuthRepository : AuthRepository {
        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, "", ""))

        override suspend fun register(account: UserAccount, password: String): Result<UserAccount> =
            Result.success(account)

        override suspend fun requestPasswordReset(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteAccount(email: String): Result<Unit> = Result.success(Unit)
    }

    @Composable
    private fun AuthScreenHost(startOnRegister: Boolean = false) {
        val viewModel = remember {
            AuthViewModel(
                authRepository = StubAuthRepository(),
                sireneLookupService = MockSireneLookupService(simulatedDelayMillis = 0L),
            ).also {
                if (startOnRegister) {
                    it.processIntent(AuthIntent.ModeChanged(true))
                }
            }
        }
        CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
            AuthScreen(viewModel = viewModel)
        }
    }

    @Test
    fun invalidEmail_disablesSubmitButton_andShowsEmailErrorMessage() = runComposeUiTest {
        setContent { AuthScreenHost(startOnRegister = false) }

        // Saisie d'un e-mail erroné
        onNodeWithTag(AuthTags.EMAIL_FIELD)
            .performScrollTo()
            .performTextInput("invalid-email-address")

        onNodeWithTag(AuthTags.PASSWORD_FIELD)
            .performScrollTo()
            .performTextInput("ValidPass123!")

        // 1. Assertion sur l'apparition du message d'erreur sous le champ e-mail
        onNodeWithTag(AuthTags.EMAIL_ERROR)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Format d'adresse e-mail invalide")

        // 2. Assertion sur le blocage du bouton de connexion
        onNodeWithTag(AuthTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
    }

    @Test
    fun weakPassword_inRegistration_displaysRequirementErrorMessage() = runComposeUiTest {
        setContent { AuthScreenHost(startOnRegister = true) }

        // Saisie d'un mot de passe non conforme (sans majuscule, sans caractère spécial)
        onNodeWithTag(AuthTags.PASSWORD_FIELD)
            .performScrollTo()
            .performTextInput("test1234")

        // Assertion sur l'apparition du message sous le champ mot de passe
        onNodeWithTag(AuthTags.PASSWORD_ERROR)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(PasswordValidator.ERROR_MESSAGE)

        // Bouton de soumission doit rester inactif
        onNodeWithTag(AuthTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsNotEnabled()
    }
}
