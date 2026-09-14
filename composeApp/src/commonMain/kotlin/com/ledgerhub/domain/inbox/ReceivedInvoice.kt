package com.ledgerhub.domain.inbox

import com.ledgerhub.domain.invoice.Money

/**
 * Statut du cycle de vie d'une facture reçue (Achats / Fournisseurs).
 */
enum class ReceivedInvoiceStatus {
    RECEIVED,
    APPROVED,
    REJECTED,
    DUPLICATE_ALERT,
}

/**
 * Facture fournisseur reçue dans l'Inbox avec métadonnées d'intégrité et de déduplication.
 */
data class ReceivedInvoice(
    val id: String,
    val supplierName: String,
    val supplierSiren: String,
    val supplierSiret: String,
    val invoiceNumber: String,
    val issueDate: String,
    val dueDate: String,
    val totalHt: Money,
    val totalVat: Money,
    val totalTtc: Money,
    val rawPayload: String = "",
    val fileHash: String,
    val status: ReceivedInvoiceStatus = ReceivedInvoiceStatus.RECEIVED,
    val duplicateReason: String? = null,
    val receivedAt: String,
)
