package com.ledgerhub.presentation.integrations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.integrations.IntegrationModule
import com.ledgerhub.domain.integrations.IntegrationStatus
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 3a (US-20) — rendu du hub d'intégrations sous Robolectric (JVM, CI sans émulateur).
 *
 * Le catalogue et son ordre sont déjà verrouillés au niveau 1 : ce niveau vérifie que **l'écran
 * les montre** — les quatre cartes, leurs badges, et la réaction au toucher d'un module verrouillé.
 *
 * L'écran est relié à un vrai [IntegrationsHubViewModel] : câbler un état figé ferait passer les
 * tests sur une interface qui ne réagit à rien.
 *
 * Qualifiers larges (`w720dp`) : `LazyVerticalGrid` **virtualise**, donc une carte hors du viewport
 * n'existe pas dans l'arbre sémantique. L'appareil Robolectric par défaut ne fait que 320 dp de
 * large, et les assertions sur les quatre modules échoueraient pour une raison sans rapport avec
 * ce qu'elles éprouvent. À 720 dp, la grille sort quatre colonnes et tout est rendu d'un coup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w720dp-h1000dp")
@OptIn(ExperimentalTestApi::class)
class IntegrationsHubRobolectricTest {

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    @Composable
    private fun Hub(language: AppLanguage = AppLanguage.FR) {
        val viewModel = remember { IntegrationsHubViewModel() }
        val state by viewModel.uiState.collectAsState()

        CompositionLocalProvider(LocalAppLanguage provides language) {
            IntegrationsHubContent(uiState = state, onIntent = viewModel::processIntent)
        }
    }

    // ── Structure de l'écran ────────────────────────────────────────────────

