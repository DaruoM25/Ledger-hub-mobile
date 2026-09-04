package com.ledgerhub.presentation.auth

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.auth.UserAccount
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-21) — inscription par SIRET au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que la vérification **réelle d'une seconde** aboutit sur
 * l'appareil, que le clavier logiciel saisit bien les 14 chiffres dans le champ, et que les champs
 * offrent une cible tactile décente. S'y ajoute l'export de la capture QA officielle.
 *
 * Le service est ici le **vrai** [MockSireneLookupService], avec son délai d'une seconde : c'est
 * l'unique niveau où ce délai est éprouvé tel qu'il sera vécu.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AuthScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US21_mobile_auth_siret_lookup_sdk_gphone64_x86_64.png"
    private val validSiret = MockSireneLookupService.DEMO_SIRET
    private val expectedCompany = MockSireneLookupService.DEFAULT_COMPANY_NAME

    private fun tr(key: StringKey) = AppTranslations.get(key, AppLanguage.FR)

    /** Connexion hors sujet : seul le répertoire SIRENE est sollicité par ce niveau. */
    private class UnusedAuthRepository : AuthRepository {
        override suspend fun login(email: String, password: String): Result<UserAccount> =
            Result.success(UserAccount(email, companyName = "", siret = ""))

        override suspend fun register(
            account: UserAccount,
            password: String,
        ): Result<UserAccount> = Result.success(account)
    }

    @Composable
    private fun AuthHost() {
        val viewModel = remember {
            AuthViewModel(
                authRepository = UnusedAuthRepository(),
                sireneLookupService = MockSireneLookupService(),
            ).apply { processIntent(AuthIntent.ModeChanged(true)) }
        }
        val state by viewModel.uiState.collectAsState()

        CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
            AuthContent(uiState = state, onIntent = viewModel::processIntent)
        }
    }

    private fun render() {
        composeRule.setContent { AuthHost() }
        composeRule.waitForIdle()
    }

    private fun typeSiretAndAwaitVerification(siret: String = validSiret) {
        composeRule.onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput(siret)
        // La vérification dure réellement une seconde sur l'appareil : on attend le badge plutôt
        // que de supposer la réponse acquise.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Parcours tactile ────────────────────────────────────────────────────

    @Test
    fun theRegistrationForm_isDisplayedOnDevice() {
        render()

        composeRule.onNodeWithTag(AuthTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTags.COMPANY_NAME_INPUT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(tr(StringKey.AUTH_REGISTER_TITLE)).assertIsDisplayed()
    }

    @Test
    fun typingTheSiret_verifiesTheCompany_andFillsItsName() {
        render()

        typeSiretAndAwaitVerification()

        composeRule.onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(tr(StringKey.AUTH_SIRENE_VERIFIED_BADGE)).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTags.COMPANY_NAME_INPUT).assertTextContains(expectedCompany)
    }

    /** Accessibilité tactile : les champs de saisie tiennent la cible minimale. */
    @Test
    fun theSiretAndCompanyFields_meetTheMinimumTouchTargetHeight() {
        render()

        composeRule.onNodeWithTag(AuthTags.SIRET_INPUT)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(AuthTags.COMPANY_NAME_INPUT)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun correctingTheSiret_removesTheBadgeOnDevice() {
        render()
        typeSiretAndAwaitVerification()

        composeRule.onNodeWithTag(AuthTags.SIRET_INPUT)
            .performTextReplacement(validSiret.dropLast(1))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun theRegisterButton_becomesEnabledOnceVerified() {
        render()

        composeRule.onNodeWithTag(AuthTags.EMAIL_FIELD)
            .performScrollTo()
            .performTextInput("vous@cabinet.fr")
        composeRule.onNodeWithTag(AuthTags.PASSWORD_FIELD)
            .performScrollTo()
            .performTextInput("motdepasse")
        typeSiretAndAwaitVerification()

        composeRule.onNodeWithTag(AuthTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-21 : le SIRET saisi, la raison sociale complétée automatiquement
     * et le badge vert de vérification — ce que l'écran doit démontrer.
     *
     * Écrite **aux deux emplacements** : les fichiers de l'application (traçabilité) et
     * `/sdcard/Download`, d'où `scripts/run-qa.ps1 -ScreenshotPrefix "US21"` la rapatrie sans
     * `adb pull` manuel (acquis de l'US-20).
     */
    @Test
    fun exportsTheSiretLookupScreenshot() {
        render()
        typeSiretAndAwaitVerification()

        composeRule.onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE).assertIsDisplayed()
        composeRule.onNodeWithTag(AuthTags.COMPANY_NAME_INPUT).assertTextContains(expectedCompany)
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(AuthTags.SCREEN)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFile = File(context.getExternalFilesDir(null), screenshotName)
        appFile.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        val sharedFile = File("/sdcard/Download", screenshotName).apply { parentFile?.mkdirs() }
        runCatching {
            sharedFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        assertTrue(appFile.exists(), "Capture US-21 non écrite : ${appFile.absolutePath}")
        assertTrue(appFile.length() > 0, "Capture US-21 vide : ${appFile.absolutePath}")
    }
}
