package com.ledgerhub.domain.directory

/** Mode de routage d'une entreprise dans l'écosystème de facturation électronique 2026. */
enum class RoutingMode {
    /** Portail Public de Facturation — voie par défaut. */
    PPF,

    /** Plateforme de Dématérialisation Partenaire — impose un identifiant de PDP. */
    PDP,
}

/** État de l'entreprise au registre de l'annuaire. */
enum class DirectoryStatus {
    ACTIVE,
    INACTIVE,
    UNKNOWN,
}

/** Nature d'un identifiant saisi dans la barre de recherche. */
enum class IdentifierKind {
    SIREN,
    SIRET,
    UNKNOWN,
}

/**
 * Fiche d'annuaire résolue pour une entreprise — cache local d'une entrée qui, en production,
 * proviendrait de l'annuaire centralisé DGFIP/PPF.
 *
 * @param siren identifiant racine (9 chiffres) — clé primaire du cache.
 * @param siret établissement concerné, si la résolution s'est faite au niveau établissement.
 * @param vatNumber numéro de TVA intracommunautaire certifié, `null` si l'entreprise n'est pas
 *   assujettie (franchise en base).
 * @param routingMode voie de routage ([RoutingMode.PPF] par défaut, [RoutingMode.PDP] sinon).
 * @param pdpIdentifier identifiant de la PDP destinataire — renseigné uniquement si
 *   [routingMode] vaut [RoutingMode.PDP].
 * @param lastSyncAt horodatage ISO 8601 UTC de la dernière synchronisation du cache.
 */
data class DirectoryEntry(
    val siren: String,
    val siret: String?,
    val companyName: String,
    val vatNumber: String?,
    val routingMode: RoutingMode,
    val pdpIdentifier: String?,
    val isVatSubject: Boolean,
    val status: DirectoryStatus,
    val lastSyncAt: String,
)

/** Résultat d'une résolution d'annuaire. */
sealed interface DirectoryLookupResult {
    /** L'identifiant saisi ne respecte pas la clé de Luhn / n'a pas une longueur exploitable. */
    data object InvalidChecksum : DirectoryLookupResult

    /** Identifiant valide mais absent de l'annuaire. */
    data object NotFound : DirectoryLookupResult

    /** Fiche trouvée. */
    data class Resolved(val entry: DirectoryEntry) : DirectoryLookupResult
}
