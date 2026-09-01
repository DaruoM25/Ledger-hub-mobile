package com.ledgerhub.presentation.command

import com.ledgerhub.domain.command.CommandAction

/**
 * État immuable de la palette de commandes (US-19).
 *
 * [results] n'est pas recalculé ici mais posé par le ViewModel à chaque frappe : le filtrage
 * dépend de la langue courante, que l'état ne connaît pas — et une propriété dérivée qui aurait
 * besoin d'un paramètre n'en est plus une.
 *
 * @param executedAction action choisie, en attente de prise en charge par le shell. Remise à
 *   `null` par [CommandPaletteIntent.ActionConsumed].
 */
data class CommandPaletteUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<CommandAction> = CommandAction.entries.toList(),
    val executedAction: CommandAction? = null,
) {
    /** Aucune correspondance : à distinguer d'une palette qui vient de s'ouvrir, encore vierge. */
    val hasNoMatch: Boolean get() = results.isEmpty()
}
