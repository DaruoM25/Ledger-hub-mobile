package com.ledgerhub.presentation.command

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
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.command.CommandAction
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-19) — palette de commandes au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que le déclencheur et les lignes d'action offrent une
 * cible tactile décente, que la modale se ferme réellement au toucher du voile, et que la saisie
 * passe par le clavier logiciel de l'appareil. S'y ajoute l'export de la capture QA.
 *
 * Le raccourci clavier n'est **pas** testé ici : l'émulateur n'a pas de clavier matériel. Sa règle
 * est couverte en niveau 1 par `CommandPaletteShortcutTest`, ce pour quoi elle a été extraite de la
 * composition.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CommandPaletteInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US19_mobile_command_palette_sdk_gphone64_x86_64.png"

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    /** Déclencheur et palette reliés par un ViewModel réel — le câblage du shell, à l'échelle du test. */
    @Composable
    private fun PaletteHost(onExecuted: (CommandAction) -> Unit = {}) {
        val viewModel = remember { CommandPaletteViewModel() }
        val state by viewModel.uiState.collectAsState()

        CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
            Box(modifier = Modifier.fillMaxSize()) {
                CommandPaletteTrigger(
                    onClick = { viewModel.processIntent(CommandPaletteIntent.Open, AppLanguage.FR) },
                )
                CommandPalette(
                    uiState = state,
                    onIntent = { intent ->
                        if (intent is CommandPaletteIntent.ExecuteAction) onExecuted(intent.action)
                        viewModel.processIntent(intent, AppLanguage.FR)
                    },
                )
            }
        }
    }

    private fun render(onExecuted: (CommandAction) -> Unit = {}) {
        composeRule.setContent { PaletteHost(onExecuted) }
        composeRule.waitForIdle()
    }

    private fun openPalette() {
        composeRule.onNodeWithTag(CommandPaletteTags.TRIGGER).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    // ── Parcours tactile ────────────────────────────────────────────────────

    @Test
    fun tappingTheTrigger_opensThePaletteOnDevice() {
        render()

        composeRule.onNodeWithTag(CommandPaletteTags.TRIGGER).assertIsDisplayed()
        openPalette()

        composeRule.onNodeWithTag(CommandPaletteTags.DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText(tr(StringKey.COMMAND_PALETTE_PLACEHOLDER)).assertIsDisplayed()
    }

    @Test
    fun typingThenTappingAnAction_publishesIt_andClosesThePalette() {
        var executed: CommandAction? = null
        render(onExecuted = { executed = it })
        openPalette()

        composeRule.onNodeWithTag(CommandPaletteTags.INPUT).performTextInput("retard")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CommandPaletteTags.action(CommandAction.REMIND_OVERDUE))
            .assertIsDisplayed()
            .performTouchInput { click() }

        // La fermeture passe par une recomposition : on attend la disparition du noeud plutot que
        // de la supposer acquise a la milliseconde du toucher.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CommandPaletteTags.DIALOG).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(CommandAction.REMIND_OVERDUE, executed)
    }

    /** Accessibilité tactile : le déclencheur et les lignes tiennent la cible minimale. */
    @Test
    fun theTriggerAndActions_meetTheMinimumTouchTargetHeight() {
        render()

        composeRule.onNodeWithTag(CommandPaletteTags.TRIGGER).assertHeightIsAtLeast(40.dp)
        openPalette()

        CommandAction.entries.forEach { action ->
            composeRule.onNodeWithTag(CommandPaletteTags.action(action))
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    /** Le voile ferme la palette : c'est le geste attendu de toute modale flottante. */
    @Test
    fun tappingOutside_dismissesThePalette() {
        render()
        openPalette()

        composeRule.onNodeWithTag(CommandPaletteTags.DIALOG).assertIsDisplayed()
        // Un toucher en haut de l'ecran tombe sur le voile, la palette etant centree.
        composeRule.onNodeWithTag(CommandPaletteTags.DIALOG).performTouchInput { click(topLeft) }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CommandPaletteTags.DIALOG).fetchSemanticsNodes().isEmpty()
        }
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-19 : la palette ouverte, ses trois actions rapides visibles et le
     * champ de recherche en évidence — ce que l'écran doit démontrer.
     *
     * Export sous `US19_mobile_command_palette_sdk_gphone64_x86_64.png`, à rapatrier dans
     * `screenshots/` aux côtés des captures US-10 à US-18.
     */
    @Test
    fun exportsTheCommandPaletteScreenshot() {
        render()
        openPalette()

        composeRule.onNodeWithTag(CommandPaletteTags.DIALOG).assertIsDisplayed()
        CommandAction.entries.forEach { action ->
            composeRule.onNodeWithTag(CommandPaletteTags.action(action)).assertIsDisplayed()
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(CommandPaletteTags.DIALOG)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), screenshotName)
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-19 non écrite : ${output.absolutePath}")
        assertTrue(output.length() > 0, "Capture US-19 vide : ${output.absolutePath}")
    }
}
