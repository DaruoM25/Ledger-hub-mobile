package com.ledgerhub.domain.invoice

/**
 * Mode de transaction réglementaire 2026.
 * - [E_INVOICING] : B2B France (facturation électronique obligatoire, SIREN/SIRET requis).
 * - [E_REPORTING] : B2C / International (e-Reporting de transaction, SIREN optionnel).
 */
enum class TransactionMode {
    E_INVOICING,
    E_REPORTING,
}
