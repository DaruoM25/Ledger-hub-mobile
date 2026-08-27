package com.ledgerhub.presentation.invoiceform

/** Identifie un champ du formulaire pour lui associer un message d'erreur. */
enum class InvoiceFormField {
    INVOICE_NUMBER,
    ISSUE_DATE,
    ISSUER_NAME,
    ISSUER_SIREN,
    ISSUER_SIRET,
    RECIPIENT_NAME,
    RECIPIENT_SIREN,
    RECIPIENT_SIRET,
    // Les erreurs de ligne (libellé/quantité/prix) vivent désormais dans
    // InvoiceLineFormState.errors (par ligne), pas ici (en-tête).
}
