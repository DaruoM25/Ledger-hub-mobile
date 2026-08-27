package com.ledgerhub.domain.repository

import com.ledgerhub.domain.invoice.Invoice

/**
 * Contrat d'accès à l'API Ledger distante (Sprint 1 — US-01) — distinct de
 * [com.ledgerhub.domain.invoice.InvoiceRepository], qui persiste localement via SQLDelight. Ce
 * repository lit depuis le backend de dev local (voir `ledgerApiBaseUrl` dans
 * [com.ledgerhub.data.remote.HttpClientFactory]), jamais un service cloud (pivot 100% local).
 */
interface LedgerRepository {
    /** Liste des factures existantes côté serveur. */
    suspend fun fetchInvoices(): Result<List<Invoice>>

    /** Détail d'une facture par son numéro — succès avec `null` si elle n'existe pas côté serveur (404). */
    suspend fun getInvoiceDetail(number: String): Result<Invoice?>

    /** Vérifie que le backend local répond — sert à diagnostiquer une config réseau cassée avant tout appel métier. */
    suspend fun healthCheck(): Result<Unit>
}
