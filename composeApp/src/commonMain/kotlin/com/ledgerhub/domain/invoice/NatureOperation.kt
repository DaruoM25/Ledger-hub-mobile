package com.ledgerhub.domain.invoice

/**
 * Catégorie de transaction au sens de la réforme fiscale DGFiP 2026 / Factur-X.
 * Mention légale obligatoire définissant la nature des biens ou services facturés.
 */
enum class NatureOperation(val label: String) {
    LIVRAISON_BIENS("Livraison de biens"),
    PRESTATION_SERVICES("Prestation de services"),
    MIXTE("Mixte");
}
