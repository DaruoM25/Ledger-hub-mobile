package com.ledgerhub.data.repository

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.repository.LedgerRepository

/**
 * Adapte le [InvoiceRepository] local (persistance SQLDelight, écrit par le formulaire de saisie)
 * vers le contrat [LedgerRepository] attendu par les écrans liste/détail des factures.
 *
 * Colle ainsi la boucle complète en local : « Émettre et Persister » écrit via
 * [com.ledgerhub.data.invoice.SqlDelightInvoiceRepository], et la même instance est relue ici par
 * `InvoiceListScreen` / `InvoiceDetailScreen` (et par le tableau de bord). Tant qu'aucun backend
 * Ktor réel n'est branché, c'est cette implémentation qui est câblée dans `App.kt` ;
 * [com.ledgerhub.data.repository.LedgerRepositoryImpl] (Ktor) reste la cible distante finale.
 */
class LocalLedgerRepository(
    private val invoiceRepository: InvoiceRepository,
) : LedgerRepository {

    override suspend fun fetchInvoices(): Result<List<Invoice>> = invoiceRepository.fetchInvoices()

    override suspend fun getInvoiceDetail(number: String): Result<Invoice?> =
        invoiceRepository.fetchInvoices().map { invoices -> invoices.firstOrNull { it.number == number } }

    /** Persistance locale : toujours disponible, pas de round-trip réseau à sonder. */
    override suspend fun healthCheck(): Result<Unit> = Result.success(Unit)
}
