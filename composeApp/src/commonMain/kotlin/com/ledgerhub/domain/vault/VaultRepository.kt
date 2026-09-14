package com.ledgerhub.domain.vault

/**
 * Contrat du repository de gestion du coffre-fort numérique et de la piste d'audit chaînée.
 */
interface VaultRepository {
    /** Enregistre ou met à jour une archive numérique scellée. */
    suspend fun saveArchive(archive: DigitalArchive): Result<Unit>

    /** Récupère toutes les archives scellées. */
    suspend fun fetchArchives(): Result<List<DigitalArchive>>

    /** Récupère une archive par son numéro de facture. */
    suspend fun getArchiveByInvoiceNumber(invoiceNumber: String): Result<DigitalArchive?>

    /** Compte le nombre total de documents scellés. */
    suspend fun countSealedDocuments(): Result<Long>

    /** Ajoute une entrée chaînée à la Piste d'Audit Fiable. */
    suspend fun appendAuditEntry(
        id: String,
        invoiceNumber: String?,
        action: String,
        details: String,
        timestamp: String,
    ): Result<PisteAuditEntry>

    /** Récupère l'ensemble de la piste d'audit dans l'ordre chronologique strict. */
    suspend fun fetchAuditTrail(): Result<List<PisteAuditEntry>>

    /** Récupère la dernière entrée de la piste d'audit pour chaînage. */
    suspend fun getLatestAuditEntry(): Result<PisteAuditEntry?>
}
