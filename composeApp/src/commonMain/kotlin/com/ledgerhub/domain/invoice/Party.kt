package com.ledgerhub.domain.invoice

/**
 * Émetteur ou destinataire d'une facture.
 *
 * [email] : coordonnée de contact du client — mention attendue par le format Factur-X 2026 pour
 * l'acheminement de la facture électronique. Optionnel (défaut vide) pour ne pas casser les
 * call-sites hérités ; le formulaire de saisie l'exige désormais côté destinataire.
 */
data class Party(
    val name: String,
    val siren: String,
    val siret: String,
    val email: String = "",
)
