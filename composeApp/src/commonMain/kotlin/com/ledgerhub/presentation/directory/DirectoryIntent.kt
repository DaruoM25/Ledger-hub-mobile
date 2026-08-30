package com.ledgerhub.presentation.directory

/** Intentions de l'écran Annuaire DGFIP (UDF). */
sealed interface DirectoryIntent {
    /** L'utilisateur modifie la saisie SIREN/SIRET — déclenche le feedback Luhn temps réel. */
    data class QueryChanged(val value: String) : DirectoryIntent

    /** L'utilisateur lance la résolution de l'identifiant courant. */
    data object Search : DirectoryIntent

    /** L'UI a consommé le message d'erreur courant. */
    data object MessageShown : DirectoryIntent
}
