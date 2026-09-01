package com.ledgerhub.presentation.command

import com.ledgerhub.domain.command.CommandAction

/** Intentions de la palette de commandes (UDF/MVI) — US-19. */
sealed interface CommandPaletteIntent {
    /** Ouverture, par le déclencheur de l'en-tête ou par le raccourci clavier. */
    data object Open : CommandPaletteIntent

    /** Fermeture : clic en dehors, touche Retour, ou action exécutée. */
    data object Close : CommandPaletteIntent

    /** Frappe dans le champ de recherche — refiltre la liste à chaque caractère. */
    data class UpdateQuery(val value: String) : CommandPaletteIntent

    /** L'utilisateur choisit une action. La palette se ferme et publie son choix. */
    data class ExecuteAction(val action: CommandAction) : CommandPaletteIntent

    /**
     * Le shell a pris en charge l'action publiée. Sans cet accusé, l'action resterait dans l'état
     * et se rejouerait à la moindre recomposition.
     */
    data object ActionConsumed : CommandPaletteIntent
}
