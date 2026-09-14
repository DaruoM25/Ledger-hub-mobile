package com.ledgerhub.domain.vault

import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.audit.sha256Hex
import com.ledgerhub.domain.invoice.InvoiceRepository

/**
 * Cas d'utilisation de vérification complète de l'intégrité du coffre-fort numérique.
 *
 * Exécute deux vérifications cryptographiques :
 * 1. Intégrité des pièces : recalcule le hash de chaque facture locale et le compare avec l'archive scellée.
 * 2. Intégrité du chaînage de la PAF : vérifie chaque maillon récursif SHA-256.
 */
class VerifyVaultIntegrityUseCase(
    private val vaultRepository: VaultRepository,
    private val invoiceRepository: InvoiceRepository,
) {
    suspend operator fun invoke(
        verificationTimestampIso: String = "2026-09-14T12:00:00Z",
    ): Result<VaultIntegrityReport> = runCatching {
        val archives = vaultRepository.fetchArchives().getOrThrow()
        val invoices = invoiceRepository.fetchInvoices().getOrThrow().associateBy { it.number }
        val auditTrail = vaultRepository.fetchAuditTrail().getOrThrow()

        val corruptedDocuments = mutableListOf<String>()

        // 1. Contrôle d'intégrité de chaque archive
        for (archive in archives) {
            val invoice = invoices[archive.invoiceNumber]
            if (invoice == null) {
                corruptedDocuments.add(archive.invoiceNumber)
                continue
            }

            val expectedHash = invoice.fingerprintSha256()
            if (expectedHash != archive.payloadHash || archive.status == SealStatus.CORRUPTED) {
                corruptedDocuments.add(archive.invoiceNumber)
            }
        }

        // 2. Contrôle du chaînage de la Piste d'Audit Fiable
        var isChainValid = true
        var brokenIndex: Int? = null
        var lastChecksum: String? = null

        for ((index, entry) in auditTrail.withIndex()) {
            if (entry.previousChecksum != lastChecksum) {
                isChainValid = false
                brokenIndex = index
                break
            }

            // Recalcul du hash du maillon
            val source = listOf(
                entry.id,
                entry.invoiceNumber.orEmpty(),
                entry.action,
                entry.timestamp,
                entry.previousChecksum.orEmpty(),
            ).joinToString("|")

            val expectedChecksum = sha256Hex(source)
            if (expectedChecksum != entry.checksum) {
                isChainValid = false
                brokenIndex = index
                break
            }

            lastChecksum = entry.checksum
        }

        VaultIntegrityReport(
            totalDocuments = archives.size,
            verifiedDocuments = archives.size - corruptedDocuments.size,
            corruptedDocuments = corruptedDocuments,
            isAuditChainValid = isChainValid,
            brokenChainIndex = brokenIndex,
            verifiedAt = verificationTimestampIso,
        )
    }
}
