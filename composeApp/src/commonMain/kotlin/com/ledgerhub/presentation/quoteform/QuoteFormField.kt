package com.ledgerhub.presentation.quoteform

/** Identifie un champ du formulaire pour lui associer un message d'erreur. */
enum class QuoteFormField {
    QUOTE_NUMBER,
    ISSUE_DATE,
    VALIDITY_DATE,
    ISSUER_NAME,
    ISSUER_SIREN,
    ISSUER_SIRET,
    RECIPIENT_NAME,
    RECIPIENT_SIREN,
    RECIPIENT_SIRET,
    // Les erreurs de ligne (libellé/quantité/prix) vivent dans
    // QuoteLineFormState.errors (par ligne), pas ici (en-tête).
}
