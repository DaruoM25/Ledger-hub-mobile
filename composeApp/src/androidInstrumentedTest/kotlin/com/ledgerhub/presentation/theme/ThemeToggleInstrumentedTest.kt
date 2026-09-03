package com.ledgerhub.presentation.theme

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ledgerhub.App
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.components.LangToggleTags
import com.ledgerhub.presentation.components.ThemeToggleTags
import com.ledgerhub.presentation.dashboard.DashboardTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-25) — la bascule de thème au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que le bouton **tient** dans l'en-tête d'un vrai
 * téléphone à côté des quatre commandes qui l'y précédaient, qu'il offre une cible tactile
 * décente, et que l'appui du doigt — et non un `performClick` synthétique — repeint l'application.
 * S'y ajoute l'export de la capture QA officielle.
 *
 * L'app démarre en sombre (`ThemeMode.Default`, base vierge) : le premier appui l'amène en clair,
 * et c'est cet état qui est photographié.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ThemeToggleInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US25_mobile_theme_toggle_sdk_gphone64_x86_64.png"

    private fun tr(key: StringKey) = AppTranslations.get(key, AppLanguage.FR)

    /** Base en mémoire (`name = null`) : aucune préférence persistée, l'app part donc en sombre. */
    private fun newDatabase(): LedgerHubDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return LedgerHubDatabase(AndroidSqliteDriver(LedgerHubDatabase.Schema, context, name = null))
    }

    /** Monte le shell complet et attend que le tableau de bord soit à l'écran. */
    private fun renderShell() {
        val database = newDatabase()
        composeRule.setContent { App(database = database) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(DashboardTags.SCREEN).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tapToggle() {
        composeRule.onNodeWithTag(ThemeToggleTags.ROOT).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    // ── Place dans l'en-tête et cible tactile ───────────────────────────────

    /**
     * **Le risque de l'US, affirmé plutôt qu'estimé.** La bascule est la cinquième commande de
     * l'en-tête sur les 393 dp d'un Pixel 5. Le sélecteur de langue est celui qui serait poussé
     * dehors le premier : les deux doivent être visibles ensemble, sur l'appareil réel.
     */
    @Test
    fun theHeader_showsTheThemeToggleAndTheLanguageSelectorTogether() {
        renderShell()

        composeRule.onNodeWithTag(ThemeToggleTags.ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(LangToggleTags.ROOT).assertIsDisplayed()
    }

    /** Un bouton d'en-tête doit se viser au doigt, pas au stylet. */
    @Test
    fun theToggle_meetsTheMinimumTouchTarget() {
        renderShell()

        composeRule.onNodeWithTag(ThemeToggleTags.ROOT).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(ThemeToggleTags.ROOT).assertWidthIsAtLeast(48.dp)
    }

    // ── Le geste réel ───────────────────────────────────────────────────────

    /**
     * L'appui du doigt bascule le thème : le bouton annonce désormais le chemin inverse. C'est la
     * preuve observable côté appareil ; que la palette Material change de valeurs est affirmé par
     * `ThemeToggleRobolectricTest`, qui peut sonder l'intérieur du thème.
     */
    @Test
    fun tappingTheToggle_switchesTheThemeOnDevice() {
        renderShell()

        composeRule.onNodeWithTag(ThemeToggleTags.ICON_SUN, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ThemeToggleTags.ROOT)
            .assertContentDescriptionContains(tr(StringKey.THEME_TOGGLE_TO_LIGHT))

        tapToggle()

        composeRule.onNodeWithTag(ThemeToggleTags.ICON_MOON, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ThemeToggleTags.ROOT)
            .assertContentDescriptionContains(tr(StringKey.THEME_TOGGLE_TO_DARK))
    }

    /** Aller-retour au doigt : le shell revient au sombre sans rien perdre de son en-tête. */
    @Test
    fun tappingTwice_returnsToTheDarkThemeOnDevice() {
        renderShell()

        tapToggle()
        tapToggle()

        composeRule.onNodeWithTag(ThemeToggleTags.ICON_SUN, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LangToggleTags.ROOT).assertIsDisplayed()
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-25, cadrée sur **la racine du shell en thème clair**.
     *
     * Écart assumé avec les US-20 à US-24, qui cadrent sur le composant : photographier un bouton
     * de 48 dp ne prouverait rien. Ce que l'US doit démontrer, c'est l'application **repeinte** —
     * en-tête (bouton compris) et tableau de bord sur fond clair. Le composant seul serait ici le
     * détail, et l'écran la preuve.
     *
     * Écrite **aux deux emplacements** : les fichiers de l'application (traçabilité) et
     * `/sdcard/Download`, d'où `scripts/run-qa.ps1 -ScreenshotPrefix "US25"` la rapatrie sans
     * `adb pull` manuel (même procédé que les US-20 à US-24).
     */
    @Test
    fun exportsTheLightThemeShellScreenshot() {
        renderShell()

        tapToggle()
        composeRule.onNodeWithTag(ThemeToggleTags.ICON_MOON, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.SCREEN).assertIsDisplayed()
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFile = File(context.getExternalFilesDir(null), screenshotName)
        appFile.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        val sharedFile = File("/sdcard/Download", screenshotName).apply { parentFile?.mkdirs() }
        runCatching {
            sharedFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        assertTrue(appFile.exists(), "Capture US-25 non écrite : ${appFile.absolutePath}")
        assertTrue(appFile.length() > 0, "Capture US-25 vide : ${appFile.absolutePath}")
    }
}
