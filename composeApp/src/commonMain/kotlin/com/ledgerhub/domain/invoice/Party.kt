package com.ledgerhub.domain.invoice

/** Émetteur ou destinataire d'une facture. */
data class Party(
    val name: String,
    val siren: String,
    val siret: String,
)
