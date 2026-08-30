package com.ledgerhub.presentation.directory

import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.IdentifierKind

/**
 * État immuable de l'écran Annuaire DGFIP — pattern UDF.
 *
 * @param query saisie brute (chiffres, espaces éventuels).
 * @param identifierKind nature déduite de la longueur des chiffres saisis.
 * @param luhnValid `null` tant que la saisie ne fait pas 9 ou 14 chiffres ; sinon résultat du
 *   contrôle de clé de Luhn — c'est le feedback temps réel.
 * @param resolved fiche résolue après une recherche réussie.
 * @param notFound `true` si la dernière recherche portait sur un identifiant valide mais absent.
 */
data class DirectoryUiState(
    val query: String = "",
    val identifierKind: IdentifierKind = IdentifierKind.UNKNOWN,
    val luhnValid: Boolean? = null,
    val isSearching: Boolean = false,
    val resolved: DirectoryEntry? = null,
    val notFound: Boolean = false,
    val errorMessage: String? = null,
) {
    /** La recherche n'est proposée que sur un identifiant dont la clé de Luhn est correcte. */
    val isSearchEnabled: Boolean get() = luhnValid == true && !isSearching
}
