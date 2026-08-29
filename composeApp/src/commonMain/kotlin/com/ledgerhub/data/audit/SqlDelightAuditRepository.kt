package com.ledgerhub.data.audit

import com.ledgerhub.db.AuditLog as AuditLogRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.audit.AuditRepository
import com.ledgerhub.domain.invoice.InvoiceStatus

/**
 * Lecture de la Piste d'Audit Fiable (voir `AuditLog.sq`).
 *
 * En lecture seule : l'écriture appartient à
 * [SqlDelightInvoiceRepository][com.ledgerhub.data.invoice.SqlDelightInvoiceRepository], qui seul
 * peut garantir qu'une trace accompagne bien un changement de statut dans la même transaction.
 */
class SqlDelightAuditRepository(
    private val database: LedgerHubDatabase,
) : AuditRepository {

    override suspend fun entriesFor(invoiceNumber: String): Result<List<AuditEntry>> = runCatching {
        database.auditLogQueries.selectByInvoiceNumber(invoiceNumber).executeAsList().map { it.toDomain() }
    }

    private fun AuditLogRow.toDomain(): AuditEntry = AuditEntry(
        id = id,
        invoiceNumber = invoiceNumber,
        // Un statut devenu inconnu (référentiel qui évoluerait de nouveau) ne doit pas rendre
        // tout l'historique illisible : la ligne est conservée, le champ retombe à null.
        fromStatus = fromStatus?.let { statusOrNull(it) },
        toStatus = statusOrNull(toStatus) ?: InvoiceStatus.DRAFT,
        reason = reason,
        createdAt = createdAt,
    )

    private fun statusOrNull(raw: String): InvoiceStatus? =
        InvoiceStatus.entries.firstOrNull { it.name == raw }
}
