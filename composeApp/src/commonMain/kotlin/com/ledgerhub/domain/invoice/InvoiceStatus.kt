package com.ledgerhub.domain.invoice

/**
 * Cycle de vie réglementaire d'une facture — référentiel DGFIP 2026.
 *
 * Remplace le cycle v1 (`VALIDATED` / `SENT`), dont les deux valeurs désignaient une facture
 * émise et en circulation : elles sont regroupées sous [DEPOSITED] par la migration `2.sqm`.
 *
 * Les transitions autorisées ne sont pas portées ici mais par [InvoiceStatusTransition] : un
 * statut décrit un état, pas les chemins qui y mènent.
 */
enum class InvoiceStatus {
    /** Brouillon — seul état modifiable et supprimable. */
    DRAFT,

    /**
     * Déposée sur le portail public de facturation ou une plateforme agréée — le `SUBMITTED`
     * du référentiel PPF. Le nom historique est conservé : il est persisté tel quel en base
     * (`Invoice.status TEXT`), le renommer imposerait une migration pour aucun gain.
     */
    DEPOSITED,

    /**
     * Approuvée par l'administration. Pendant favorable de [REJECTED] : le portail public de
     * facturation a accepté le flux, la facture est entrée dans le circuit légal et n'attend
     * plus que son encaissement.
     */
    APPROVED,

    /** Encaissée. */
    PAID,

    /**
     * Rejetée par la plateforme. La facture n'est **jamais entrée** dans le circuit légal :
     * sa correction puis son redépôt sont la procédure attendue, d'où le retour possible
     * à [DRAFT] — voir [InvoiceStatusTransition].
     */
    REJECTED,

    /** Refusée par l'acheteur. La facture a circulé : seul un avoir peut la corriger. */
    REFUSED,

    /** Annulée par un avoir. État terminal. */
    CANCELLED,

    /**
     * Émise en format de secours sous mode dégradé (US-29 / Continuité d'activité DGFiP 2026).
     * En attente de régularisation électronique (télétransmission vers DEPOSITED) dès rétablissement
     * de la connectivité avec le Portail Public de Facturation.
     */
    PENDING_REGULARIZATION;

    /** Aucun état n'est atteignable depuis un état terminal. */
    val isTerminal: Boolean get() = InvoiceStatusTransition.allowedFrom(this).isEmpty()
}
