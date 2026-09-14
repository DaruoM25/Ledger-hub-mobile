package com.ledgerhub.domain.vault

/**
 * Type de document déposé dans le coffre-fort numérique.
 */
enum class VaultDocumentType(val label: String) {
    INVOICE_FACTURX("Facture Factur-X"),
    CREDIT_NOTE("Avoir de correction"),
    ACCOUNTING_EXPORT("Export comptable FEC");
}

/**
 * Statut d'intégrité et de scellement d'une archive numérique.
 */
enum class SealStatus {
    SEALED,
    CORRUPTED,
    REVOKED;
}

/**
 * Archive numérique scellée en coffre-fort électronique.
 */
data class DigitalArchive(
    val id: String,
    val invoiceNumber: String,
    val documentType: VaultDocumentType = VaultDocumentType.INVOICE_FACTURX,
    val payloadHash: String,
    val archiveSize: Long,
    val sealedAt: String,
    val sealedBy: String = "SYSTEM",
    val status: SealStatus = SealStatus.SEALED,
)

/**
 * Entrée chaînée de la Piste d'Audit Fiable (PAF).
 */
data class PisteAuditEntry(
    val id: String,
    val invoiceNumber: String?,
    val action: String,
    val details: String,
    val timestamp: String,
    val previousChecksum: String?,
    val checksum: String,
)

/**
 * Résultat du diagnostic d'intégrité global du coffre-fort numérique.
 */
data class VaultIntegrityReport(
    val totalDocuments: Int,
    val verifiedDocuments: Int,
    val corruptedDocuments: List<String> = emptyList(),
    val isAuditChainValid: Boolean = true,
    val brokenChainIndex: Int? = null,
    val verifiedAt: String,
) {
    val isCompletelyValid: Boolean
        get() = corruptedDocuments.isEmpty() && isAuditChainValid && totalDocuments >= 0

    val integrityPercentage: Int
        get() = if (totalDocuments == 0) 100 else (((totalDocuments - corruptedDocuments.size).toDouble() / totalDocuments) * 100).toInt()
}
