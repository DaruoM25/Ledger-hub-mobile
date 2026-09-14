package com.ledgerhub.domain.vault

import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceRepository

/**
 * Cas d'utilisation de scellement numérique d'une facture.
 * Calcule l'empreinte SHA-256 canonique, crée l'archive et enregistre l'événement dans la piste d'audit chaînée.
 */
class GenerateInvoiceSealUseCase(
    private val vaultRepository: VaultRepository,
    private val invoiceRepository: InvoiceRepository,
) {
    suspend operator fun invoke(
        invoiceNumber: String,
        timestampIso: String = "2026-09-14T10:00:00Z",
    ): Result<DigitalArchive> = runCatching {
        val invoices = invoiceRepository.fetchInvoices().getOrThrow()
        val invoice = invoices.firstOrNull { it.number == invoiceNumber }
            ?: error("Facture introuvable pour scellement: $invoiceNumber")

        val payloadHash = invoice.fingerprintSha256()
        val archiveSize = (payloadHash.length * 16L) + 1024L // Taille simulée du payload Factur-X / PDF

        val archive = DigitalArchive(
            id = "ARC-$invoiceNumber",
            invoiceNumber = invoiceNumber,
            documentType = VaultDocumentType.INVOICE_FACTURX,
            payloadHash = payloadHash,
            archiveSize = archiveSize,
            sealedAt = timestampIso,
            sealedBy = "DGFiP-SEAL-2026",
            status = SealStatus.SEALED,
        )

        vaultRepository.saveArchive(archive).getOrThrow()

        vaultRepository.appendAuditEntry(
            id = "AUD-${archive.id}",
            invoiceNumber = invoiceNumber,
            action = "DOCUMENT_SEALED",
            details = "Scellement cryptographique SHA-256: ${payloadHash.take(16)}...",
            timestamp = timestampIso,
        ).getOrThrow()

        archive
    }
}
