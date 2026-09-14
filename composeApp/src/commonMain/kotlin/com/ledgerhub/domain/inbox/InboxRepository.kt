package com.ledgerhub.domain.inbox

/**
 * Contrat du repository de stockage des factures fournisseurs reçues.
 */
interface InboxRepository {
    /** Enregistre ou met à jour une facture reçue. */
    suspend fun saveReceivedInvoice(invoice: ReceivedInvoice): Result<Unit>

    /** Récupère toutes les factures reçues. */
    suspend fun fetchReceivedInvoices(): Result<List<ReceivedInvoice>>

    /** Récupère une facture par son identifiant. */
    suspend fun getReceivedInvoiceById(id: String): Result<ReceivedInvoice?>

    /** Recherche une facture existante par empreinte de fichier SHA-256. */
    suspend fun findByFileHash(fileHash: String): Result<ReceivedInvoice?>

    /** Recherche une facture existante sur le triptyque (SIREN fournisseur, n° facture, total TTC en centimes). */
    suspend fun findDuplicateTriptych(
        supplierSiren: String,
        invoiceNumber: String,
        totalTtcCents: Long,
        excludeId: String = "",
    ): Result<ReceivedInvoice?>

    /** Met à jour le statut d'une facture reçue. */
    suspend fun updateStatus(id: String, status: ReceivedInvoiceStatus, duplicateReason: String? = null): Result<Unit>
}
