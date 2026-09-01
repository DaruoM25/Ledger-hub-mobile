package com.ledgerhub.presentation.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.auth.AuthRepository
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.sirene.SireneCompany
import com.ledgerhub.domain.sirene.SireneLookupResult
import com.ledgerhub.domain.sirene.SireneLookupService
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Niveau 3a (US-21) — parcours sémantique de l'inscription par SIRET sous Robolectric.
 *
 * Les règles (seuil des 14 chiffres, transitions d'état) sont déjà verrouillées aux niveaux 1 et 2 :
 * ce niveau vérifie que le **geste** produit l'écran attendu — taper un SIRET, voir l'indicateur,
 * puis le badge vert et la raison sociale reportée.
 *
 * L'écran est relié à un vrai [AuthViewModel] : câbler un état figé ferait passer les tests sur une
 * interface qui ne réagit à rien.
 *
 * Qualifiers d'un téléphone réel (`w411dp-h891dp`) : l'appareil Robolectric par défaut ne fait que
 * 320 × 470 dp, où le formulaire d'inscription — quatre champs, un badge, un bouton — n'aurait
 * aucune chance de tenir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class AuthScreenRobolectricTest {

    private val validSiret = MockSireneLookupService.DEMO_SIRET
    private val expectedCompany = MockSireneLookupService.DEFAULT_COMPANY_NAME

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    /** Connexion hors sujet ici : le dépôt d'authentification n'est jamais sollicité. */
    private class UnusedAuthRepository : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> =
            Result.success(Unit)
    }

    /**
     * Répertoire dont la réponse est **pilotée par le test** : l'indicateur de chargement ne peut
     * s'observer que si l'on maîtrise l'instant de la réponse. Courir contre un `delay` réel
     * donnerait un test instable, ou lent d'une seconde par cas.
     */
    private class DeferredSireneLookupService(
        private val gate: CompletableDeferred<SireneLookupResult>,
    ) : SireneLookupService {
        override suspend fun lookup(siret: String): SireneLookupResult = gate.await()
    }

    @Composable
    private fun AuthHost(
        sirene: SireneLookupService = MockSireneLookupService(simulatedDelayMillis = 0L),
        language: AppLanguage = AppLanguage.FR,
        startOnRegister: Boolean = true,
    ) {
        val viewModel = remember {
            AuthViewModel(
                authRepository = UnusedAuthRepository(),
                sireneLookupService = sirene,
                dispatcher = UnconfinedTestDispatcher(),
            ).apply { if (startOnRegister) processIntent(AuthIntent.ModeChanged(true)) }
        }
        val state by viewModel.uiState.collectAsState()

        CompositionLocalProvider(LocalAppLanguage provides language) {
            AuthContent(uiState = state, onIntent = viewModel::processIntent)
        }
    }

    // ── Bascule des onglets ─────────────────────────────────────────────────

    @Test
    fun theScreen_opensOnLogin_andSwitchesToRegistration() = runComposeUiTest {
        setContent { AuthHost(startOnRegister = false) }

        onNodeWithTag(AuthTags.SCREEN).assertIsDisplayed()
        // En connexion, le bloc SIRET n'existe pas dans l'arbre.
        onNodeWithTag(AuthTags.SIRET_INPUT).assertDoesNotExist()

        onNodeWithTag(AuthTags.TAB_REGISTER).performScrollTo().performClick()
        waitForIdle()

        // `performScrollTo` avant chaque assertion : le formulaire d'inscription est plus haut que
        // l'écran d'un téléphone, et un nœud hors du viewport n'est pas « affiché ».
        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().assertIsDisplayed()
        onNodeWithTag(AuthTags.COMPANY_NAME_INPUT).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUTH_REGISTER_TITLE)).performScrollTo().assertIsDisplayed()
    }

    /** Le lien historique « S'inscrire » mène désormais à l'onglet, pas à une destination morte. */
    @Test
    fun theRegisterLink_switchesToTheRegistrationTab() = runComposeUiTest {
        setContent { AuthHost(startOnRegister = false) }

        onNodeWithTag(AuthTags.REGISTER_LINK).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().assertIsDisplayed()
    }

    // ── Saisie du SIRET ─────────────────────────────────────────────────────

    @Test
    fun anIncompleteSiret_showsNeitherLoaderNorBadge() = runComposeUiTest {
        setContent { AuthHost() }

        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput("9012345670001")
        waitForIdle()

        onNodeWithTag(AuthTags.SIRET_LOADER, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE).assertDoesNotExist()
        // La loupe reste en place tant qu'aucune vérification n'est lancée.
        onNodeWithTag(AuthTags.SIRET_SEARCH_ICON, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theFourteenthDigit_fillsTheCompanyName_andShowsTheVerifiedBadge() = runComposeUiTest {
        setContent { AuthHost() }

        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput(validSiret)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUTH_SIRENE_VERIFIED_BADGE)).assertIsDisplayed()
        onNodeWithTag(AuthTags.COMPANY_NAME_INPUT)
            .performScrollTo()
            .assertTextContains(expectedCompany)
    }

    /** L'indicateur de vérification, observé pendant que la réponse est retenue par le test. */
    @Test
    fun duringTheVerification_theFieldCarriesItsLoader() = runComposeUiTest {
        val gate = CompletableDeferred<SireneLookupResult>()
        setContent { AuthHost(sirene = DeferredSireneLookupService(gate)) }

        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput(validSiret)
        waitForIdle()

        onNodeWithTag(AuthTags.SIRET_LOADER, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(AuthTags.SIRET_SEARCH_ICON, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(tr(StringKey.AUTH_SIRENE_VERIFYING)).assertIsDisplayed()
        onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE).assertDoesNotExist()

        gate.complete(
            SireneLookupResult.Verified(SireneCompany(validSiret, expectedCompany, "EURL")),
        )
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag(AuthTags.SIRET_LOADER, useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Réinitialisation ────────────────────────────────────────────────────

    @Test
    fun correctingTheSiretBelowFourteenDigits_removesTheBadgeAndTheName() = runComposeUiTest {
        setContent { AuthHost() }
        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput(validSiret)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(AuthTags.SIRET_INPUT).performTextReplacement(validSiret.dropLast(1))
        waitForIdle()

        onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE).assertDoesNotExist()
        // Le champ est vidé : la raison sociale venait du répertoire, elle n'a plus lieu d'être.
        onNodeWithText(expectedCompany).assertDoesNotExist()
    }

    // ── Issues défavorables ─────────────────────────────────────────────────

    @Test
    fun anUnknownSiret_showsTheNotFoundMessage_withoutBadge() = runComposeUiTest {
        setContent { AuthHost() }

        onNodeWithTag(AuthTags.SIRET_INPUT)
            .performScrollTo()
            .performTextInput(MockSireneLookupService.UNKNOWN_SIRET)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(AuthTags.SIRENE_MESSAGE).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(AuthTags.SIRENE_VERIFIED_BADGE).assertDoesNotExist()
        onNodeWithText(tr(StringKey.AUTH_SIRENE_NOT_FOUND)).performScrollTo().assertIsDisplayed()
    }

    // ── Activation du bouton ────────────────────────────────────────────────

    @Test
    fun theRegisterButton_isEnabledOnlyOnceEverythingIsFilledAndVerified() = runComposeUiTest {
        setContent { AuthHost() }

        onNodeWithTag(AuthTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(AuthTags.EMAIL_FIELD).performScrollTo().performTextInput("vous@cabinet.fr")
        onNodeWithTag(AuthTags.PASSWORD_FIELD).performScrollTo().performTextInput("motdepasse")
        onNodeWithTag(AuthTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput(validSiret)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(AuthTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_theRegistrationFormIsFullyTranslated() = runComposeUiTest {
        setContent { AuthHost(language = AppLanguage.EN) }

        onNodeWithText(tr(StringKey.AUTH_REGISTER_TITLE, AppLanguage.EN))
            .performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUTH_SIRET_LABEL, AppLanguage.EN))
            .performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.AUTH_COMPANY_NAME_LABEL, AppLanguage.EN))
            .performScrollTo().assertIsDisplayed()

        onNodeWithTag(AuthTags.SIRET_INPUT).performScrollTo().performTextInput(validSiret)
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(AuthTags.SIRENE_VERIFIED_BADGE).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithText(tr(StringKey.AUTH_SIRENE_VERIFIED_BADGE, AppLanguage.EN))
            .performScrollTo().assertIsDisplayed()
    }
}
