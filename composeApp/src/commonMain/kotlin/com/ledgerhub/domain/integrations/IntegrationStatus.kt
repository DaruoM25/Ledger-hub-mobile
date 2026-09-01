package com.ledgerhub.domain.integrations

import com.ledgerhub.domain.i18n.StringKey

/**
 * Degré d'ouverture d'un module d'intégration (US-20).
 *
 * Deux valeurs seulement, et aucune troisième « disponible » : à ce stade **aucun connecteur n'est
 * branché**. Déclarer un statut ouvert qu'aucun module ne porte reviendrait à laisser le modèle
 * promettre ce que le produit ne fait pas — et le premier module réellement livré n'aurait plus
 * qu'à l'utiliser sans que rien ne l'y oblige.
 *
 * Le statut porte sa propre clé de badge : la couleur relève de la présentation, mais le **texte**
 * qui qualifie l'ouverture relève du domaine, au même titre que les libellés de statut de facture.
 */
enum class IntegrationStatus(val badgeKey: StringKey) {
    /** Accès anticipé — le module existe, il n'est pas encore ouvert à tous. */
    BETA(StringKey.INTEGRATION_BADGE_BETA),

    /** Annoncé, pas encore développé. */
    COMING_SOON(StringKey.INTEGRATION_BADGE_COMING_SOON),
}
