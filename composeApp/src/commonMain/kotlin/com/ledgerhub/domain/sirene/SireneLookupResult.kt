package com.ledgerhub.domain.sirene

/**
 * Issue d'une interrogation du répertoire SIRENE (US-21).
 *
 * Deux valeurs seulement : l'entreprise existe, ou elle n'existe pas. L'**indisponibilité du
 * service** n'en est délibérément pas une — c'est une exception, remontée telle quelle et captée
 * par le ViewModel. Un `Unavailable` dans le résultat forcerait chaque appelant à distinguer
 * « le répertoire a répondu que non » de « le répertoire n'a pas répondu », alors que seul le
 * second est un incident technique.
 */
sealed interface SireneLookupResult {

    /** Entreprise trouvée : sa raison sociale peut être reportée dans le formulaire. */
    data class Verified(val company: SireneCompany) : SireneLookupResult

    /** SIRET syntaxiquement exploitable, mais absent du répertoire. */
    data object NotFound : SireneLookupResult
}
