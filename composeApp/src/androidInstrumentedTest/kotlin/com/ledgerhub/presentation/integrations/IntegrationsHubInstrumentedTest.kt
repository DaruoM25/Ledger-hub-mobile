package com.ledgerhub.presentation.integrations

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.integrations.IntegrationModule
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.theme.LedgerHubTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-20) — hub d'intégrations au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que les quatre cartes tiennent **réellement** dans
 * l'écran de l'appareil cible (la grille virtualise, une carte hors champ n'existerait pas), que
 * chacune offre une cible tactile décente, et que le toucher d'un module verrouillé produit bien
 * son bandeau. S'y ajoute l'export de la capture QA officielle.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class IntegrationsHubInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US20_mobile_integrations_hub_sdk_gphone64_x86_64.png"

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    /** L'écran relié à un ViewModel réel — le câblage du shell, à l'échelle du test. */
    @Composable
    private fun Hub() {
        val viewModel = remember { IntegrationsHubViewModel() }
        val state by viewModel.uiState.collectAsState()

        // Thème complet : la capture officielle doit montrer le hub tel que l'utilisateur le voit,
        // fond slate compris, et non sur le fond clair par défaut d'une activité nue.
        LedgerHubTheme {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IntegrationsHubContent(uiState = state, onIntent = viewModel::processIntent)
                }
            }
        }
    }

    private fun render() {
        composeRule.setContent { Hub() }
        composeRule.waitForIdle()
    }

    // ── Parcours tactile ────────────────────────────────────────────────────

    /**
     * Les quatre modules sont visibles **sans défilement** sur l'appareil cible : c'est ce que la
     * largeur minimale de carte (170 dp → deux colonnes sur un Pixel 5) est censée garantir.
     */
    @Test
    fun theFourModules_areVisibleWithoutScrollingOnDevice() {
        render()

        composeRule.onNodeWithTag(IntegrationsHubTags.CONTAINER).assertIsDisplayed()
        IntegrationModule.entries.forEach { module ->
            composeRule.onNodeWithTag(IntegrationsHubTags.card(module)).assertIsDisplayed()
        }
    }

    @Test
    fun everyCard_carriesItsBadgeOnDevice() {
        render()

        IntegrationModule.entries.forEach { module ->
            composeRule.onNodeWithTag(IntegrationsHubTags.badge(module), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        composeRule.onAllNodesWithText(tr(StringKey.INTEGRATION_BADGE_BETA), useUnmergedTree = true)
            .assertCountEquals(2)
        composeRule.onAllNodesWithText(
            tr(StringKey.INTEGRATION_BADGE_COMING_SOON),
            useUnmergedTree = true,
        ).assertCountEquals(2)
    }

    /** Accessibilité tactile : une carte entière est une cible, pas seulement son titre. */
    @Test
    fun everyCard_meetsTheMinimumTouchTargetHeight() {
        render()

        IntegrationModule.entries.forEach { module ->
            composeRule.onNodeWithTag(IntegrationsHubTags.card(module))
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun tappingALockedModule_showsTheNotice_andDismissingItRemovesIt() {
        render()

        composeRule.onNodeWithTag(IntegrationsHubTags.card(IntegrationModule.STRIPE_PAYMENTS))
            .performTouchInput { click() }
        // L'apparition passe par une recomposition : on l'attend plutôt que de la supposer acquise
        // à la milliseconde du toucher.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(IntegrationsHubTags.NOTICE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(IntegrationsHubTags.NOTICE)
            .assertTextContains(tr(StringKey.INTEGRATIONS_LOCKED_NOTICE), substring = true)

        composeRule.onNodeWithTag(IntegrationsHubTags.NOTICE).performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(IntegrationsHubTags.NOTICE).fetchSemanticsNodes().isEmpty()
        }
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-20 : les quatre modules verrouillés et leurs badges colorés, tels
     * qu'ils s'offrent à l'utilisateur — ce que l'écran doit démontrer.
     *
     * Écrite **aux deux emplacements** : les fichiers de l'application (traçabilité) et
     * `/sdcard/Download`, d'où `scripts/run-qa.ps1 -ScreenshotPrefix "US20"` la rapatrie sans
     * `adb pull` manuel (même procédé que `ClientPickerGeometryInstrumentedTest`).
     */
    @Test
    fun exportsTheIntegrationsHubScreenshot() {
        render()

        composeRule.onNodeWithTag(IntegrationsHubTags.CONTAINER).assertIsDisplayed()
        IntegrationModule.entries.forEach { module ->
            composeRule.onNodeWithTag(IntegrationsHubTags.card(module)).assertIsDisplayed()
            composeRule.onNodeWithTag(IntegrationsHubTags.badge(module), useUnmergedTree = true)
                .assertIsDisplayed()
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(IntegrationsHubTags.CONTAINER)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFile = File(context.getExternalFilesDir(null), screenshotName)
        appFile.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        val sharedFile = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)!!,
            screenshotName,
        ).apply { parentFile?.mkdirs() }
        runCatching {
            sharedFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        assertTrue(appFile.exists(), "Capture US-20 non écrite : ${appFile.absolutePath}")
        assertTrue(appFile.length() > 0, "Capture US-20 vide : ${appFile.absolutePath}")
    }
}
