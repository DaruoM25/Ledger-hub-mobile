package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.Party

/**
 * Identité fiscale de l'émetteur — le cabinet de l'utilisateur connecté. Fixe côté formulaire
 * (comme sur le Web : « Créer une Facture » ne saisit que le client, l'émetteur est implicite).
 * En dur tant que le profil entreprise n'est pas persisté ; SIREN/SIRET de démonstration valides
 * en longueur (9 / 14 chiffres) pour passer [com.ledgerhub.domain.invoice.FiscalValidation].
 */
internal object CabinetIdentity {
    val party: Party = Party(
        name = "Cabinet LedgerHub",
        siren = "820329331",
        siret = "82032933100027",
        email = "facturation@ledgerhub.app",
    )
}
