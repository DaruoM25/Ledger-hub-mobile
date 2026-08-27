package com.ledgerhub.domain.invoice

/** Abstraction de la persistance/backend — la couche présentation ne connaît que ce contrat. */
interface InvoiceRepository {
    suspend fun submitInvoice(invoice: Invoice): Result<Unit>

    /** Liste des factures existantes. */
    suspend fun fetchInvoices(): Result<List<Invoice>>
}