    @Test
    fun theHub_rendersItsContainerAndHeading() = runComposeUiTest {
        setContent { Hub() }

        onNodeWithTag(IntegrationsHubTags.CONTAINER).assertIsDisplayed()
        onNodeWithText(tr(StringKey.INTEGRATIONS_TITLE)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.INTEGRATIONS_SUBTITLE)).assertIsDisplayed()
    }

    /** Le cœur de l'US : les quatre modules imposés, chacun sous son tag du cahier des charges. */
    @Test
    fun theFourRequiredModules_areDisplayed() = runComposeUiTest {
        setContent { Hub() }

        onNodeWithTag("integration_card_stripe").assertIsDisplayed()
        onNodeWithTag("integration_card_slack").assertIsDisplayed()
        onNodeWithTag("integration_card_fec").assertIsDisplayed()
        onNodeWithTag("integration_card_bank_sync").assertIsDisplayed()
    }

    @Test
    fun everyModule_showsItsTitleAndDescription() = runComposeUiTest {
        setContent { Hub() }

        IntegrationModule.entries.forEach { module ->
            onNodeWithText(tr(module.titleKey)).assertIsDisplayed()
            onNodeWithText(tr(module.descriptionKey)).assertIsDisplayed()
        }
    }

    // ── Badges ──────────────────────────────────────────────────────────────

    /**
     * Les badges sont visés dans l'arbre **non fusionné** : la carte porte une sémantique de clic
     * qui absorbe ses descendants, et son badge n'y est plus un nœud à part entière.
     */
    @Test
    fun everyModule_carriesItsStatusBadge() = runComposeUiTest {
        setContent { Hub() }

        IntegrationModule.entries.forEach { module ->
            onNodeWithTag(IntegrationsHubTags.badge(module), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    /**
     * Chaque libellé de badge apparaît **exactement deux fois** : deux modules en bêta, deux
     * annoncés. `onAllNodesWithText` et non `onNodeWithText`, qui exigerait un nœud unique et
     * échouerait sur la seconde occurrence — pour une raison sans rapport avec ce qui est éprouvé.
     */
    @Test
    fun bothBadgeWordings_areVisibleOnScreen() = runComposeUiTest {
        setContent { Hub() }

        // « Bêta - Accès anticipé » : Stripe et l'export FEC.
        onAllNodesWithText(tr(StringKey.INTEGRATION_BADGE_BETA), useUnmergedTree = true)
            .assertCountEquals(2)
        // « Bientôt disponible » : Slack et la synchronisation bancaire.
        onAllNodesWithText(tr(StringKey.INTEGRATION_BADGE_COMING_SOON), useUnmergedTree = true)
            .assertCountEquals(2)
    }

    @Test
    fun theBetaBadge_sitsOnTheBetaModules() = runComposeUiTest {
        setContent { Hub() }

        val betaLabel = tr(StringKey.INTEGRATION_BADGE_BETA)
        val comingSoonLabel = tr(StringKey.INTEGRATION_BADGE_COMING_SOON)

        IntegrationModule.entries.forEach { module ->
            val expected = when (module.status) {
                IntegrationStatus.BETA -> betaLabel
                IntegrationStatus.COMING_SOON -> comingSoonLabel
            }
            onNodeWithTag(IntegrationsHubTags.card(module))
                .assertIsDisplayed()
                .assertTextContains(expected)
        }
    }

    // ── Réaction au toucher d'un module verrouillé ──────────────────────────

    @Test
    fun tappingALockedModule_explainsWhyNothingHappens() = runComposeUiTest {
        setContent { Hub() }

        onNodeWithTag(IntegrationsHubTags.NOTICE).assertDoesNotExist()
        onNodeWithTag(IntegrationsHubTags.card(IntegrationModule.STRIPE_PAYMENTS)).performClick()
        waitForIdle()

        onNodeWithTag(IntegrationsHubTags.NOTICE).assertIsDisplayed()
        onNodeWithText(
            tr(IntegrationModule.STRIPE_PAYMENTS.titleKey) + " — " +
                tr(StringKey.INTEGRATIONS_LOCKED_NOTICE),
        ).assertIsDisplayed()
    }

    @Test
    fun dismissingTheNotice_removesIt() = runComposeUiTest {
        setContent { Hub() }
        onNodeWithTag(IntegrationsHubTags.card(IntegrationModule.BANK_SYNC)).performClick()
        waitForIdle()

        onNodeWithTag(IntegrationsHubTags.NOTICE).performClick()
        waitForIdle()

        onNodeWithTag(IntegrationsHubTags.NOTICE).assertDoesNotExist()
    }

    /** Le bandeau suit le dernier module touché, il ne s'empile pas. */
    @Test
    fun tappingASecondModule_replacesTheNotice() = runComposeUiTest {
        setContent { Hub() }

        onNodeWithTag(IntegrationsHubTags.card(IntegrationModule.STRIPE_PAYMENTS)).performClick()
        waitForIdle()
        onNodeWithTag(IntegrationsHubTags.card(IntegrationModule.SLACK_NOTIFICATIONS)).performClick()
        waitForIdle()

        onNodeWithTag(IntegrationsHubTags.NOTICE)
            .assertTextContains(tr(IntegrationModule.SLACK_NOTIFICATIONS.titleKey), substring = true)
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_theHubIsFullyTranslated_badgesIncluded() = runComposeUiTest {
        setContent { Hub(language = AppLanguage.EN) }

        onNodeWithText(tr(StringKey.INTEGRATIONS_TITLE, AppLanguage.EN)).assertIsDisplayed()
        IntegrationModule.entries.forEach { module ->
            onNodeWithText(tr(module.titleKey, AppLanguage.EN)).assertIsDisplayed()
        }
        onAllNodesWithText(
            tr(StringKey.INTEGRATION_BADGE_BETA, AppLanguage.EN),
            useUnmergedTree = true,
        ).assertCountEquals(2)
        onAllNodesWithText(
            tr(StringKey.INTEGRATION_BADGE_COMING_SOON, AppLanguage.EN),
            useUnmergedTree = true,
        ).assertCountEquals(2)
    }

    // ── Déclencheur du shell ────────────────────────────────────────────────

    @Test
    fun theShellTrigger_isDisplayedAndClickable() = runComposeUiTest {
        var opened = 0
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                IntegrationsHubTrigger(onClick = { opened++ })
            }
        }

        onNodeWithTag(IntegrationsHubTags.TRIGGER).assertIsDisplayed()
        onNodeWithText(tr(StringKey.INTEGRATIONS_TRIGGER_LABEL)).assertIsDisplayed()
        onNodeWithTag(IntegrationsHubTags.TRIGGER).performClick()

        assertEquals(1, opened)
    }
}
