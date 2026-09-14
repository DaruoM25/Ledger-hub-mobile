package com.ledgerhub.data.inbox

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.inbox.InboxRepository
import com.ledgerhub.domain.inbox.ReceivedInvoice
import com.ledgerhub.domain.inbox.ReceivedInvoiceStatus
import com.ledgerhub.domain.invoice.Money
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implémentation SQLDelight du repository Inbox des factures reçues.
 */
class SqlDelightInboxRepository(
    private val database: LedgerHubDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InboxRepository {

    override suspend fun saveReceivedInvoice(invoice: ReceivedInvoice): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            database.receivedInvoiceQueries.insertOrReplace(
                id = invoice.id,
                supplierName = invoice.supplierName,
                supplierSiren = invoice.supplierSiren,
                supplierSiret = invoice.supplierSiret,
                invoiceNumber = invoice.invoiceNumber,
                issueDate = invoice.issueDate,
                dueDate = invoice.dueDate,
                totalHtCents = invoice.totalHt.cents,
                totalVatCents = invoice.totalVat.cents,
                totalTtcCents = invoice.totalTtc.cents,
                rawPayload = invoice.rawPayload,
                fileHash = invoice.fileHash,
                status = invoice.status.name,
                duplicateReason = invoice.duplicateReason,
                receivedAt = invoice.receivedAt,
            )
            Unit
        }
    }

    override suspend fun fetchReceivedInvoices(): Result<List<ReceivedInvoice>> = withContext(ioDispatcher) {
        runCatching {
            database.receivedInvoiceQueries.selectAll().executeAsList().map { row ->
                ReceivedInvoice(
                    id = row.id,
                    supplierName = row.supplierName,
                    supplierSiren = row.supplierSiren,
                    supplierSiret = row.supplierSiret,
                    invoiceNumber = row.invoiceNumber,
                    issueDate = row.issueDate,
                    dueDate = row.dueDate,
                    totalHt = Money(row.totalHtCents),
                    totalVat = Money(row.totalVatCents),
                    totalTtc = Money(row.totalTtcCents),
                    rawPayload = row.rawPayload,
                    fileHash = row.fileHash,
                    status = ReceivedInvoiceStatus.valueOf(row.status),
                    duplicateReason = row.duplicateReason,
                    receivedAt = row.receivedAt,
                )
            }
        }
    }

    override suspend fun getReceivedInvoiceById(id: String): Result<ReceivedInvoice?> = withContext(ioDispatcher) {
        runCatching {
            database.receivedInvoiceQueries.selectById(id).executeAsOneOrNull()?.let { row ->
                ReceivedInvoice(
                    id = row.id,
                    supplierName = row.supplierName,
                    supplierSiren = row.supplierSiren,
                    supplierSiret = row.supplierSiret,
                    invoiceNumber = row.invoiceNumber,
                    issueDate = row.issueDate,
                    dueDate = row.dueDate,
                    totalHt = Money(row.totalHtCents),
                    totalVat = Money(row.totalVatCents),
                    totalTtc = Money(row.totalTtcCents),
                    rawPayload = row.rawPayload,
                    fileHash = row.fileHash,
                    status = ReceivedInvoiceStatus.valueOf(row.status),
                    duplicateReason = row.duplicateReason,
                    receivedAt = row.receivedAt,
                )
            }
        }
    }

    override suspend fun findByFileHash(fileHash: String): Result<ReceivedInvoice?> = withContext(ioDispatcher) {
        runCatching {
            database.receivedInvoiceQueries.selectByFileHash(fileHash).executeAsOneOrNull()?.let { row ->
                ReceivedInvoice(
                    id = row.id,
                    supplierName = row.supplierName,
                    supplierSiren = row.supplierSiren,
                    supplierSiret = row.supplierSiret,
                    invoiceNumber = row.invoiceNumber,
                    issueDate = row.issueDate,
                    dueDate = row.dueDate,
                    totalHt = Money(row.totalHtCents),
                    totalVat = Money(row.totalVatCents),
                    totalTtc = Money(row.totalTtcCents),
                    rawPayload = row.rawPayload,
                    fileHash = row.fileHash,
                    status = ReceivedInvoiceStatus.valueOf(row.status),
                    duplicateReason = row.duplicateReason,
                    receivedAt = row.receivedAt,
                )
            }
        }
    }

    override suspend fun findDuplicateTriptych(
        supplierSiren: String,
        invoiceNumber: String,
        totalTtcCents: Long,
        excludeId: String,
    ): Result<ReceivedInvoice?> = withContext(ioDispatcher) {
        runCatching {
            database.receivedInvoiceQueries.findDuplicateTriptych(
                supplierSiren = supplierSiren,
                invoiceNumber = invoiceNumber,
                totalTtcCents = totalTtcCents,
                id = excludeId,
            ).executeAsOneOrNull()?.let { row ->
                ReceivedInvoice(
                    id = row.id,
                    supplierName = row.supplierName,
                    supplierSiren = row.supplierSiren,
                    supplierSiret = row.supplierSiret,
                    invoiceNumber = row.invoiceNumber,
                    issueDate = row.issueDate,
                    dueDate = row.dueDate,
                    totalHt = Money(row.totalHtCents),
                    totalVat = Money(row.totalVatCents),
                    totalTtc = Money(row.totalTtcCents),
                    rawPayload = row.rawPayload,
                    fileHash = row.fileHash,
                    status = ReceivedInvoiceStatus.valueOf(row.status),
                    duplicateReason = row.duplicateReason,
                    receivedAt = row.receivedAt,
                )
            }
        }
    }

    override suspend fun updateStatus(
        id: String,
        status: ReceivedInvoiceStatus,
        duplicateReason: String?,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            database.receivedInvoiceQueries.updateStatus(
                status = status.name,
                duplicateReason = duplicateReason,
                id = id,
            )
            Unit
        }
    }
}
