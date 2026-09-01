package com.ledgerhub.domain.integrations

/**
 * Lecture du catalogue d'intégrations (US-20) — fonctions **pures**, sans état ni composition.
 *
 * L'ordre d'affichage n'est pas une affaire de présentation : il dit à l'utilisateur ce qui est
 * le plus proche de lui être livré. Il est donc décidé ici, où il se teste, et non dans un
 * `sortedBy` perdu au milieu d'un composable.
 */
object IntegrationCatalog {

    /** Tous les modules, dans l'ordre de déclaration. */
    fun all(): List<IntegrationModule> = IntegrationModule.entries.toList()

    /** Les modules d'un statut donné, ordre de déclaration préservé. */
    fun withStatus(status: IntegrationStatus): List<IntegrationModule> =
        IntegrationModule.entries.filter { it.status == status }

    /**
     * Ordre d'affichage du hub : ce qui est déjà approchable d'abord, ce qui reste à venir ensuite.
     *
     * Construit **à partir de** [withStatus] : le filtrage n'est pas une commodité écrite pour les
     * tests, c'est ce sur quoi repose l'écran.
     */
    fun ordered(): List<IntegrationModule> =
        withStatus(IntegrationStatus.BETA) + withStatus(IntegrationStatus.COMING_SOON)
}
