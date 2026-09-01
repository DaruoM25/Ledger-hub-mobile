package com.ledgerhub.presentation.command

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.command.CommandAction
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Niveau 3a (US-19) — parcours sémantique de la palette sous Robolectric (JVM, CI sans émulateur).
 *
 * Les règles de filtrage et d'état sont déjà verrouillées en niveau 1 : ce niveau vérifie que le
 * **geste** produit l'écran attendu — ouvrir, saisir, voir la liste se réduire, choisir une action.
 *
 * L'écran est relié à un vrai [CommandPaletteViewModel] : câbler un état figé ferait passer les
 * tests sur une interface qui ne réagit à rien.
 *
 * La taille de l'écran est déclarée en qualifiers Robolectric — l'appareil par défaut ne fait que
 * 320 dp, et la palette y serait à l'étroit.
 *
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w720dp-h1000dp")
@OptIn(ExperimentalTestApi::class)
class CommandPaletteRobolectricTest {

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    /**
     * Hôte de test : le déclencheur et la palette, reliés par un [CommandPaletteViewModel] réel —
     * le câblage du shell, réduit à ce que ce niveau doit éprouver. Un état figé ferait passer les
     * tests sur une interface qui ne réagit à rien.
     */
    @Composable
    private fun PaletteHost(
        language: AppLanguage = AppLanguage.FR,
        onExecuted: (CommandAction) -> Unit = {},
    ) {
        val viewModel = remember { CommandPaletteViewModel() }
        val state by viewModel.uiState.collectAsState()

        CompositionLocalProvider(LocalAppLanguage provides language) {
            CommandPaletteTrigger(
                onClick = { viewModel.processIntent(CommandPaletteIntent.Open, language) },
            )
            CommandPalette(
                uiState = state,
                onIntent = { intent ->
                    if (intent is CommandPaletteIntent.ExecuteAction) onExecuted(intent.action)
                    viewModel.processIntent(intent, language)
                },
            )
        }
    }

    // ── Déclencheur et ouverture ────────────────────────────────────────────

    @Test
    fun theTrigger_isDisplayedAndOpensThePalette() = runComposeUiTest {
        setContent { PaletteHost() }

        onNodeWithTag(CommandPaletteTags.TRIGGER).assertIsDisplayed()
        // Fermee, la modale n'existe pas dans l'arbre.
        onNodeWithTag(CommandPaletteTags.DIALOG).assertDoesNotExist()

        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.DIALOG).assertIsDisplayed()
        onNodeWithTag(CommandPaletteTags.INPUT).assertIsDisplayed()
    }

    @Test
    fun theOpenPalette_offersEveryQuickAction() = runComposeUiTest {
        setContent { PaletteHost() }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        CommandAction.entries.forEach { action ->
            onNodeWithTag(CommandPaletteTags.action(action)).assertIsDisplayed()
        }
        onNodeWithText(tr(StringKey.COMMAND_PALETTE_PLACEHOLDER)).assertIsDisplayed()
    }

    // ── Filtrage à la saisie ────────────────────────────────────────────────

    @Test
    fun typing_narrowsTheListToTheMatchingAction() = runComposeUiTest {
        setContent { PaletteHost() }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.INPUT).performTextInput("retard")
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.action(CommandAction.REMIND_OVERDUE)).assertIsDisplayed()
        onNodeWithTag(CommandPaletteTags.action(CommandAction.CREATE_INVOICE)).assertDoesNotExist()
        onNodeWithTag(CommandPaletteTags.action(CommandAction.EXPORT_ACCOUNTING)).assertDoesNotExist()
    }

    @Test
    fun anUnknownQuery_showsTheEmptyMessage() = runComposeUiTest {
        setContent { PaletteHost() }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.INPUT).performTextInput("zzzzz")
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.EMPTY).assertIsDisplayed()
        onNodeWithText(tr(StringKey.COMMAND_PALETTE_EMPTY)).assertIsDisplayed()
    }

    /** Corriger sa saisie doit rouvrir la liste : le filtrage suit la frappe dans les deux sens. */
    @Test
    fun clearingTheQuery_restoresEveryAction() = runComposeUiTest {
        setContent { PaletteHost() }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()
        onNodeWithTag(CommandPaletteTags.INPUT).performTextInput("retard")
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.INPUT).performTextReplacement("")
        waitForIdle()

        CommandAction.entries.forEach { action ->
            onNodeWithTag(CommandPaletteTags.action(action)).assertIsDisplayed()
        }
    }

    // ── Exécution d'une action ──────────────────────────────────────────────

    @Test
    fun clickingAnAction_publishesIt_andClosesThePalette() = runComposeUiTest {
        var executed: CommandAction? = null
        setContent { PaletteHost(onExecuted = { executed = it }) }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.action(CommandAction.CREATE_INVOICE)).performClick()
        waitForIdle()

        assertEquals(CommandAction.CREATE_INVOICE, executed)
        onNodeWithTag(CommandPaletteTags.DIALOG).assertDoesNotExist()
    }

    @Test
    fun theExportAction_isReachableAfterFiltering() = runComposeUiTest {
        var executed: CommandAction? = null
        setContent { PaletteHost(onExecuted = { executed = it }) }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()
        onNodeWithTag(CommandPaletteTags.INPUT).performTextInput("csv")
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.action(CommandAction.EXPORT_ACCOUNTING)).performClick()
        waitForIdle()

        assertEquals(CommandAction.EXPORT_ACCOUNTING, executed)
    }

    @Test
    fun noActionIsPublished_untilOneIsClicked() = runComposeUiTest {
        var executed: CommandAction? = null
        setContent { PaletteHost(onExecuted = { executed = it }) }

        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        assertNull(executed)
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_thePaletteIsFullyTranslated() = runComposeUiTest {
        setContent { PaletteHost(language = AppLanguage.EN) }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        onNodeWithText(tr(StringKey.COMMAND_PALETTE_PLACEHOLDER, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.COMMAND_ACTION_CREATE_INVOICE, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.COMMAND_ACTION_REMIND_OVERDUE, AppLanguage.EN)).assertIsDisplayed()
        onNodeWithText(tr(StringKey.COMMAND_ACTION_EXPORT_ACCOUNTING, AppLanguage.EN)).assertIsDisplayed()
    }

    /** En anglais, la recherche porte sur le vocabulaire anglais jusque dans l'interface. */
    @Test
    fun inEnglish_typingAnEnglishKeyword_filters() = runComposeUiTest {
        setContent { PaletteHost(language = AppLanguage.EN) }
        onNodeWithTag(CommandPaletteTags.TRIGGER).performClick()
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.INPUT).performTextInput("overdue")
        waitForIdle()

        onNodeWithTag(CommandPaletteTags.action(CommandAction.REMIND_OVERDUE)).assertIsDisplayed()
        onNodeWithTag(CommandPaletteTags.action(CommandAction.CREATE_INVOICE)).assertDoesNotExist()
    }
}
