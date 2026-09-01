package com.ledgerhub.domain.sirene

/**
 * Fiche d'entreprise telle que la renverrait le répertoire SIRENE de l'INSEE (US-21).
 *
 * Volontairement réduite à ce que l'inscription consomme : le SIRET interrogé et la raison
 * sociale à reporter dans le formulaire. [legalForm] n'est là que pour l'affichage de confort —
 * tout le reste (adresse, code APE, effectifs) serait du champ transporté que personne ne lit.
 *
 * Distincte de [com.ledgerhub.domain.directory.DirectoryEntry], qui décrit une entrée de
 * l'annuaire **DGFIP/PPF** (routage PPF/PDP, TVA intracommunautaire, statut d'assujettissement).
 * Les deux répertoires répondent à des questions différentes : « où adresser une facture » pour
 * l'un, « qui est cette entreprise » pour l'autre. Les confondre ferait dépendre l'inscription
 * d'un modèle de facturation électronique qui n'a rien à y faire.
 */
data class SireneCompany(
    val siret: String,
    val companyName: String,
    val legalForm: String? = null,
)
