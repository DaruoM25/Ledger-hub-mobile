package com.ledgerhub.presentation.invoiceform

/**
 * Identifie un champ d'en-tête du formulaire pour lui associer un message d'erreur.
 * Parité Web : une seule partie saisie (le client) ; l'émetteur est l'identité fixe du cabinet
 * (voir [CabinetIdentity]).
 */
enum class InvoiceFormField {
    CLIENT_NAME,
    CLIENT_SIRET,
    CLIENT_SIREN,
    CLIENT_EMAIL,
    INVOICE_NUMBER,
    ISSUE_DATE,
    DUE_DATE,
    DELIVERY_STREET,
    DELIVERY_ZIP,
    DELIVERY_CITY,
    DELIVERY_COUNTRY,
    // Les erreurs de ligne (libellé/quantité/prix) vivent dans InvoiceLineFormState.errors (par ligne).
}
