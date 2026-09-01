package com.ledgerhub.presentation.command

import com.ledgerhub.domain.command.CommandAction
import com.ledgerhub.domain.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-19) — état de la palette de commandes.
 *
 * Le filtrage lui-même est verrouillé par `CommandActionFilterTest` : ce niveau vérifie ce qui
 * appartient en propre au ViewModel — l'ouverture, la fermeture, la publication de l'action choisie
 * et son acquittement.
 */
class CommandPaletteViewModelTest {

    private fun viewModel() = CommandPaletteViewModel()

    // ── Ouverture et fermeture ──────────────────────────────────────────────

    @Test
    fun thePaletteStartsClosed() {
        assertFalse(viewModel().uiState.value.isOpen)
    }

    @Test
    fun open_showsEveryActionOnAnEmptyQuery() {
        val subject = viewModel()

        subject.processIntent(CommandPaletteIntent.Open)

        val state = subject.uiState.value
        assertTrue(state.isOpen)
        assertEquals("", state.query)
        assertEquals(CommandAction.entries.toList(), state.results)
    }

    /**
     * Rouvrir la palette repart d'une requête vierge : conserver la recherche précédente
     * obligerait à effacer avant chaque nouvelle saisie.
     */
    @Test
    fun reopening_startsFromAFreshQuery() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open)
        subject.processIntent(CommandPaletteIntent.UpdateQuery("export"))
        subject.processIntent(CommandPaletteIntent.Close)

        subject.processIntent(CommandPaletteIntent.Open)

        assertEquals("", subject.uiState.value.query)
        assertEquals(CommandAction.entries.toList(), subject.uiState.value.results)
    }

    @Test
    fun close_hidesThePaletteAndClearsTheQuery() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open)
        subject.processIntent(CommandPaletteIntent.UpdateQuery("retard"))

        subject.processIntent(CommandPaletteIntent.Close)

        assertFalse(subject.uiState.value.isOpen)
        assertEquals("", subject.uiState.value.query)
    }

    // ── Filtrage à la frappe ────────────────────────────────────────────────

    @Test
    fun typing_narrowsTheResults() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open)

        subject.processIntent(CommandPaletteIntent.UpdateQuery("retard"))

        assertEquals(listOf(CommandAction.REMIND_OVERDUE), subject.uiState.value.results)
        assertEquals("retard", subject.uiState.value.query)
    }

    @Test
    fun anUnknownQuery_reportsNoMatch() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open)

        subject.processIntent(CommandPaletteIntent.UpdateQuery("zzzzz"))

        assertTrue(subject.uiState.value.hasNoMatch)
    }

    /** La langue est passée à chaque intention : la palette suit un changement sans être recréée. */
    @Test
    fun theQueryIsFilteredInTheGivenLanguage() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open, AppLanguage.EN)

        subject.processIntent(CommandPaletteIntent.UpdateQuery("overdue"), AppLanguage.EN)

        assertEquals(listOf(CommandAction.REMIND_OVERDUE), subject.uiState.value.results)
    }

    // ── Exécution d'une action ──────────────────────────────────────────────

    /** Choisir une action ferme la palette : la laisser ouverte derrière l'écran d'arrivée serait déroutant. */
    @Test
    fun executingAnAction_closesThePalette_andPublishesTheChoice() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open)
        subject.processIntent(CommandPaletteIntent.UpdateQuery("export"))

        subject.processIntent(CommandPaletteIntent.ExecuteAction(CommandAction.EXPORT_ACCOUNTING))

        val state = subject.uiState.value
        assertFalse(state.isOpen)
        assertEquals("", state.query)
        assertEquals(CommandAction.EXPORT_ACCOUNTING, state.executedAction)
    }

    /**
     * Sans acquittement, l'action resterait dans l'état et le shell la rejouerait à chaque
     * recomposition — un export comptable relancé en boucle, par exemple.
     */
    @Test
    fun theExecutedAction_isClearedOnceConsumed() {
        val subject = viewModel()
        subject.processIntent(CommandPaletteIntent.Open)
        subject.processIntent(CommandPaletteIntent.ExecuteAction(CommandAction.CREATE_INVOICE))

        subject.processIntent(CommandPaletteIntent.ActionConsumed)

        assertNull(subject.uiState.value.executedAction)
    }

    @Test
    fun everyAction_canBeExecuted() {
        CommandAction.entries.forEach { action ->
            val subject = viewModel()
            subject.processIntent(CommandPaletteIntent.Open)

            subject.processIntent(CommandPaletteIntent.ExecuteAction(action))

            assertEquals(action, subject.uiState.value.executedAction, "Action non publiée : $action")
        }
    }
}
