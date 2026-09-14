package com.ledgerhub.domain.inbox

import com.ledgerhub.domain.audit.sha256Hex
import com.ledgerhub.domain.invoice.Money

/**
 * Résultat du traitement et de l'analyse anti-doublon d'une facture reçue.
 */
sealed interface ProcessReceivedInvoiceResult {
    data class Success(val invoice: ReceivedInvoice) : ProcessReceivedInvoiceResult
    data class DuplicateDetected(
        val invoice: ReceivedInvoice,
        val existingInvoice: ReceivedInvoice,
        val reason: String,
    ) : ProcessReceivedInvoiceResult
    data class Failure(val error: Throwable) : ProcessReceivedInvoiceResult
}

/**
 * Use-case de réception, contrôle anti-doublon et enregistrement d'une facture fournisseur.
 * Conforme aux exigences de la réforme 2026 : blocage immédiat des doublons sur empreinte SHA-256
 * ou sur le triptyque (SIREN fournisseur, n° facture, montant TTC en centimes).
 */
class ProcessReceivedInvoiceUseCase(
    private val inboxRepository: InboxRepository,
) {
    suspend operator fun invoke(
        id: String,
        supplierName: String,
        supplierSiren: String,
        supplierSiret: String,
        invoiceNumber: String,
        issueDate: String,
        dueDate: String,
        totalHt: Money,
        totalVat: Money,
        totalTtc: Money,
        rawPayload: String = "",
        receivedAt: String,
    ): ProcessReceivedInvoiceResult {
        // Calcul du hash canonique de la facture reçue
        val canonicalSource = "$supplierSiren|$invoiceNumber|$issueDate|${totalTtc.cents}|$rawPayload"
        val fileHash = sha256Hex(canonicalSource)

        // 1. Vérification par empreinte de fichier exacte
        val hashMatch = inboxRepository.findByFileHash(fileHash).getOrNull()
        if (hashMatch != null && hashMatch.id != id) {
            val duplicateReason = "Doublon exact détecté (Empreinte SHA-256 identique à la facture ${hashMatch.invoiceNumber})"
            val invoiceWithAlert = ReceivedInvoice(
                id = id,
                supplierName = supplierName,
                supplierSiren = supplierSiren,
                supplierSiret = supplierSiret,
                invoiceNumber = invoiceNumber,
                issueDate = issueDate,
                dueDate = dueDate,
                totalHt = totalHt,
                totalVat = totalVat,
                totalTtc = totalTtc,
                rawPayload = rawPayload,
                fileHash = fileHash,
                status = ReceivedInvoiceStatus.DUPLICATE_ALERT,
                duplicateReason = duplicateReason,
                receivedAt = receivedAt,
            )
            inboxRepository.saveReceivedInvoice(invoiceWithAlert)
            return ProcessReceivedInvoiceResult.DuplicateDetected(
                invoice = invoiceWithAlert,
                existingInvoice = hashMatch,
                reason = duplicateReason,
            )
        }

        // 2. Vérification par triptyque métier (SIREN + Numéro + Montant TTC)
        val triptychMatch = inboxRepository.findDuplicateTriptych(
            supplierSiren = supplierSiren,
            invoiceNumber = invoiceNumber,
            totalTtcCents = totalTtc.cents,
            excludeId = id,
        ).getOrNull()

        if (triptychMatch != null) {
            val duplicateReason = "Doublon métier détecté : Fournisseur ($supplierSiren), Facture ($invoiceNumber), Total TTC (${totalTtc.cents / 100.0} €)"
            val invoiceWithAlert = ReceivedInvoice(
                id = id,
                supplierName = supplierName,
                supplierSiren = supplierSiren,
                supplierSiret = supplierSiret,
                invoiceNumber = invoiceNumber,
                issueDate = issueDate,
                dueDate = dueDate,
                totalHt = totalHt,
                totalVat = totalVat,
                totalTtc = totalTtc,
                rawPayload = rawPayload,
                fileHash = fileHash,
                status = ReceivedInvoiceStatus.DUPLICATE_ALERT,
                duplicateReason = duplicateReason,
                receivedAt = receivedAt,
            )
            inboxRepository.saveReceivedInvoice(invoiceWithAlert)
            return ProcessReceivedInvoiceResult.DuplicateDetected(
                invoice = invoiceWithAlert,
                existingInvoice = triptychMatch,
                reason = duplicateReason,
            )
        }

        // 3. Aucune collision : enregistrement avec statut RECEIVED
        val newInvoice = ReceivedInvoice(
            id = id,
            supplierName = supplierName,
            supplierSiren = supplierSiren,
            supplierSiret = supplierSiret,
            invoiceNumber = invoiceNumber,
            issueDate = issueDate,
            dueDate = dueDate,
            totalHt = totalHt,
            totalVat = totalVat,
            totalTtc = totalTtc,
            rawPayload = rawPayload,
            fileHash = fileHash,
            status = ReceivedInvoiceStatus.RECEIVED,
            duplicateReason = null,
            receivedAt = receivedAt,
        )

        val saveResult = inboxRepository.saveReceivedInvoice(newInvoice)
        return if (saveResult.isSuccess) {
            ProcessReceivedInvoiceResult.Success(newInvoice)
        } else {
            ProcessReceivedInvoiceResult.Failure(saveResult.exceptionOrNull() ?: Exception("Erreur d'enregistrement"))
        }
    }

    suspend fun approveInvoice(id: String): Result<Unit> {
        val invoice = inboxRepository.getReceivedInvoiceById(id).getOrNull()
            ?: return Result.failure(IllegalArgumentException("Facture $id introuvable"))

        if (invoice.status == ReceivedInvoiceStatus.DUPLICATE_ALERT) {
            return Result.failure(IllegalStateException("Impossible d'approuver une facture marquée en alerte doublon"))
        }

        return inboxRepository.updateStatus(id, ReceivedInvoiceStatus.APPROVED, null)
    }

    suspend fun rejectInvoice(id: String, reason: String): Result<Unit> {
        return inboxRepository.updateStatus(id, ReceivedInvoiceStatus.REJECTED, reason)
    }
}
